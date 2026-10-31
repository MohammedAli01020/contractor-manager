package com.contractor.app.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

/** Verifies same-tenant default chart initialization under real MySQL concurrency. */
class DefaultChartOfAccountsConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private DefaultChartOfAccountsService defaults;
    @SpyBean private ChartAccountRepository accounts;

    @Test
    void concurrentInitializersCreateOneDefaultChartWithoutDuplicateKeyFailures() throws Exception {
        JsonNode registered = registerTenant("Concurrent chart tenant", uniqueName("chartowner"));
        Long tenantId = registered.path("user").path("tenantId").asLong();

        // Force both transactions to complete their initial, non-locking read before either
        // can acquire the tenant row lock. The second transaction must then use a current read.
        CountDownLatch bothInitialReads = new CountDownLatch(2);
        AtomicInteger initialReadCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (initialReadCount.incrementAndGet() <= 2) {
                bothInitialReads.countDown();
                if (!bothInitialReads.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent initial reads did not rendezvous.");
                }
            }
            return invocation.callRealMethod();
        }).when(accounts).findByTenantIdOrderByAccountCodeAsc(tenantId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> defaults.initializeForTenant(tenantId));
            Future<?> second = executor.submit(() -> defaults.initializeForTenant(tenantId));

            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<com.contractor.app.accounting.domain.ChartAccount> created =
                accounts.findByTenantIdOrderByAccountCodeAsc(tenantId);
        assertThat(created).hasSize(15);
        assertThat(created).extracting(com.contractor.app.accounting.domain.ChartAccount::getAccountCode)
                .doesNotHaveDuplicates();
    }
}
