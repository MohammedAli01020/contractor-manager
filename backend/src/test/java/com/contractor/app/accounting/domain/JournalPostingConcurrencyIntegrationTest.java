package com.contractor.app.accounting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.contractor.app.common.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.test.mock.mockito.SpyBean;

class JournalPostingConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JournalPostingService postingService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ChartAccountRepository accounts;
    @Autowired private JournalEntryRepository entries;
    @Autowired private JournalLineRepository lineRepository;
    @SpyBean private JournalLineRepository lines;

    private Long tenantId;
    private Long actorUserId;
    private Long debitAccountId;
    private Long creditAccountId;
    private Long cashAccountId;
    private Long bankAccountId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode registered = registerTenant("Accounting " + uniqueName("tenant"), uniqueName("owner"));
        tenantId = registered.path("user").path("tenantId").asLong();
        actorUserId = registered.path("user").path("id").asLong();
        debitAccountId = accounts.saveAndFlush(new ChartAccount(
                tenantId, uniqueName("D"), "Debit account", AccountType.ASSET, null, false)).getId();
        creditAccountId = accounts.saveAndFlush(new ChartAccount(
                tenantId, uniqueName("C"), "Credit account", AccountType.REVENUE, null, false)).getId();
        cashAccountId = accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Default cash account was not created"))
                .getId();
        bankAccountId = accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1010")
                .orElseThrow(() -> new IllegalStateException("Default bank account was not created"))
                .getId();
    }

    @Test
    void concurrentIdenticalRequestsResolveToOneJournalEntry() throws Exception {
        JournalPostingRequest request = request(uniqueName("concurrent"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<JournalEntry> first = pool.submit(() -> postTogether(request, ready, start));
            Future<JournalEntry> second = pool.submit(() -> postTogether(request, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            JournalEntry firstResult = first.get(30, TimeUnit.SECONDS);
            JournalEntry secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResult.getId()).isEqualTo(secondResult.getId());
            assertThat(entries.findByTenantIdAndPostingKey(tenantId, request.getPostingKey())).isPresent();
            assertThat(lineRepository.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                    tenantId, firstResult.getId())).hasSize(2);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCashOutflowsCannotSpendTheSameAvailableBalanceTwice() throws Exception {
        JournalPostingRequest opening = new JournalPostingRequest(uniqueName("cash-opening"), "CASH_OPENING",
                10L, LocalDate.of(2026, 10, 31), "Opening cash balance", null, Arrays.asList(
                new JournalLineRequest(cashAccountId, "Opening cash", new BigDecimal("100.00"),
                        BigDecimal.ZERO.setScale(2), null, null),
                new JournalLineRequest(creditAccountId, "Opening equity", BigDecimal.ZERO.setScale(2),
                        new BigDecimal("100.00"), null, null)));
        postingService.post(tenantId, actorUserId, opening);

        JournalPostingRequest firstRequest = liquidityOutRequest(uniqueName("cash-out-a"), cashAccountId, new BigDecimal("80.00"));
        JournalPostingRequest secondRequest = liquidityOutRequest(uniqueName("cash-out-b"), cashAccountId, new BigDecimal("80.00"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<JournalEntry> first = pool.submit(() -> postTogether(firstRequest, ready, start));
            Future<JournalEntry> second = pool.submit(() -> postTogether(secondRequest, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            int insufficient = 0;
            for (Future<JournalEntry> result : Arrays.asList(first, second)) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) ex.getCause()).getStatus().value()).isEqualTo(409);
                    insufficient++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(insufficient).isEqualTo(1);
            assertThat(lineRepository.calculateDebitNormalBalance(tenantId, cashAccountId))
                    .isEqualByComparingTo("20.00");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }


    @Test
    void concurrentCashAndBankOutflowsCannotSpendEitherBalanceTwice() throws Exception {
        openLiquidityAccount(cashAccountId, uniqueName("cash-opening"), new BigDecimal("100.00"));
        openLiquidityAccount(bankAccountId, uniqueName("bank-opening"), new BigDecimal("100.00"));

        JournalPostingRequest[] requests = {
                liquidityOutRequest(uniqueName("cash-out-a"), cashAccountId, new BigDecimal("80.00")),
                liquidityOutRequest(uniqueName("cash-out-b"), cashAccountId, new BigDecimal("80.00")),
                liquidityOutRequest(uniqueName("bank-out-a"), bankAccountId, new BigDecimal("80.00")),
                liquidityOutRequest(uniqueName("bank-out-b"), bankAccountId, new BigDecimal("80.00"))
        };
        CountDownLatch ready = new CountDownLatch(requests.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests.length);
        try {
            java.util.List<Future<JournalEntry>> results = new java.util.ArrayList<>();
            for (JournalPostingRequest request : requests) {
                results.add(pool.submit(() -> postTogether(request, ready, start)));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            int insufficient = 0;
            for (Future<JournalEntry> result : results) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) ex.getCause()).getStatus().value()).isEqualTo(409);
                    insufficient++;
                }
            }
            assertThat(successes).isEqualTo(2);
            assertThat(insufficient).isEqualTo(2);
            assertThat(lineRepository.calculateDebitNormalBalance(tenantId, cashAccountId))
                    .isEqualByComparingTo("20.00");
            assertThat(lineRepository.calculateDebitNormalBalance(tenantId, bankAccountId))
                    .isEqualByComparingTo("20.00");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void oppositeDirectionCashBankTransfersUseConsistentLockOrder() throws Exception {
        openLiquidityAccount(cashAccountId, uniqueName("cash-opening"), new BigDecimal("100.00"));
        openLiquidityAccount(bankAccountId, uniqueName("bank-opening"), new BigDecimal("100.00"));

        JournalPostingRequest cashToBank = transferRequest(
                uniqueName("cash-to-bank"), bankAccountId, cashAccountId, new BigDecimal("10.00"));
        JournalPostingRequest bankToCash = transferRequest(
                uniqueName("bank-to-cash"), cashAccountId, bankAccountId, new BigDecimal("10.00"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<JournalEntry> first = pool.submit(() -> postTogether(cashToBank, ready, start));
            Future<JournalEntry> second = pool.submit(() -> postTogether(bankToCash, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS)).isNotNull();
            assertThat(second.get(30, TimeUnit.SECONDS)).isNotNull();
            assertThat(lineRepository.calculateDebitNormalBalance(tenantId, cashAccountId))
                    .isEqualByComparingTo("100.00");
            assertThat(lineRepository.calculateDebitNormalBalance(tenantId, bankAccountId))
                    .isEqualByComparingTo("100.00");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentReversalsCreateAtMostOneReversal() throws Exception {
        JournalEntry original = postingService.post(tenantId, actorUserId, request(uniqueName("original")));
        JournalPostingRequest firstRequest = reversalRequest(uniqueName("reversal-a"), original.getId());
        JournalPostingRequest secondRequest = reversalRequest(uniqueName("reversal-b"), original.getId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<JournalEntry> first = pool.submit(() -> postTogether(firstRequest, ready, start));
            Future<JournalEntry> second = pool.submit(() -> postTogether(secondRequest, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<JournalEntry> result : Arrays.asList(first, second)) {
                try {
                    JournalEntry reversal = result.get(30, TimeUnit.SECONDS);
                    successes++;
                    assertThat(reversal.getReversalOfEntryId()).isEqualTo(original.getId());
                } catch (java.util.concurrent.ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) ex.getCause()).getStatus().value()).isEqualTo(409);
                    conflicts++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(entries.existsByTenantIdAndReversalOfEntryId(tenantId, original.getId())).isTrue();
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void databaseRejectsMutationAndDeletionOfPostedJournalAndLines() {
        JournalEntry entry = postingService.post(tenantId, actorUserId, request(uniqueName("immutable")));
        Long lineId = lineRepository.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                tenantId, entry.getId()).get(0).getId();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbcTemplate.update("UPDATE journal_entries SET description = 'tampered' WHERE id = ?", entry.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("POSTED_JOURNAL_IS_IMMUTABLE");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM journal_entries WHERE id = ?", entry.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("POSTED_JOURNAL_CANNOT_BE_DELETED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbcTemplate.update("UPDATE journal_lines SET debit = 999.00 WHERE id = ?", lineId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("POSTED_JOURNAL_LINES_ARE_IMMUTABLE");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM journal_lines WHERE id = ?", lineId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("POSTED_JOURNAL_LINES_CANNOT_BE_DELETED");
    }

    @Test
    void failedLinePersistenceRollsBackEntryHeader() {
        String key = uniqueName("rollback");
        doThrow(new IllegalStateException("simulated persistence failure"))
                .when(lines).saveAll(anyList());

        assertThrows(RuntimeException.class, () -> postingService.post(tenantId, actorUserId, request(key)));
        assertThat(entries.findByTenantIdAndPostingKey(tenantId, key)).isEmpty();
    }

    private JournalEntry postTogether(JournalPostingRequest request, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent posting");
        }
        return postingService.post(tenantId, actorUserId, request);
    }

    private void openLiquidityAccount(Long accountId, String key, BigDecimal amount) {
        postingService.post(tenantId, actorUserId, new JournalPostingRequest(key, "LIQUIDITY_OPENING", null,
                LocalDate.of(2026, 10, 31), "Opening liquidity balance", null, Arrays.asList(
                new JournalLineRequest(accountId, "Opening balance", amount, BigDecimal.ZERO, null, null),
                new JournalLineRequest(creditAccountId, "Opening offset", BigDecimal.ZERO, amount, null, null))));
    }

    private JournalPostingRequest liquidityOutRequest(String key, Long accountId, BigDecimal amount) {
        return new JournalPostingRequest(key, "LIQUIDITY_OUTFLOW", null, LocalDate.of(2026, 10, 31),
                "Liquidity outflow integration test", null, Arrays.asList(
                new JournalLineRequest(debitAccountId, "Expense", amount, BigDecimal.ZERO, null, null),
                new JournalLineRequest(accountId, "Cash or bank payment", BigDecimal.ZERO, amount, null, null)));
    }

    private JournalPostingRequest transferRequest(String key, Long receivingAccountId,
                                                   Long payingAccountId, BigDecimal amount) {
        return new JournalPostingRequest(key, "CASH_BANK_TRANSFER", null, LocalDate.of(2026, 10, 31),
                "Cash/bank transfer integration test", null, Arrays.asList(
                new JournalLineRequest(receivingAccountId, "Transfer received", amount, BigDecimal.ZERO, null, null),
                new JournalLineRequest(payingAccountId, "Transfer paid", BigDecimal.ZERO, amount, null, null)));
    }

    private JournalPostingRequest request(String key) {
        return new JournalPostingRequest(key, "INTEGRATION_TEST", 1L, LocalDate.of(2026, 10, 28),
                "Accounting integration test", null, Arrays.asList(
                new JournalLineRequest(debitAccountId, "Debit", new BigDecimal("100.00"),
                        BigDecimal.ZERO.setScale(2), null, null),
                new JournalLineRequest(creditAccountId, "Credit", BigDecimal.ZERO.setScale(2),
                        new BigDecimal("100.00"), null, null)));
    }

    private JournalPostingRequest reversalRequest(String key, Long originalEntryId) {
        return new JournalPostingRequest(key, "INTEGRATION_TEST_REVERSAL", 2L,
                LocalDate.of(2026, 10, 28), "Accounting integration reversal", originalEntryId,
                Arrays.asList(
                new JournalLineRequest(debitAccountId, "Debit", BigDecimal.ZERO.setScale(2),
                        new BigDecimal("100.00"), null, null),
                new JournalLineRequest(creditAccountId, "Credit", new BigDecimal("100.00"),
                        BigDecimal.ZERO.setScale(2), null, null)));
    }
}
