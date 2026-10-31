package com.contractor.app.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TrialBalanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private DefaultChartOfAccountsService defaultAccounts;
    @Autowired private ChartAccountRepository accounts;
    @Autowired private JournalPostingService journalPostingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long tenantId;
    private Long actorUserId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode registered = registerTenant("Trial Balance " + uniqueName("tenant"), uniqueName("owner"));
        tenantId = registered.path("user").path("tenantId").asLong();
        actorUserId = registered.path("user").path("id").asLong();
        token = registered.path("accessToken").asText();
        defaultAccounts.initializeForTenant(tenantId);
    }

    @Test
    void calculatesOpeningPeriodAndClosingBalancesAndExcludesEntriesAfterEndDate() throws Exception {
        postJournal("tb-opening", LocalDate.of(2026, 1, 31), "5000", "2100", "120.00");
        postJournal("tb-period-expense", LocalDate.of(2026, 2, 10), "5100", "2000", "75.00");
        postJournal("tb-period-recovery", LocalDate.of(2026, 2, 12), "2100", "5000", "20.00");
        postJournal("tb-after-period", LocalDate.of(2026, 3, 1), "5200", "2000", "40.00");

        JsonNode report = body(getJson(reportPath("2026-02-01", "2026-02-28"), token)
                .andExpect(status().isOk()));

        assertThat(report.path("rows")).hasSize(15);
        JsonNode wages = row(report, "5000");
        assertMoney(wages, "openingDebit", "120.00");
        assertMoney(wages, "periodCredit", "20.00");
        assertMoney(wages, "closingDebit", "100.00");
        assertMoney(wages, "closingCredit", "0.00");

        JsonNode driverPayable = row(report, "2100");
        assertMoney(driverPayable, "openingCredit", "120.00");
        assertMoney(driverPayable, "periodDebit", "20.00");
        assertMoney(driverPayable, "closingCredit", "100.00");

        JsonNode equipmentExpenses = row(report, "5100");
        assertMoney(equipmentExpenses, "periodDebit", "75.00");
        assertMoney(equipmentExpenses, "closingDebit", "75.00");

        JsonNode supplierPayable = row(report, "2000");
        assertMoney(supplierPayable, "periodCredit", "75.00");
        assertMoney(supplierPayable, "closingCredit", "75.00");

        assertThat(report.path("balanced").asBoolean()).isTrue();
        JsonNode totals = report.path("totals");
        assertMoney(totals, "openingDebit", "120.00");
        assertMoney(totals, "openingCredit", "120.00");
        assertMoney(totals, "periodDebit", "95.00");
        assertMoney(totals, "periodCredit", "95.00");
        assertMoney(totals, "closingDebit", "175.00");
        assertMoney(totals, "closingCredit", "175.00");
        assertThat(report.path("fromDate").asText()).isEqualTo("2026-02-01");
        assertThat(report.path("toDate").asText()).isEqualTo("2026-02-28");
    }

    @Test
    void includesZeroBalanceAccountsForHistoricalVisibility() throws Exception {
        JsonNode report = body(getJson(reportPath("2026-02-01", "2026-02-28"), token)
                .andExpect(status().isOk()));

        assertThat(report.path("rows")).hasSize(15);
        JsonNode inventory = row(report, "1400");
        assertThat(inventory.path("accountType").asText()).isEqualTo("ASSET");
        assertMoney(inventory, "openingDebit", "0.00");
        assertMoney(inventory, "periodDebit", "0.00");
        assertMoney(inventory, "closingDebit", "0.00");
    }

    @Test
    void reportIsIsolatedBetweenTenants() throws Exception {
        postJournal("tb-private-entry", LocalDate.of(2026, 2, 10), "5000", "2100", "300.00");
        JsonNode otherTenant = registerTenant("Other Trial Balance", uniqueName("otherowner"));
        String otherToken = otherTenant.path("accessToken").asText();

        JsonNode otherReport = body(getJson(reportPath("2026-02-01", "2026-02-28"), otherToken)
                .andExpect(status().isOk()));

        assertThat(otherReport.path("rows")).hasSize(15);
        assertMoney(row(otherReport, "5000"), "periodDebit", "0.00");
        assertMoney(row(otherReport, "2100"), "periodCredit", "0.00");
        assertMoney(otherReport.path("totals"), "periodDebit", "0.00");
        assertMoney(otherReport.path("totals"), "periodCredit", "0.00");
    }

    @Test
    void includesTransactionsOnBothPeriodBoundaries() throws Exception {
        postJournal("tb-start-boundary", LocalDate.of(2026, 2, 1), "5200", "2000", "10.00");
        postJournal("tb-end-boundary", LocalDate.of(2026, 2, 28), "5200", "2000", "15.00");

        JsonNode report = body(getJson(reportPath("2026-02-01", "2026-02-28"), token)
                .andExpect(status().isOk()));

        assertMoney(row(report, "5200"), "periodDebit", "25.00");
        assertMoney(row(report, "2000"), "periodCredit", "25.00");
        assertMoney(report.path("totals"), "periodDebit", "25.00");
        assertMoney(report.path("totals"), "periodCredit", "25.00");
    }

    @Test
    void rejectsDateRangeWhenStartIsAfterEnd() throws Exception {
        getJson(reportPath("2026-03-01", "2026-02-01"), token)
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewerCanReadTrialBalanceButUnauthenticatedRequestsAreRejected() throws Exception {
        String viewerName = uniqueName("trialviewer");
        java.util.Map<String, Object> viewerRequest = new java.util.HashMap<>(userBody(viewerName));
        viewerRequest.put("role", "VIEWER");
        body(postJson("/api/v1/users", viewerRequest, token).andExpect(status().isCreated()));
        String viewerToken = login(viewerName);

        getJson(reportPath("2026-02-01", "2026-02-28"), viewerToken)
                .andExpect(status().isOk());
        getJson(reportPath("2026-02-01", "2026-02-28"), null)
                .andExpect(status().isUnauthorized());
    }

    private void postJournal(String key, LocalDate date, String debitCode,
                             String creditCode, String amount) {
        BigDecimal value = new BigDecimal(amount);
        JournalPostingRequest request = new JournalPostingRequest(key, "TRIAL_BALANCE_TEST",
                null, date, "Trial balance integration fixture", null,
                Arrays.asList(
                        new JournalLineRequest(accountId(debitCode), "Debit fixture", value,
                                BigDecimal.ZERO, null, null),
                        new JournalLineRequest(accountId(creditCode), "Credit fixture",
                                BigDecimal.ZERO, value, null, null)));
        journalPostingService.post(tenantId, actorUserId, request);
    }

    private Long accountId(String code) {
        return accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, code)
                .orElseThrow(() -> new AssertionError("Missing test chart account " + code))
                .getId();
    }

    private String reportPath(String from, String to) {
        return "/api/v1/accounting/reports/trial-balance?from=" + from + "&to=" + to;
    }

    private JsonNode row(JsonNode report, String code) {
        for (JsonNode row : report.path("rows")) {
            if (code.equals(row.path("accountCode").asText())) {
                return row;
            }
        }
        throw new AssertionError("Missing trial-balance row for account " + code);
    }

    private void assertMoney(JsonNode node, String field, String expected) {
        assertThat(node.path(field).decimalValue()).isEqualByComparingTo(expected);
    }

    private String login(String username) throws Exception {
        return body(postJson("/api/v1/auth/login",
                java.util.Map.of("username", username, "password", PASSWORD), null)
                .andExpect(status().isOk())).path("accessToken").asText();
    }
}
