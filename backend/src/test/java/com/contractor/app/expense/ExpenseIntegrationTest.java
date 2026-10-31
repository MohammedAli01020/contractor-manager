package com.contractor.app.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.contractor.app.expense.infra.ExpenseRepository;
import com.contractor.app.expense.infra.ExpensePaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MvcResult;

class ExpenseIntegrationTest extends AbstractIntegrationTest {

    @Autowired private DefaultChartOfAccountsService defaultAccounts;
    @Autowired private ChartAccountRepository accounts;
    @Autowired private JournalEntryRepository journalEntries;
    @Autowired private JournalLineRepository journalLines;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ExpensePaymentRepository paymentRepository;
    @SpyBean private JournalPostingService journalPostingService;

    private Long tenantId;
    private Long actorUserId;
    private String token;
    private Long expenseAccountId;
    private Long cashAccountId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode registered = registerTenant("Expense " + uniqueName("tenant"), uniqueName("owner"));
        tenantId = registered.path("user").path("tenantId").asLong();
        actorUserId = registered.path("user").path("id").asLong();
        token = registered.path("accessToken").asText();
        defaultAccounts.initializeForTenant(tenantId);
        expenseAccountId = account("5100").getId();
        cashAccountId = account("1000").getId();
    }

    @Test
    void createsExpenseAndPostsBalancedJournalAutomatically() throws Exception {
        JsonNode created = createExpense("expense-create-1", "3000.00");

        assertThat(created.path("amount").decimalValue()).isEqualByComparingTo("3000.00");
        assertThat(created.path("paidAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(created.path("remainingAmount").decimalValue()).isEqualByComparingTo("3000.00");
        assertThat(created.path("journalEntryId").asLong()).isPositive();

        var lines = journalLines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                tenantId, created.path("journalEntryId").asLong());
        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(line -> line.getDebit().toPlainString())
                .contains("3000.00", "0.00");
        assertThat(lines).extracting(line -> line.getCredit().toPlainString())
                .contains("0.00", "3000.00");
        assertThat(journalEntries.findByTenantIdAndPostingKey(tenantId, "expense:expense-create-1"))
                .isPresent();
    }

    @Test
    void partialPaymentUpdatesRemainingAndDuplicateRetryDoesNotPostTwice() throws Exception {
        seedCashBalance(new BigDecimal("5000.00"));
        JsonNode expense = createExpense("expense-payment-base", "3000.00");
        long expenseId = expense.path("id").asLong();

        JsonNode first = addPayment(expenseId, "payment-once", "1000.00");
        assertThat(first.path("paidAmount").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(first.path("remainingAmount").decimalValue()).isEqualByComparingTo("2000.00");

        JsonNode retry = addPayment(expenseId, "payment-once", "1000.00");
        assertThat(retry.path("paidAmount").decimalValue()).isEqualByComparingTo("1000.00");
        var paymentJournal = journalEntries.findByTenantIdAndPostingKey(
                tenantId, "expense-payment:payment-once");
        assertThat(paymentJournal).isPresent();
        var paymentLines = journalLines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                tenantId, paymentJournal.get().getId());
        assertThat(paymentLines).hasSize(2);
        assertThat(paymentLines).extracting(line -> line.getDebit().toPlainString())
                .contains("1000.00", "0.00");
        assertThat(paymentLines).extracting(line -> line.getCredit().toPlainString())
                .contains("0.00", "1000.00");

        JsonNode paid = addPayment(expenseId, "payment-final", "2000.00");
        assertThat(paid.path("paidAmount").decimalValue()).isEqualByComparingTo("3000.00");
        assertThat(paid.path("remainingAmount").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsOverpaymentWithoutCreatingPaymentJournal() throws Exception {
        JsonNode expense = createExpense("expense-overpay", "500.00");
        Map<String, Object> payment = paymentBody("payment-too-large", "500.01");

        postJson("/api/v1/expenses/" + expense.path("id").asLong() + "/payments", payment, token)
                .andExpect(status().isBadRequest());

        assertThat(body(getJson("/api/v1/expenses", token))).hasSize(1);
        assertThat(journalEntries.findByTenantIdAndPostingKey(tenantId, "expense-payment:payment-too-large"))
                .isEmpty();
    }

    @Test
    void expenseListIsIsolatedBetweenTenants() throws Exception {
        createExpense("tenant-one-expense", "750.00");
        String otherToken = registerTenant("Other Expense Co", uniqueName("otherowner"))
                .path("accessToken").asText();

        JsonNode otherList = body(getJson("/api/v1/expenses", otherToken).andExpect(status().isOk()));
        assertThat(otherList).isEmpty();
    }

    @Test
    void viewerCanReadExpensesButCannotCreateOrPay() throws Exception {
        JsonNode expense = createExpense("expense-viewer-access", "250.00");
        String viewerName = uniqueName("expenseviewer");
        Map<String, Object> viewerRequest = new HashMap<>(userBody(viewerName));
        viewerRequest.put("role", "VIEWER");
        body(postJson("/api/v1/users", viewerRequest, token).andExpect(status().isCreated()));
        String viewerToken = login(viewerName);

        getJson("/api/v1/expenses", viewerToken).andExpect(status().isOk());
        postJson("/api/v1/expenses", expenseBody("viewer-must-not-create", "50.00"), viewerToken)
                .andExpect(status().isForbidden());
        postJson("/api/v1/expenses/" + expense.path("id").asLong() + "/payments",
                paymentBody("viewer-must-not-pay", "50.00"), viewerToken)
                .andExpect(status().isForbidden());

        assertThat(journalEntries.findByTenantIdAndPostingKey(tenantId, "expense-payment:viewer-must-not-pay"))
                .isEmpty();
    }

    @Test
    void anotherTenantCannotPayExpenseOrUseForeignExpenseAccount() throws Exception {
        JsonNode expense = createExpense("expense-cross-tenant", "600.00");
        JsonNode otherTenant = registerTenant("Other Expense Tenant", uniqueName("otherexpense"));
        Long otherTenantId = otherTenant.path("user").path("tenantId").asLong();
        String otherToken = otherTenant.path("accessToken").asText();

        postJson("/api/v1/expenses/" + expense.path("id").asLong() + "/payments",
                paymentBody("foreign-tenant-payment", "100.00"), otherToken)
                .andExpect(status().isNotFound());

        postJson("/api/v1/expenses",
                expenseBody("foreign-account-expense", "100.00"), otherToken)
                .andExpect(status().isNotFound());

        assertThat(journalEntries.findByTenantIdAndPostingKey(tenantId, "expense-payment:foreign-tenant-payment"))
                .isEmpty();
        assertThat(expensesForTenant(otherToken)).isEmpty();
        assertThat(otherTenantId).isNotEqualTo(tenantId);
    }

    @Test
    void operatorCanCreateExpenseAndRecordPayment() throws Exception {
        seedCashBalance(new BigDecimal("500.00"));
        String operatorName = uniqueName("expenseoperator");
        body(postJson("/api/v1/users", userBody(operatorName), token).andExpect(status().isCreated()));
        String operatorToken = login(operatorName);

        JsonNode created = body(postJson("/api/v1/expenses",
                expenseBody("operator-expense", "200.00"), operatorToken)
                .andExpect(status().isCreated()));
        postJson("/api/v1/expenses/" + created.path("id").asLong() + "/payments",
                paymentBody("operator-payment", "50.00"), operatorToken)
                .andExpect(status().isCreated());
    }

    @Test
    void identicalExpenseRetryReturnsExistingRecordInsteadOfDuplicating() throws Exception {
        JsonNode first = createExpense("expense-idempotent", "125.00");
        JsonNode retry = createExpense("expense-idempotent", "125.00");

        assertThat(retry.path("id").asLong()).isEqualTo(first.path("id").asLong());
        assertThat(body(getJson("/api/v1/expenses", token))).hasSize(1);
        assertThat(journalEntries.findByTenantIdAndPostingKey(tenantId, "expense:expense-idempotent"))
                .isPresent();
    }

    @Test
    void rollsBackExpenseWhenJournalPostingFails() throws Exception {
        doThrow(new IllegalStateException("Simulated journal persistence failure"))
                .when(journalPostingService)
                .post(anyLong(), anyLong(), any(JournalPostingRequest.class));

        postJson("/api/v1/expenses", expenseBody("expense-rollback", "900.00"), token)
                .andExpect(status().is5xxServerError());

        assertThat(body(getJson("/api/v1/expenses", token))).isEmpty();
        assertThat(journalEntries.findByTenantIdAndPostingKey(tenantId, "expense:expense-rollback"))
                .isEmpty();
    }

    @Test
    void concurrentPaymentsCannotOverpayTheSameExpense() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        JsonNode expense = createExpense("expense-concurrent", "1000.00");
        long expenseId = expense.path("id").asLong();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentPayment(
                    expenseId, "concurrent-payment-a", ready, start));
            Future<Integer> second = executor.submit(() -> concurrentPayment(
                    expenseId, "concurrent-payment-b", ready, start));

            ready.await();
            start.countDown();

            assertThat(java.util.Arrays.asList(first.get(), second.get()))
                    .containsExactlyInAnyOrder(201, 400);
        } finally {
            executor.shutdownNow();
        }

        JsonNode refreshed = body(getJson("/api/v1/expenses", token));
        assertThat(refreshed.get(0).path("paidAmount").decimalValue())
                .isEqualByComparingTo("700.00");
        assertThat(journalEntries.findByTenantIdAndPostingKey(tenantId, "expense-payment:concurrent-payment-a")
                .isPresent() ^ journalEntries.findByTenantIdAndPostingKey(
                        tenantId, "expense-payment:concurrent-payment-b").isPresent()).isTrue();
    }

    @Test
    void concurrentIdenticalExpenseCreatesReturnTheSameSourceAndJournal() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> concurrentCreateExpense(
                    "expense-same-key-race", ready, start));
            Future<MvcResult> second = executor.submit(() -> concurrentCreateExpense(
                    "expense-same-key-race", ready, start));
            ready.await();
            start.countDown();

            MvcResult firstResult = first.get();
            MvcResult secondResult = second.get();
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(201);
            JsonNode firstBody = objectMapper.readTree(firstResult.getResponse().getContentAsString());
            JsonNode secondBody = objectMapper.readTree(secondResult.getResponse().getContentAsString());
            assertThat(firstBody.path("id").asLong()).isEqualTo(secondBody.path("id").asLong());
            assertThat(expenseRepository.findByTenantIdOrderByExpenseDateDescIdDesc(tenantId)).hasSize(1);
            assertThat(journalEntries.findByTenantIdAndPostingKey(tenantId,
                    "expense:expense-same-key-race")).isPresent();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentPaymentKeyAcrossDifferentExpensesProducesOnePaymentAndOneConflict() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        JsonNode firstExpense = createExpense("expense-payment-race-a", "500.00");
        JsonNode secondExpense = createExpense("expense-payment-race-b", "500.00");
        long firstId = firstExpense.path("id").asLong();
        long secondId = secondExpense.path("id").asLong();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentPayment(
                    firstId, "same-payment-key-race", ready, start));
            Future<Integer> second = executor.submit(() -> concurrentPayment(
                    secondId, "same-payment-key-race", ready, start));
            ready.await();
            start.countDown();

            assertThat(java.util.Arrays.asList(first.get(), second.get()))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(paymentRepository.findByTenantIdAndPaymentKey(tenantId, "same-payment-key-race"))
                .isPresent();
        assertThat(journalEntries.findByTenantIdAndPostingKey(
                tenantId, "expense-payment:same-payment-key-race")).isPresent();
        assertThat(journalEntries.findByTenantIdAndPostingKey(
                tenantId, "expense-payment:same-payment-key-race")).hasValueSatisfying(entry ->
                assertThat(journalLines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                        tenantId, entry.getId())).hasSize(2));
    }

    private MvcResult concurrentCreateExpense(String key, CountDownLatch ready,
                                               CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return postJson("/api/v1/expenses", expenseBody(key, "125.00"), token).andReturn();
    }

    private int concurrentPayment(long expenseId, String key, CountDownLatch ready,
                                  CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        MvcResult result = postJson("/api/v1/expenses/" + expenseId + "/payments",
                paymentBody(key, "700.00"), token).andReturn();
        return result.getResponse().getStatus();
    }

    private String login(String username) throws Exception {
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("username", username);
        credentials.put("password", PASSWORD);
        return body(postJson("/api/v1/auth/login", credentials, null)
                .andExpect(status().isOk())).path("accessToken").asText();
    }

    private JsonNode expensesForTenant(String accessToken) throws Exception {
        return body(getJson("/api/v1/expenses", accessToken).andExpect(status().isOk()));
    }

    private JsonNode createExpense(String key, String amount) throws Exception {
        return body(postJson("/api/v1/expenses", expenseBody(key, amount), token)
                .andExpect(status().isCreated()));
    }

    private Map<String, Object> expenseBody(String key, String amount) {
        Map<String, Object> request = new HashMap<>();
        request.put("clientRequestKey", key);
        request.put("expenseDate", LocalDate.of(2026, 10, 31));
        request.put("categoryCode", "EQUIPMENT_OPERATING");
        request.put("description", "Fuel and maintenance");
        request.put("supplierName", "Test supplier");
        request.put("amount", new BigDecimal(amount));
        request.put("expenseAccountId", expenseAccountId);
        return request;
    }

    private JsonNode addPayment(long expenseId, String key, String amount) throws Exception {
        return body(postJson("/api/v1/expenses/" + expenseId + "/payments",
                paymentBody(key, amount), token).andExpect(status().isCreated()));
    }

    private Map<String, Object> paymentBody(String key, String amount) {
        Map<String, Object> request = new HashMap<>();
        request.put("paymentKey", key);
        request.put("paymentDate", LocalDate.of(2026, 10, 31));
        request.put("amount", new BigDecimal(amount));
        request.put("paymentAccountId", cashAccountId);
        return request;
    }


    private void seedCashBalance(BigDecimal amount) {
        Long equityAccountId = account("3000").getId();
        journalPostingService.post(tenantId, actorUserId, new JournalPostingRequest(
                "test-cash-opening:" + uniqueName("expense"), "TEST_CASH_OPENING", null,
                LocalDate.of(2026, 10, 31), "Test fixture opening cash", null, java.util.Arrays.asList(
                new JournalLineRequest(cashAccountId, "Opening cash", amount, BigDecimal.ZERO, null, null),
                new JournalLineRequest(equityAccountId, "Opening capital", BigDecimal.ZERO, amount, null, null))));
    }

    private ChartAccount account(String code) {
        return accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, code)
                .orElseThrow(() -> new AssertionError("Missing default account " + code));
    }
}
