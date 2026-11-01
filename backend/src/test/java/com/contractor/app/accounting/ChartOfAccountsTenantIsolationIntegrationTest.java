package com.contractor.app.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.domain.AccountType;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class ChartOfAccountsTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final String ACCOUNTS_ENDPOINT = "/api/v1/accounting/accounts";

    @Autowired
    private ChartAccountRepository chartAccountRepository;

    @Autowired
    private DefaultChartOfAccountsService defaultAccounts;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenA;
    private String tokenB;
    private long tenantA;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode tenantAResponse = registerTenant("Accounting Tenant A", uniqueName("accountinga"));
        JsonNode tenantBResponse = registerTenant("Accounting Tenant B", uniqueName("accountingb"));

        tokenA = tenantAResponse.path("accessToken").asText();
        tokenB = tenantBResponse.path("accessToken").asText();
        tenantA = tenantAResponse.path("user").path("tenantId").asLong();

        chartAccountRepository.saveAndFlush(new ChartAccount(
                tenantA, "9900", "Tenant A private account", AccountType.EXPENSE, null, false));
    }

    @Test
    void listsAccountsInCodeOrderAndOnlyForTheAuthenticatedTenant() throws Exception {
        JsonNode accountsA = body(getJson(ACCOUNTS_ENDPOINT, tokenA).andExpect(status().isOk()));
        JsonNode accountsB = body(getJson(ACCOUNTS_ENDPOINT, tokenB).andExpect(status().isOk()));

        // The tenant-specific chart may gain new system accounts over time.
        // Assert isolation and ordering without coupling this test to catalog size.
        assertThat(accountsA.size()).isEqualTo(accountsB.size() + 1);
        assertThat(accountsA.get(0).path("accountCode").asText()).isEqualTo("1000");
        assertThat(accountsA.get(accountsA.size() - 1).path("accountCode").asText()).isEqualTo("9900");
        assertThat(accountsA.toString()).contains("Tenant A private account");
        assertThat(accountsB.toString()).doesNotContain("Tenant A private account");
    }


    @Test
    void concurrentInitializationCreatesOneCompleteDefaultChartForTenant() throws Exception {
        // Keep the tenant-specific account, but simulate a tenant with no default chart.
        jdbcTemplate.update("DELETE FROM chart_of_accounts WHERE tenant_id = ? AND account_code <> '9900'", tenantA);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                defaultAccounts.initializeForTenant(tenantA);
                return null;
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                defaultAccounts.initializeForTenant(tenantA);
                return null;
            });

            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(chartAccountRepository.findByTenantIdOrderByAccountCodeAsc(tenantA))
                .hasSize(15)
                .extracting(ChartAccount::getAccountCode)
                .doesNotHaveDuplicates()
                .contains("1000", "1010", "1300", "5200", "9900");
    }

    @Test
    void requiresAuthentication() throws Exception {
        getJson(ACCOUNTS_ENDPOINT, null).andExpect(status().isUnauthorized());
    }
}
