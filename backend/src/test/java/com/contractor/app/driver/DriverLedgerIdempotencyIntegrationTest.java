package com.contractor.app.driver;

import static org.assertj.core.api.Assertions.assertThat;
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

class DriverLedgerIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private DefaultChartOfAccountsService defaultAccounts;
    @Autowired private ChartAccountRepository accounts;
    @Autowired private JournalEntryRepository journals;
    @Autowired private JournalPostingService postingService;
    @Autowired private DriverLedgerEntryRepository ledger;

    private Long tenantId;
    private Long actorId;
    private String token;
    private Long cashId;
    private Long bankId;
    private Long expenseId;
    private Long payableId;
    private Long recoverableAdvanceId;
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
        recoverableAdvanceId = account("1300").getId();
        equityId = account("3000").getId();
        openLiquidity(cashId, "cash-opening", new BigDecimal("2000.00"));
        openLiquidity(bankId, "bank-opening", new BigDecimal("2000.00"));
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
                .extracting(DriverLedgerEntry::getId).containsExactly(entry.getId());
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
                "Accrued driver wages", null, Arrays.asList(
                new JournalLineRequest(expenseId, "Driver wages", new BigDecimal("500.00"),
                        BigDecimal.ZERO, null, null),
                new JournalLineRequest(payableId, "Driver payable", BigDecimal.ZERO,
                        new BigDecimal("500.00"), driverId, null))));
        ledger.saveAndFlush(new DriverLedgerEntry(tenantId, driverId, "EARNING",
                LocalDate.of(2026, 10, 31), new BigDecimal("500.00"), new BigDecimal("500.00"),
                "Accrued wages", uniqueName(key), null, journal.getId(), actorId));
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
