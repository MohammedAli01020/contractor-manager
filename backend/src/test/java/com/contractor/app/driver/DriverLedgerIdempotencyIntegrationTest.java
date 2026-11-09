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
    void workforceAndLegacyDriverPaymentRoutesShareOneIdempotencyReservationAndMirror() throws Exception {
        Long driverId = createDriver("cross-route-payment");
        earn(driverId, "cross-route-payment-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "cross-route-payment-key");
        payment.put("paymentDate", LocalDate.of(2026, 11, 2));
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Cross-route payment retry");

        String workforceRoute = "/api/v1/workforce/members/" + memberId + "/ledger/payments";
        String driverRoute = "/api/v1/drivers/" + driverId + "/ledger/payments";
        postJson(workforceRoute, payment, token).andExpect(status().isCreated());
        postJson(driverRoute, payment, token).andExpect(status().isCreated());

        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, "cross-route-payment-key"))
                .hasValueSatisfying(entry -> assertThat(entry.getDriverId()).isEqualTo(driverId));
        assertThat(workforceLedger.findByTenantIdAndClientRequestKey(tenantId, "cross-route-payment-key"))
                .hasValueSatisfying(entry -> {
                    assertThat(entry.getWorkforceMemberId()).isEqualTo(memberId);
                    assertThat(entry.getLegacyDriverId()).isEqualTo(driverId);
                    assertThat(entry.getLegacySourceEntryId()).isNotNull();
                });
        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType())).hasSize(1);
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId)).filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType())).hasSize(1);
        assertThat(journals.findByTenantIdAndPostingKey(tenantId, "driver-payment:cross-route-payment-key"))
                .isPresent();
    }

    @Test
    void legacyDriverThenWorkforcePaymentRetryReturnsTheSameCanonicalMovement() throws Exception {
        Long driverId = createDriver("reverse-cross-route-payment");
        earn(driverId, "reverse-cross-route-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "reverse-cross-route-payment-key");
        payment.put("paymentDate", LocalDate.of(2026, 11, 2));
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Reverse cross-route payment retry");

        String workforceRoute = "/api/v1/workforce/members/" + memberId + "/ledger/payments";
        String driverRoute = "/api/v1/drivers/" + driverId + "/ledger/payments";
        postJson(driverRoute, payment, token).andExpect(status().isCreated());
        postJson(workforceRoute, payment, token).andExpect(status().isCreated());

        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, "reverse-cross-route-payment-key"))
                .hasValueSatisfying(entry -> assertThat(entry.getDriverId()).isEqualTo(driverId));
        assertThat(workforceLedger.findByTenantIdAndClientRequestKey(
                tenantId, "reverse-cross-route-payment-key"))
                .hasValueSatisfying(entry -> {
                    assertThat(entry.getWorkforceMemberId()).isEqualTo(memberId);
                    assertThat(entry.getLegacyDriverId()).isEqualTo(driverId);
                    assertThat(entry.getLegacySourceEntryId()).isNotNull();
                });
        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType())).hasSize(1);
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId)).filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType())).hasSize(1);
        assertThat(journals.findByTenantIdAndPostingKey(
                tenantId, "driver-payment:reverse-cross-route-payment-key")).isPresent();
    }

    @Test
    void concurrentWorkforceAndLegacyDriverPaymentRoutesWithSameKeyCreateOneMovement() throws Exception {
        Long driverId = createDriver("mixed-route-payment-race");
        earn(driverId, "mixed-route-payment-race-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "mixed-route-payment-race-key");
        payment.put("paymentDate", LocalDate.of(2026, 11, 2));
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Concurrent mixed-route payment");

        String workforceRoute = "/api/v1/workforce/members/" + memberId + "/ledger/payments";
        String driverRoute = "/api/v1/drivers/" + driverId + "/ledger/payments";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> workforce = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(workforceRoute, payment, token).andReturn().getResponse().getStatus();
            });
            Future<Integer> legacy = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(driverRoute, payment, token).andReturn().getResponse().getStatus();
            });
            ready.await();
            start.countDown();
            assertThat(Arrays.asList(workforce.get(), legacy.get()))
                    .containsExactlyInAnyOrder(201, 201);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType())).hasSize(1);
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId)).filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType())).hasSize(1);
        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, "mixed-route-payment-race-key"))
                .isPresent();
        assertThat(workforceLedger.findByTenantIdAndClientRequestKey(
                tenantId, "mixed-route-payment-race-key"))
                .hasValueSatisfying(entry -> {
                    assertThat(entry.getWorkforceMemberId()).isEqualTo(memberId);
                    assertThat(entry.getLegacyDriverId()).isEqualTo(driverId);
                    assertThat(entry.getLegacySourceEntryId()).isNotNull();
                });
        assertThat(journals.findByTenantIdAndPostingKey(
                tenantId, "driver-payment:mixed-route-payment-race-key")).isPresent();
    }

    @Test
    void concurrentMixedRoutePaymentsWithDifferentKeysCannotOverdrawOneWorkforceMember() throws Exception {
        Long driverId = createDriver("mixed-route-distinct-key-payment-race");
        earn(driverId, "mixed-route-distinct-key-payment-race-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> workforcePayment = new HashMap<>();
        workforcePayment.put("clientRequestKey", "mixed-route-workforce-300-key");
        workforcePayment.put("paymentDate", LocalDate.of(2026, 11, 3));
        workforcePayment.put("amount", new BigDecimal("300.00"));
        workforcePayment.put("paymentAccountId", cashId);
        workforcePayment.put("description", "Concurrent Workforce payment");
        Map<String, Object> legacyPayment = new HashMap<>(workforcePayment);
        legacyPayment.put("clientRequestKey", "mixed-route-driver-300-key");
        legacyPayment.put("description", "Concurrent legacy Driver payment");

        String workforceRoute = "/api/v1/workforce/members/" + memberId + "/ledger/payments";
        String driverRoute = "/api/v1/drivers/" + driverId + "/ledger/payments";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> workforce = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(workforceRoute, workforcePayment, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> legacy = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(driverRoute, legacyPayment, token)
                        .andReturn().getResponse().getStatus();
            });
            ready.await();
            start.countDown();
            assertThat(Arrays.asList(workforce.get(), legacy.get()))
                    .containsExactlyInAnyOrder(201, 400);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getAmount()).isEqualByComparingTo("300.00"));
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId))
                .filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAmount()).isEqualByComparingTo("300.00");
                    assertThat(entry.getLegacyDriverId()).isEqualTo(driverId);
                    assertThat(entry.getLegacySourceEntryId()).isNotNull();
                });
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("200.00");
        assertThat(workforceLedger.sumBalance(tenantId, memberId)).isEqualByComparingTo("200.00");
        assertThat(journals.findByTenantIdAndPostingKey(tenantId,
                "driver-payment:mixed-route-workforce-300-key").isPresent()
                ^ journals.findByTenantIdAndPostingKey(tenantId,
                "driver-payment:mixed-route-driver-300-key").isPresent()).isTrue();
    }

    @Test
    void concurrentPaymentAndAgainstEarnedAdvanceCannotSpendTheSameWorkforceBalanceTwice() throws Exception {
        Long driverId = createDriver("payment-earned-advance-race");
        earn(driverId, "payment-earned-advance-race-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "payment-earned-advance-race-payment");
        payment.put("paymentDate", LocalDate.of(2026, 11, 6));
        payment.put("amount", new BigDecimal("300.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Payment racing earned-balance advance");

        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "payment-earned-advance-race-advance");
        advance.put("advanceDate", LocalDate.of(2026, 11, 6));
        advance.put("amount", new BigDecimal("300.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "Against-earned advance racing payment");
        advance.put("advanceType", "AGAINST_EARNED");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> paymentResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start payment.");
                }
                return postJson("/api/v1/drivers/" + driverId + "/ledger/payments", payment, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> advanceResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start advance.");
                }
                return postJson("/api/v1/drivers/" + driverId + "/ledger/advances", advance, token)
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(Arrays.asList(
                    paymentResult.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    advanceResult.get(30, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 400);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("200.00");
        assertThat(workforceLedger.sumBalance(tenantId, memberId)).isEqualByComparingTo("200.00");
        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType())
                        || "ADVANCE".equals(entry.getTransactionType()))
                .hasSize(1);
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId))
                .filteredOn(entry -> "PAYMENT".equals(entry.getTransactionType())
                        || "ADVANCE".equals(entry.getTransactionType()))
                .hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id = ? AND posting_key IN (?, ?)",
                Integer.class, tenantId,
                "driver-payment:payment-earned-advance-race-payment",
                "driver-advance:payment-earned-advance-race-advance")).isEqualTo(1);
    }

    @Test
    void concurrentAgainstEarnedAndRecoverableAdvancesShareTheCanonicalFinancialMutex() throws Exception {
        Long driverId = createDriver("earned-vs-recoverable-advance-race");
        earn(driverId, "earned-vs-recoverable-advance-race-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> earnedAdvance = new HashMap<>();
        earnedAdvance.put("clientRequestKey", "earned-vs-recoverable-earned-key");
        earnedAdvance.put("advanceDate", LocalDate.of(2026, 11, 7));
        earnedAdvance.put("amount", new BigDecimal("300.00"));
        earnedAdvance.put("paymentAccountId", cashId);
        earnedAdvance.put("description", "Against-earned advance racing recoverable advance");
        earnedAdvance.put("advanceType", "AGAINST_EARNED");

        Map<String, Object> recoverableAdvance = new HashMap<>(earnedAdvance);
        recoverableAdvance.put("clientRequestKey", "earned-vs-recoverable-recoverable-key");
        recoverableAdvance.put("description", "Recoverable advance racing against-earned advance");
        recoverableAdvance.put("advanceType", "RECOVERABLE");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> earnedResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start earned advance.");
                }
                return postJson("/api/v1/drivers/" + driverId + "/ledger/advances",
                        earnedAdvance, token).andReturn().getResponse().getStatus();
            });
            Future<Integer> recoverableResult = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start recoverable advance.");
                }
                return postJson("/api/v1/drivers/" + driverId + "/ledger/advances",
                        recoverableAdvance, token).andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(Arrays.asList(
                    earnedResult.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    recoverableResult.get(30, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactly(201, 201);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("200.00");
        assertThat(workforceLedger.sumBalance(tenantId, memberId)).isEqualByComparingTo("200.00");
        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "ADVANCE".equals(entry.getTransactionType())
                        || "RECOVERABLE_ADVANCE".equals(entry.getTransactionType()))
                .hasSize(2);
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId))
                .filteredOn(entry -> "ADVANCE".equals(entry.getTransactionType())
                        || "RECOVERABLE_ADVANCE".equals(entry.getTransactionType()))
                .hasSize(2);
        assertThat(workforceLedger.sumRecoverableAdvanceBalance(tenantId, memberId))
                .isEqualByComparingTo("300.00");
        assertThat(journals.findByTenantIdAndPostingKey(
                tenantId, "driver-advance:earned-vs-recoverable-earned-key")).isPresent();
        assertThat(journals.findByTenantIdAndPostingKey(
                tenantId, "driver-advance:earned-vs-recoverable-recoverable-key")).isPresent();
    }

    @Test
    void recoverableAdvanceRetriesAcrossWorkforceAndDriverRoutesCreateOneMovement() throws Exception {
        Long firstDriver = createDriver("cross-route-advance-workforce-first");
        Long firstMember = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, firstDriver, tenantId);
        Map<String, Object> workforceFirst = new HashMap<>();
        workforceFirst.put("clientRequestKey", "cross-route-advance-workforce-first-key");
        workforceFirst.put("advanceDate", LocalDate.of(2026, 11, 2));
        workforceFirst.put("amount", new BigDecimal("50.00"));
        workforceFirst.put("paymentAccountId", cashId);
        workforceFirst.put("description", "Cross-route recoverable advance");
        workforceFirst.put("advanceType", "RECOVERABLE");

        postJson("/api/v1/workforce/members/" + firstMember + "/ledger/advances",
                workforceFirst, token).andExpect(status().isCreated());
        postJson("/api/v1/drivers/" + firstDriver + "/ledger/advances",
                workforceFirst, token).andExpect(status().isCreated());

        Long secondDriver = createDriver("cross-route-advance-driver-first");
        Long secondMember = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, secondDriver, tenantId);
        Map<String, Object> driverFirst = new HashMap<>(workforceFirst);
        driverFirst.put("clientRequestKey", "cross-route-advance-driver-first-key");

        postJson("/api/v1/drivers/" + secondDriver + "/ledger/advances",
                driverFirst, token).andExpect(status().isCreated());
        postJson("/api/v1/workforce/members/" + secondMember + "/ledger/advances",
                driverFirst, token).andExpect(status().isCreated());

        for (Long[] identity : Arrays.asList(
                new Long[] {firstDriver, firstMember},
                new Long[] {secondDriver, secondMember})) {
            Long driverId = identity[0];
            Long memberId = identity[1];
            String key = driverId.equals(firstDriver)
                    ? "cross-route-advance-workforce-first-key"
                    : "cross-route-advance-driver-first-key";
            assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, key))
                    .hasValueSatisfying(entry -> {
                        assertThat(entry.getDriverId()).isEqualTo(driverId);
                        assertThat(entry.getTransactionType()).isEqualTo("RECOVERABLE_ADVANCE");
                    });
            assertThat(workforceLedger.findByTenantIdAndClientRequestKey(tenantId, key))
                    .hasValueSatisfying(entry -> {
                        assertThat(entry.getWorkforceMemberId()).isEqualTo(memberId);
                        assertThat(entry.getLegacyDriverId()).isEqualTo(driverId);
                        assertThat(entry.getLegacySourceEntryId()).isNotNull();
                    });
            assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                    .filteredOn(entry -> "RECOVERABLE_ADVANCE".equals(entry.getTransactionType())).hasSize(1);
            assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                    tenantId, memberId))
                    .filteredOn(entry -> "RECOVERABLE_ADVANCE".equals(entry.getTransactionType())).hasSize(1);
            assertThat(journals.findByTenantIdAndPostingKey(tenantId, "driver-advance:" + key))
                    .isPresent();
            assertThat(workforceLedger.sumRecoverableAdvanceBalance(tenantId, memberId))
                    .isEqualByComparingTo("50.00");
        }
    }

    @Test
    void concurrentMixedRouteAgainstEarnedAdvancesWithDifferentKeysCannotOverdrawOneWorkforceMember() throws Exception {
        Long driverId = createDriver("mixed-route-earned-advance-race");
        earn(driverId, "mixed-route-earned-advance-race-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> workforceAdvance = new HashMap<>();
        workforceAdvance.put("clientRequestKey", "mixed-route-workforce-earned-advance-key");
        workforceAdvance.put("advanceDate", LocalDate.of(2026, 11, 4));
        workforceAdvance.put("amount", new BigDecimal("300.00"));
        workforceAdvance.put("paymentAccountId", cashId);
        workforceAdvance.put("description", "Concurrent Workforce earned advance");
        workforceAdvance.put("advanceType", "AGAINST_EARNED");
        Map<String, Object> legacyAdvance = new HashMap<>(workforceAdvance);
        legacyAdvance.put("clientRequestKey", "mixed-route-driver-earned-advance-key");
        legacyAdvance.put("description", "Concurrent legacy Driver earned advance");

        String workforceRoute = "/api/v1/workforce/members/" + memberId + "/ledger/advances";
        String driverRoute = "/api/v1/drivers/" + driverId + "/ledger/advances";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> workforce = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(workforceRoute, workforceAdvance, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> legacy = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(driverRoute, legacyAdvance, token)
                        .andReturn().getResponse().getStatus();
            });
            ready.await();
            start.countDown();
            assertThat(Arrays.asList(workforce.get(), legacy.get()))
                    .containsExactlyInAnyOrder(201, 400);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "ADVANCE".equals(entry.getTransactionType()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getAmount()).isEqualByComparingTo("300.00"));
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId))
                .filteredOn(entry -> "ADVANCE".equals(entry.getTransactionType()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAmount()).isEqualByComparingTo("300.00");
                    assertThat(entry.getLegacyDriverId()).isEqualTo(driverId);
                    assertThat(entry.getLegacySourceEntryId()).isNotNull();
                });
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("200.00");
        assertThat(workforceLedger.sumBalance(tenantId, memberId)).isEqualByComparingTo("200.00");
        assertThat(journals.findByTenantIdAndPostingKey(tenantId,
                "driver-advance:mixed-route-workforce-earned-advance-key").isPresent()
                ^ journals.findByTenantIdAndPostingKey(tenantId,
                "driver-advance:mixed-route-driver-earned-advance-key").isPresent()).isTrue();
    }

    @Test
    void concurrentMixedRouteRecoverableAdvancesDoNotReduceEarnedBalance() throws Exception {
        Long driverId = createDriver("mixed-route-recoverable-advance-race");
        earn(driverId, "mixed-route-recoverable-advance-race-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> workforceAdvance = new HashMap<>();
        workforceAdvance.put("clientRequestKey", "mixed-route-workforce-recoverable-key");
        workforceAdvance.put("advanceDate", LocalDate.of(2026, 11, 4));
        workforceAdvance.put("amount", new BigDecimal("300.00"));
        workforceAdvance.put("paymentAccountId", cashId);
        workforceAdvance.put("description", "Concurrent Workforce recoverable advance");
        workforceAdvance.put("advanceType", "RECOVERABLE");
        Map<String, Object> legacyAdvance = new HashMap<>(workforceAdvance);
        legacyAdvance.put("clientRequestKey", "mixed-route-driver-recoverable-key");
        legacyAdvance.put("description", "Concurrent legacy Driver recoverable advance");

        String workforceRoute = "/api/v1/workforce/members/" + memberId + "/ledger/advances";
        String driverRoute = "/api/v1/drivers/" + driverId + "/ledger/advances";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> workforce = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(workforceRoute, workforceAdvance, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> legacy = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(driverRoute, legacyAdvance, token)
                        .andReturn().getResponse().getStatus();
            });
            ready.await();
            start.countDown();
            assertThat(Arrays.asList(workforce.get(), legacy.get()))
                    .containsExactlyInAnyOrder(201, 201);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "RECOVERABLE_ADVANCE".equals(entry.getTransactionType()))
                .hasSize(2);
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId))
                .filteredOn(entry -> "RECOVERABLE_ADVANCE".equals(entry.getTransactionType()))
                .hasSize(2);
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("500.00");
        assertThat(workforceLedger.sumBalance(tenantId, memberId)).isEqualByComparingTo("500.00");
        assertThat(workforceLedger.sumRecoverableAdvanceBalance(tenantId, memberId))
                .isEqualByComparingTo("600.00");
        assertThat(journals.findByTenantIdAndPostingKey(
                tenantId, "driver-advance:mixed-route-workforce-recoverable-key")).isPresent();
        assertThat(journals.findByTenantIdAndPostingKey(
                tenantId, "driver-advance:mixed-route-driver-recoverable-key")).isPresent();
    }

    @Test
    void concurrentWorkforceAndLegacyDriverRecoverableAdvanceRoutesWithSameKeyCreateOneMovement()
            throws Exception {
        Long driverId = createDriver("mixed-route-recoverable-same-key-race");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);

        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "mixed-route-recoverable-same-key-race-key");
        advance.put("advanceDate", LocalDate.of(2026, 11, 5));
        advance.put("amount", new BigDecimal("75.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "Concurrent mixed-route recoverable advance retry");
        advance.put("advanceType", "RECOVERABLE");

        String workforceRoute = "/api/v1/workforce/members/" + memberId + "/ledger/advances";
        String driverRoute = "/api/v1/drivers/" + driverId + "/ledger/advances";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> workforce = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(workforceRoute, advance, token).andReturn().getResponse().getStatus();
            });
            Future<Integer> legacy = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson(driverRoute, advance, token).andReturn().getResponse().getStatus();
            });
            ready.await();
            start.countDown();
            assertThat(Arrays.asList(workforce.get(), legacy.get()))
                    .containsExactlyInAnyOrder(201, 201);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(ledger.findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(tenantId, driverId))
                .filteredOn(entry -> "RECOVERABLE_ADVANCE".equals(entry.getTransactionType()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getClientRequestKey())
                            .isEqualTo("mixed-route-recoverable-same-key-race-key");
                    assertThat(entry.getAmount()).isEqualByComparingTo("75.00");
                });
        assertThat(workforceLedger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
                tenantId, memberId))
                .filteredOn(entry -> "RECOVERABLE_ADVANCE".equals(entry.getTransactionType()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getClientRequestKey())
                            .isEqualTo("mixed-route-recoverable-same-key-race-key");
                    assertThat(entry.getWorkforceMemberId()).isEqualTo(memberId);
                    assertThat(entry.getLegacyDriverId()).isEqualTo(driverId);
                    assertThat(entry.getLegacySourceEntryId()).isNotNull();
                    assertThat(entry.getAmount()).isEqualByComparingTo("75.00");
                });
        assertThat(workforceLedger.sumRecoverableAdvanceBalance(tenantId, memberId))
                .isEqualByComparingTo("75.00");
        assertThat(journals.findByTenantIdAndPostingKey(
                tenantId, "driver-advance:mixed-route-recoverable-same-key-race-key"))
                .isPresent();
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
    void workforceMirrorTriggerFailureRollsBackLegacyPaymentAndJournal() throws Exception {
        Long driverId = createDriver("mirror-trigger-rollback");
        earn(driverId, "mirror-trigger-rollback-earned");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ? AND tenant_id = ?",
                Long.class, driverId, tenantId);
        String key = "mirror-trigger-rollback-collision";

        JournalPostingRequest nativeJournalRequest = new JournalPostingRequest(
                "mirror-trigger-native-journal", "WORKFORCE_LEDGER_TEST", memberId,
                LocalDate.of(2026, 11, 2), "Existing native Workforce movement", null,
                Arrays.asList(
                        new JournalLineRequest(expenseId, "Workforce expense",
                                new BigDecimal("10.00"), BigDecimal.ZERO, null, memberId, null),
                        new JournalLineRequest(payableId, "Workforce payable",
                                BigDecimal.ZERO, new BigDecimal("10.00"), null, memberId, null)));
        Long nativeJournalId = postingService.post(tenantId, actorId, nativeJournalRequest).getId();
        jdbcTemplate.update(
                "INSERT INTO workforce_ledger_entries (tenant_id, workforce_member_id, transaction_type, "
                        + "transaction_date, amount, balance_effect, description, client_request_key, shift_id, "
                        + "journal_entry_id, created_by_user_id, created_at, legacy_source_entry_id, legacy_driver_id) "
                        + "VALUES (?, ?, 'ADJUSTMENT', ?, ?, ?, ?, ?, NULL, ?, ?, UTC_TIMESTAMP(6), NULL, NULL)",
                tenantId, memberId, LocalDate.of(2026, 11, 2), new BigDecimal("10.00"),
                new BigDecimal("10.00"), "Existing native movement", key, nativeJournalId, actorId);

        int responseStatus = postPayment(driverId, key, cashId).andReturn().getResponse().getStatus();

        assertThat(responseStatus).isGreaterThanOrEqualTo(400);
        assertThat(ledger.findByTenantIdAndClientRequestKey(tenantId, key)).isEmpty();
        assertThat(journals.findByTenantIdAndPostingKey(tenantId, "driver-payment:" + key)).isEmpty();
        assertThat(workforceLedger.findByTenantIdAndClientRequestKey(tenantId, key))
                .hasValueSatisfying(entry -> {
                    assertThat(entry.getWorkforceMemberId()).isEqualTo(memberId);
                    assertThat(entry.getJournalEntryId()).isEqualTo(nativeJournalId);
                    assertThat(entry.getLegacySourceEntryId()).isNull();
                });
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

        // Native Workforce rows must already obey the same append-only financial
        // invariant as mirrored rows, before the application writer is switched.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE workforce_ledger_entries SET description = ? WHERE id = ?",
                "Attempted mutation", nativeEntry.getId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("WORKFORCE_LEDGER_IS_APPEND_ONLY");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM workforce_ledger_entries WHERE id = ?", nativeEntry.getId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("WORKFORCE_LEDGER_IS_APPEND_ONLY");
        assertThat(workforceLedger.findByTenantIdAndClientRequestKey(tenantId, key))
                .hasValueSatisfying(entry -> assertThat(entry.getDescription())
                        .isEqualTo("Native Workforce movement"));

        Long partialProvenanceJournalId = postingService.post(tenantId, actorId,
                new JournalPostingRequest("native-workforce-partial-provenance-journal",
                        "WORKFORCE_LEDGER_TEST", memberId, LocalDate.of(2026, 11, 1),
                        "Invalid partial provenance test", null,
                        Arrays.asList(
                                new JournalLineRequest(expenseId, "Expense",
                                        new BigDecimal("1.00"), BigDecimal.ZERO, null, memberId, null),
                                new JournalLineRequest(payableId, "Payable",
                                        BigDecimal.ZERO, new BigDecimal("1.00"), null, memberId, null))))
                .getId();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO workforce_ledger_entries (tenant_id, workforce_member_id, transaction_type, "
                        + "transaction_date, amount, balance_effect, description, client_request_key, shift_id, "
                        + "journal_entry_id, created_by_user_id, created_at, legacy_source_entry_id, legacy_driver_id) "
                        + "VALUES (?, ?, 'ADJUSTMENT', ?, ?, ?, ?, ?, NULL, ?, ?, UTC_TIMESTAMP(6), NULL, ?)",
                tenantId, memberId, LocalDate.of(2026, 11, 1), new BigDecimal("1.00"),
                new BigDecimal("1.00"), "Invalid partial provenance", "native-workforce-partial-provenance",
                partialProvenanceJournalId, actorId, driverId))
                .isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class);
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
