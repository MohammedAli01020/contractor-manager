package com.contractor.app.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.driver.domain.DriverLedgerEntry;
import com.contractor.app.driver.infra.DriverLedgerEntryRepository;
import com.contractor.app.workforce.domain.WorkforceLedgerEntry;
import com.contractor.app.workforce.infra.WorkforceLedgerEntryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import com.contractor.app.common.idempotency.FinancialIdempotencyService;

class DriverLedgerIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private DefaultChartOfAccountsService defaultAccounts;
    @Autowired private ChartAccountRepository accounts;
    @Autowired private JournalEntryRepository journals;
    @Autowired private JournalPostingService postingService;
    @Autowired private DriverLedgerEntryRepository ledger;
    @Autowired private WorkforceLedgerEntryRepository workforceLedger;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FinancialIdempotencyService financialIdempotency;

    private Long tenantId;
    private Long actorId;
    private String token;
    private Long cashId;
    private Long bankId;
    private Long expenseId;
    private Long payableId;
    private Long equityId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode registered = registerTenant("Driver ledger " + uniqueName("tenant"), uniqueName("owner"));
        tenantId = registered.path("user").path("tenantId").asLong();
        actorId = registered.path("user").path("id").asLong();
        token = registered.path("accessToken").asText();
        defaultAccounts.initializeForTenant(tenantId);
        cashId = account("1000").getId();
        bankId = account("1010").getId();
        expenseId = account("5100").getId();
        payableId = account("2100").getId();
        equityId = account("3000").getId();
        openLiquidity(cashId, "cash-opening", new BigDecimal("2000.00"));
        openLiquidity(bankId, "bank-opening", new BigDecimal("2000.00"));
    }

    @Test
    void workforceLedgerMirrorTracksAdjustmentsPaymentsAndRecoverableAdvancesByCanonicalMember() throws Exception {
        Long driverId = createDriver("workforce-ledger-mirror");
        earn(driverId, "workforce-ledger-mirror-earning");

        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);
        java.util.List<WorkforceLedgerEntry> afterEarning =
                workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                        tenantId, memberId);
        assertThat(afterEarning).hasSize(1);
        assertThat(afterEarning.get(0).getTransactionType()).isEqualTo("ADJUSTMENT");
        assertThat(afterEarning.get(0).getLegacyDriverId()).isEqualTo(driverId);
        assertThat(afterEarning.get(0).getLegacySourceEntryId()).isGreaterThan(0L);
        assertThat(workforceLedger.sumBalance(tenantId, memberId))
                .isEqualByComparingTo("500.00");

        assertThat(postPayment(driverId, "workforce-ledger-mirror-payment", cashId).andReturn()
                .getResponse().getStatus()).isEqualTo(201);

        java.util.List<WorkforceLedgerEntry> afterPayment =
                workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                        tenantId, memberId);
        assertThat(afterPayment).hasSize(2);
        assertThat(afterPayment).extracting(WorkforceLedgerEntry::getTransactionType)
                .containsExactly("ADJUSTMENT", "PAYMENT");
        assertThat(workforceLedger.sumBalance(tenantId, memberId))
                .isEqualByComparingTo("400.00");

        Map<String, Object> advanceRequest = new HashMap<>();
        advanceRequest.put("clientRequestKey", "workforce-ledger-mirror-recoverable-advance");
        advanceRequest.put("advanceDate", LocalDate.of(2026, 10, 31));
        advanceRequest.put("amount", new BigDecimal("75.00"));
        advanceRequest.put("paymentAccountId", cashId);
        advanceRequest.put("description", "Recoverable advance");
        advanceRequest.put("advanceType", "RECOVERABLE");
        assertThat(postJson("/api/v1/drivers/" + driverId + "/ledger/advances",
                advanceRequest, token).andReturn().getResponse().getStatus()).isEqualTo(201);

        java.util.List<WorkforceLedgerEntry> afterRecoverableAdvance =
                workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                        tenantId, memberId);
        assertThat(afterRecoverableAdvance).hasSize(3);
        assertThat(afterRecoverableAdvance).extracting(WorkforceLedgerEntry::getTransactionType)
                .containsExactly("ADJUSTMENT", "PAYMENT", "RECOVERABLE_ADVANCE");
        assertThat(workforceLedger.sumBalance(tenantId, memberId))
                .isEqualByComparingTo("400.00");
        assertThat(workforceLedger.sumRecoverableAdvanceBalance(tenantId, memberId))
                .isEqualByComparingTo("75.00");

        DriverLedgerEntry recoverableAdvance = ledger.findByTenantIdAndClientRequestKey(
                tenantId, "workforce-ledger-mirror-recoverable-advance").orElseThrow(AssertionError::new);
        WorkforceLedgerEntry mirroredAdvance = workforceLedger.findByTenantIdAndLegacySourceEntryId(
                tenantId, recoverableAdvance.getId()).orElseThrow(AssertionError::new);
        assertThat(mirroredAdvance.getWorkforceMemberId()).isEqualTo(memberId);
        assertThat(mirroredAdvance.getJournalEntryId()).isEqualTo(recoverableAdvance.getJournalEntryId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_lines WHERE tenant_id = ? AND journal_entry_id = ? "
                        + "AND workforce_member_id = ?",
                Integer.class, tenantId, recoverableAdvance.getJournalEntryId(), memberId)).isEqualTo(1);

        JsonNode workforceStatement = body(getJson(
                "/api/v1/workforce/members/" + memberId + "/ledger", token)
                .andExpect(status().isOk()));
        assertThat(workforceStatement.size()).isEqualTo(3);
        assertThat(workforceStatement.get(0).path("workforceMemberId").asLong()).isEqualTo(memberId);
        assertThat(workforceStatement.get(2).path("transactionType").asText())
                .isEqualTo("RECOVERABLE_ADVANCE");
        assertThat(workforceStatement.get(2).path("balanceAfter").decimalValue())
                .isEqualByComparingTo("400.00");
        assertThat(workforceStatement.get(2).has("driverId")).isFalse();

        JsonNode workforceBalance = body(getJson(
                "/api/v1/workforce/members/" + memberId + "/ledger/balance", token)
                .andExpect(status().isOk()));
        assertThat(workforceBalance.path("workforceMemberId").asLong()).isEqualTo(memberId);
        assertThat(workforceBalance.path("payableBalance").decimalValue())
                .isEqualByComparingTo("400.00");
        assertThat(workforceBalance.path("recoverableAdvanceBalance").decimalValue())
                .isEqualByComparingTo("75.00");
    }

    @Test
    void workforcePaymentAndRecoverableAdvanceCommandsUseCanonicalMemberRouteAndMirrorOnce() throws Exception {
        Long driverId = createDriver("workforce-command-adapter");
        earn(driverId, "workforce-command-earning");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "workforce-command-payment");
        payment.put("paymentDate", LocalDate.of(2026, 11, 1));
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Workforce payment");
        String route = "/api/v1/workforce/members/" + memberId + "/ledger";
        assertThat(postJson(route + "/payments", payment, token).andReturn()
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(postJson(route + "/payments", payment, token).andReturn()
                .getResponse().getStatus()).isEqualTo(201);

        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "workforce-command-recoverable");
        advance.put("advanceDate", LocalDate.of(2026, 11, 1));
        advance.put("amount", new BigDecimal("75.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "Workforce recoverable advance");
        advance.put("advanceType", "RECOVERABLE");
        JsonNode createdAdvance = body(postJson(route + "/advances", advance, token)
                .andExpect(status().isCreated()));
        postJson(route + "/advances", advance, token).andExpect(status().isCreated());
        assertThat(createdAdvance.path("workforceMemberId").asLong()).isEqualTo(memberId);
        assertThat(createdAdvance.has("driverId")).isFalse();

        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, "workforce-command-payment"))
                .isPresent();
        assertThat(workforceLedger.findByTenantIdAndClientRequestKey(tenantId, "workforce-command-payment"))
                .isPresent();
        assertThat(workforceLedger.findByTenantIdAndClientRequestKey(tenantId, "workforce-command-recoverable"))
                .isPresent();
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId)).hasSize(3);
        assertThat(workforceLedger.sumBalance(tenantId, memberId)).isEqualByComparingTo("400.00");
        assertThat(workforceLedger.sumRecoverableAdvanceBalance(tenantId, memberId))
                .isEqualByComparingTo("75.00");
    }

    @Test
    void workforceLedgerStatementCannotReadAnotherTenantsMember() throws Exception {
        Long driverId = createDriver("tenant-isolation-ledger");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);
        JsonNode otherTenant = registerTenant(
                "Other ledger tenant " + uniqueName("tenant"), uniqueName("other-owner"));
        String otherTenantToken = otherTenant.path("accessToken").asText();

        getJson("/api/v1/workforce/members/" + memberId + "/ledger", otherTenantToken)
                .andExpect(status().isNotFound());
        getJson("/api/v1/workforce/members/" + memberId + "/ledger/balance", otherTenantToken)
                .andExpect(status().isNotFound());

        Map<String, Object> foreignPayment = new HashMap<>();
        foreignPayment.put("clientRequestKey", "foreign-workforce-payment");
        foreignPayment.put("paymentDate", LocalDate.of(2026, 11, 1));
        foreignPayment.put("amount", new BigDecimal("1.00"));
        foreignPayment.put("paymentAccountId", cashId);
        foreignPayment.put("description", "Must not cross tenant boundary");
        postJson("/api/v1/workforce/members/" + memberId + "/ledger/payments",
                foreignPayment, otherTenantToken).andExpect(status().isNotFound());
    }

    @Test
    void workforceLedgerSchemaAcceptsNativeMovementWithoutLegacyDriverProvenance() throws Exception {
        Long driverId = createDriver("native-workforce-ledger");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);
        String key = "native-workforce-adjustment";
        JournalPostingRequest journalRequest = new JournalPostingRequest(
                "native-workforce-journal", "WORKFORCE_LEDGER_TEST", memberId,
                LocalDate.of(2026, 11, 1), "Native Workforce ledger schema test", null,
                Arrays.asList(
                        new JournalLineRequest(expenseId, "Workforce expense",
                                new BigDecimal("25.00"), BigDecimal.ZERO, null, memberId, null),
                        new JournalLineRequest(payableId, "Workforce payable",
                                BigDecimal.ZERO, new BigDecimal("25.00"), null, memberId, null)));
        Long journalId = postingService.post(tenantId, actorId, journalRequest).getId();

        jdbcTemplate.update(
                "INSERT INTO workforce_ledger_entries (tenant_id, workforce_member_id, transaction_type, "
                        + "transaction_date, amount, balance_effect, description, client_request_key, shift_id, "
                        + "journal_entry_id, created_by_user_id, created_at, legacy_source_entry_id, legacy_driver_id) "
                        + "VALUES (?, ?, 'ADJUSTMENT', ?, ?, ?, ?, ?, NULL, ?, ?, UTC_TIMESTAMP(6), NULL, NULL)",
                tenantId, memberId, LocalDate.of(2026, 11, 1), new BigDecimal("25.00"),
                new BigDecimal("25.00"), "Native Workforce movement", key, journalId, actorId);

        WorkforceLedgerEntry nativeEntry = workforceLedger.findByTenantIdAndClientRequestKey(tenantId, key)
                .orElseThrow(AssertionError::new);
        assertThat(nativeEntry.getWorkforceMemberId()).isEqualTo(memberId);
        assertThat(nativeEntry.getLegacySourceEntryId()).isNull();
        assertThat(nativeEntry.getLegacyDriverId()).isNull();
        assertThat(workforceLedger.sumBalance(tenantId, memberId)).isEqualByComparingTo("25.00");
        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, key)).isEmpty();
    }

    @Test
    void idempotencyReservationRequiresTheOuterFinancialTransaction() {
        assertThatThrownBy(() -> financialIdempotency.reserve(
                tenantId, "DRIVER_LEDGER", "outside-transaction", "a".repeat(64)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void concurrentPaymentKeyAcrossDifferentDriversCreatesOneLedgerEntryAndOneConflict() throws Exception {
        Long firstDriver = createDriver("driver-one");
        Long secondDriver = createDriver("driver-two");
        earn(firstDriver, "earn-first");
        earn(secondDriver, "earn-second");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> pay(firstDriver, "driver-payment-race",
                    cashId, ready, start));
            Future<Integer> second = executor.submit(() -> pay(secondDriver, "driver-payment-race",
                    cashId, ready, start));
            ready.await();
            start.countDown();
            assertThat(Arrays.asList(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        DriverLedgerEntry entry = ledger.findByTenantIdAndClientRequestKey(tenantId, "driver-payment-race")
                .orElseThrow(AssertionError::new);
        assertThat(journals.findByTenantIdAndPostingKey(tenantId, "driver-payment:driver-payment-race"))
                .isPresent();
        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, entry.getDriverId()))
                .extracting(DriverLedgerEntry::getId).contains(entry.getId());
    }

    @Test
    void concurrentRecoverableAdvanceKeyAcrossDifferentDriversCreatesOneEntryAndOneConflict() throws Exception {
        Long firstDriver = createDriver("advance-driver-one");
        Long secondDriver = createDriver("advance-driver-two");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> advance(firstDriver, "driver-advance-race",
                    ready, start));
            Future<Integer> second = executor.submit(() -> advance(secondDriver, "driver-advance-race",
                    ready, start));
            ready.await();
            start.countDown();
            assertThat(Arrays.asList(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        DriverLedgerEntry entry = ledger.findByTenantIdAndClientRequestKey(tenantId, "driver-advance-race")
                .orElseThrow(AssertionError::new);
        assertThat(entry.getTransactionType()).isEqualTo("RECOVERABLE_ADVANCE");
        assertThat(journals.findByTenantIdAndPostingKey(tenantId, "driver-advance:driver-advance-race"))
                .isPresent();
    }

    @Test
    void identicalDriverPaymentRetryReturnsTheOriginalLedgerEntry() throws Exception {
        Long driverId = createDriver("retry-driver");
        earn(driverId, "retry-driver-earned");

        assertThat(postPayment(driverId, "driver-payment-retry", cashId).andReturn()
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(postPayment(driverId, "driver-payment-retry", cashId).andReturn()
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, "driver-payment-retry")).isPresent();
        assertThat(journals.findByTenantIdAndPostingKey(tenantId, "driver-payment:driver-payment-retry"))
                .isPresent();
    }

    @Test
    void reusingDriverPaymentKeyWithDifferentCashOrBankAccountConflicts() throws Exception {
        Long driverId = createDriver("account-mismatch-driver");
        earn(driverId, "account-mismatch-earned");

        assertThat(postPayment(driverId, "driver-payment-account", cashId).andReturn()
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(postPayment(driverId, "driver-payment-account", bankId).andReturn()
                .getResponse().getStatus()).isEqualTo(409);
        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, "driver-payment-account")).isPresent();
        assertThat(journals.findByTenantIdAndPostingKey(tenantId, "driver-payment:driver-payment-account"))
                .isPresent();
    }

    @Test
    void legacyDriverPaymentRetryChecksOriginalJournalAccountWithoutReservationRow() throws Exception {
        Long driverId = createDriver("legacy-account-driver");
        earn(driverId, "legacy-account-earned");
        String key = "legacy-driver-payment-account";
        assertThat(postPayment(driverId, key, cashId).andReturn().getResponse().getStatus())
                .isEqualTo(201);

        // Simulate a driver payment created before V25 introduced the reservation table.
        jdbcTemplate.update("DELETE FROM financial_idempotency_keys "
                        + "WHERE tenant_id = ? AND operation_scope = ? AND request_key = ?",
                tenantId, "DRIVER_LEDGER", key);

        assertThat(postPayment(driverId, key, bankId).andReturn().getResponse().getStatus())
                .isEqualTo(409);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_idempotency_keys "
                        + "WHERE tenant_id = ? AND operation_scope = ? AND request_key = ?",
                Integer.class, tenantId, "DRIVER_LEDGER", key)).isZero();
        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, key)).isPresent();
    }

    @Test
    void driverPaymentAndAdvanceCannotReuseOneLedgerKeyAcrossOperationTypes() throws Exception {
        Long driverId = createDriver("cross-operation-driver");
        earn(driverId, "cross-operation-earned");
        String key = "driver-payment-then-advance";
        assertThat(postPayment(driverId, key, cashId).andReturn().getResponse().getStatus())
                .isEqualTo(201);

        Map<String, Object> request = new HashMap<>();
        request.put("clientRequestKey", key);
        request.put("advanceDate", LocalDate.of(2026, 10, 31));
        request.put("amount", new BigDecimal("50.00"));
        request.put("paymentAccountId", cashId);
        request.put("description", "Recoverable advance");
        request.put("advanceType", "RECOVERABLE");
        assertThat(postJson("/api/v1/drivers/" + driverId + "/ledger/advances", request, token)
                .andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, key))
                .hasValueSatisfying(entry -> assertThat(entry.getTransactionType()).isEqualTo("PAYMENT"));
    }

    private Long createDriver(String suffix) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", uniqueName(suffix));
        request.put("workerType", "DRIVER");
        request.put("morningRate", new BigDecimal("200.00"));
        request.put("eveningRate", new BigDecimal("200.00"));
        request.put("nightRate", new BigDecimal("200.00"));
        JsonNode created = body(postJson("/api/v1/drivers", request, token).andExpect(status().isCreated()));
        return created.path("id").asLong();
    }

    private void earn(Long driverId, String key) {
        var journal = postingService.post(tenantId, actorId, new JournalPostingRequest(
                uniqueName(key), "DRIVER_EARNING", null, LocalDate.of(2026, 10, 31),
                "Test driver payable opening", null, Arrays.asList(
                new JournalLineRequest(expenseId, "Driver wages", new BigDecimal("500.00"),
                        BigDecimal.ZERO, null, null),
                new JournalLineRequest(payableId, "Driver payable", BigDecimal.ZERO,
                        new BigDecimal("500.00"), driverId, null))));
        ledger.saveAndFlush(new DriverLedgerEntry(tenantId, driverId, "ADJUSTMENT",
                LocalDate.of(2026, 10, 31), new BigDecimal("500.00"), new BigDecimal("500.00"),
                "Test balance adjustment", uniqueName(key), null, journal.getId(), actorId));
    }

    private void openLiquidity(Long accountId, String key, BigDecimal amount) {
        postingService.post(tenantId, actorId, new JournalPostingRequest(uniqueName(key),
                "TEST_CASH_OPENING", null, LocalDate.of(2026, 10, 31), "Opening balance", null,
                Arrays.asList(
                new JournalLineRequest(accountId, "Opening cash/bank", amount, BigDecimal.ZERO, null, null),
                new JournalLineRequest(equityId, "Opening capital", BigDecimal.ZERO, amount, null, null))));
    }

    private int pay(Long driverId, String key, Long accountId, CountDownLatch ready,
                    CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return postPayment(driverId, key, accountId).andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions postPayment(Long driverId, String key,
                                                                            Long accountId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("clientRequestKey", key);
        request.put("paymentDate", LocalDate.of(2026, 10, 31));
        request.put("amount", new BigDecimal("100.00"));
        request.put("paymentAccountId", accountId);
        request.put("description", "Driver payment");
        return postJson("/api/v1/drivers/" + driverId + "/ledger/payments", request, token);
    }

    private int advance(Long driverId, String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        Map<String, Object> request = new HashMap<>();
        request.put("clientRequestKey", key);
        request.put("advanceDate", LocalDate.of(2026, 10, 31));
        request.put("amount", new BigDecimal("75.00"));
        request.put("paymentAccountId", cashId);
        request.put("description", "Recoverable advance");
        request.put("advanceType", "RECOVERABLE");
        return postJson("/api/v1/drivers/" + driverId + "/ledger/advances", request, token)
                .andReturn().getResponse().getStatus();
    }

    private ChartAccount account(String code) {
        return accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, code)
                .orElseThrow(() -> new AssertionError("Missing default account " + code));
    }
}
