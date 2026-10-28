package com.contractor.app.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.domain.AccountType;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChartOfAccountsTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final String CHART_ENDPOINT = "/api/v1/accounting/chart-of-accounts";

    @Autowired
    private ChartAccountRepository chartAccountRepository;

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
        JsonNode accountsA = body(getJson(CHART_ENDPOINT, tokenA).andExpect(status().isOk()));
        JsonNode accountsB = body(getJson(CHART_ENDPOINT, tokenB).andExpect(status().isOk()));

        assertThat(accountsA.size()).isEqualTo(14);
        assertThat(accountsB.size()).isEqualTo(13);
        assertThat(accountsA.get(0).path("accountCode").asText()).isEqualTo("1000");
        assertThat(accountsA.get(13).path("accountCode").asText()).isEqualTo("9900");
        assertThat(accountsA.toString()).contains("Tenant A private account");
        assertThat(accountsB.toString()).doesNotContain("Tenant A private account");
    }

    @Test
    void requiresAuthentication() throws Exception {
        getJson(CHART_ENDPOINT, null).andExpect(status().isUnauthorized());
    }
}
