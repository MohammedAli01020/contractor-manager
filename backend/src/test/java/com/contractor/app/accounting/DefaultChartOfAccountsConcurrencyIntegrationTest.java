package com.contractor.app.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.SystemAccountCode;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.tenant.infra.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies the tenant-lock/current-read initialization protocol under real MySQL concurrency. */
class DefaultChartOfAccountsConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ChartAccountRepository accounts;
    @Autowired private TenantRepository tenants;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentInitializersCreateOneDefaultChartWithoutDuplicateKeyFailures() throws Exception {
        JsonNode registered = registerTenant("Concurrent chart tenant", uniqueName("chartowner"));
        Long tenantId = registered.path("user").path("tenantId").asLong();

        // Both transactions establish their REPEATABLE READ snapshots before either obtains
        // the tenant row lock. The second transaction must see the first transaction's inserts
        // through the locking/current read after it finally acquires that lock.
        CountDownLatch bothInitialReads = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> initializeDefaultsWithDatabaseProtocol(tenantId, bothInitialReads));
            Future<?> second = executor.submit(() -> initializeDefaultsWithDatabaseProtocol(tenantId, bothInitialReads));

            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<ChartAccount> created = accounts.findByTenantIdOrderByAccountCodeAsc(tenantId);
        assertThat(created).hasSize(SystemAccountCode.values().length);
        assertThat(created).extracting(ChartAccount::getAccountCode).doesNotHaveDuplicates();
    }

    private void initializeDefaultsWithDatabaseProtocol(Long tenantId, CountDownLatch bothInitialReads) {
        new TransactionTemplate(transactionManager).execute(status -> {
            accounts.findByTenantIdOrderByAccountCodeAsc(tenantId); // establish consistent-read snapshot
            bothInitialReads.countDown();
            try {
                if (!bothInitialReads.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent initial reads did not rendezvous.");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while coordinating concurrent initialization.", interrupted);
            }

            tenants.findByIdForUpdate(tenantId)
                    .orElseThrow(() -> new IllegalStateException("Tenant disappeared during test."));
            Set<String> currentCodes = accounts.findByTenantIdOrderByAccountCodeAscForUpdate(tenantId).stream()
                    .map(ChartAccount::getAccountCode)
                    .collect(Collectors.toCollection(HashSet::new));
            List<ChartAccount> missing = new ArrayList<>();
            for (SystemAccountCode code : SystemAccountCode.values()) {
                if (!currentCodes.contains(code.getCode())) {
                    missing.add(new ChartAccount(tenantId, code.getCode(), code.getDefaultName(),
                            code.getAccountType(), null, true, code.isCashBankAccount()));
                }
            }
            if (!missing.isEmpty()) {
                accounts.saveAll(missing);
            }
            return null;
        });
    }
}
