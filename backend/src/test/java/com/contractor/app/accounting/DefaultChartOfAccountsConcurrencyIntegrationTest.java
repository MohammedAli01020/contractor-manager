package com.contractor.app.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.SystemAccountCode;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies the real default-chart service under concurrent initialization on MySQL. */
class DefaultChartOfAccountsConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ChartAccountRepository accounts;
    @Autowired private DefaultChartOfAccountsService defaultChart;
    
    @Test
    void concurrentInitializersCreateOneDefaultChartWithoutDuplicateKeyFailures() throws Exception {
        JsonNode registered = registerTenant("Concurrent chart tenant", uniqueName("chartowner"));
        Long tenantId = registered.path("user").path("tenantId").asLong();

        // Registration initializes defaults. Remove them to make the race meaningful:
        // both service calls must start from a tenant with no system accounts.
        List<ChartAccount> registeredDefaults = accounts.findByTenantIdOrderByAccountCodeAsc(tenantId);
        assertThat(registeredDefaults).hasSize(SystemAccountCode.values().length);
        accounts.deleteAll(registeredDefaults);
        accounts.flush();
        assertThat(accounts.findByTenantIdOrderByAccountCodeAsc(tenantId)).isEmpty();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> initializeDefaults(tenantId, start));
            Future<?> second = executor.submit(() -> initializeDefaults(tenantId, start));
            start.countDown();

            // Invoke the Spring service proxy from independent worker threads. Each call gets
            // its own transaction and executes the production tenant-lock/current-read path.
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<ChartAccount> created = accounts.findByTenantIdOrderByAccountCodeAsc(tenantId);
        assertThat(created).hasSize(SystemAccountCode.values().length);
        assertThat(created).extracting(ChartAccount::getAccountCode).doesNotHaveDuplicates();
        assertThat(created).extracting(ChartAccount::getAccountCode)
                .containsExactlyInAnyOrder(
                        java.util.Arrays.stream(SystemAccountCode.values())
                                .map(SystemAccountCode::getCode)
                                .toArray(String[]::new));
    }

    private void initializeDefaults(Long tenantId, CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent initialization did not start.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before concurrent initialization.", interrupted);
        }
        defaultChart.initializeForTenant(tenantId);
    }
}
