package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.driver.infra.DriverLedgerEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class ShiftCorrectionIntegrationTest extends AbstractShiftIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DriverLedgerEntryRepository ledger;
    @Autowired private ChartAccountRepository accounts;
    @Autowired private DefaultChartOfAccountsService defaultAccounts;
    @Autowired private JournalPostingService journals;

    private String token;
    private long tenantId;
    private long actorUserId;
    private long siteId;
    private long driverId;
    private long equipmentId;
    private long typeId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode registered = registerTenant("Correction Co", uniqueName("correction"));
        token = registered.path("accessToken").asText();
        tenantId = registered.path("user").path("tenantId").asLong();
        actorUserId = registered.path("user").path("id").asLong();
        siteId = createSite(token, "Correction Site");
        driverId = createDriver(token, "Correction Driver", "150", "175", "200");
        typeId = createType(token, "Correction Excavator");
        equipmentId = createEquipment(token, "COR-01", typeId, "500", "1200");
    }

    @Test
    void adminCorrectionReversesOriginalAndPostsReplacementExactlyOnce() throws Exception {
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        JsonNode completed = finishShift(token, started);
        long shiftId = completed.path("id").asLong();
        long originalJournalId = jdbc.queryForObject(
                "SELECT driver_wage_journal_entry_id FROM shifts WHERE id = ?", Long.class, shiftId);

        Map<String, Object> request = correctionBody(completed, "corrected-" + UUID.randomUUID());

        JsonNode corrected = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.driverWage").value(180.00)));

        assertThat(corrected.path("driverId").asLong()).isEqualTo(driverId);
        Long replacementJournalId = jdbc.queryForObject(
                "SELECT driver_wage_journal_entry_id FROM shifts WHERE id = ?", Long.class, shiftId);
        assertThat(replacementJournalId).isNotEqualTo(originalJournalId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND reversal_of_entry_id=?",
                Integer.class, tenantId, originalJournalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type='SHIFT_WAGE_CORRECTION'",
                Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=? AND status='APPLIED'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("180.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND transaction_type='CORRECTION_REVERSAL' AND amount=180.00 "
                        + "AND balance_effect=-180.00",
                Integer.class, tenantId, shiftId)).isEqualTo(1);

        // A network retry with the same key and payload must replay the completed correction.
        JsonNode replay = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk()));
        assertThat(replay.path("driverWage").decimalValue()).isEqualByComparingTo("180.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type='SHIFT_WAGE_CORRECTION'",
                Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
    }

    @Test
    void correctionRejectsZeroWageAtRequestBoundary() throws Exception {
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        JsonNode completed = finishShift(token, started);
        Map<String, Object> request = correctionBody(completed, "zero-" + UUID.randomUUID());
        request.put("driverWage", BigDecimal.ZERO);

        postJson("/api/v1/shifts/" + completed.path("id").asLong() + "/corrections", request, token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=?",
                Integer.class, tenantId)).isZero();
    }


    @Test
    void onlyAdminOfOwningTenantCanCorrectCompletedShift() throws Exception {
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        JsonNode completed = finishShift(token, started);
        long shiftId = completed.path("id").asLong();
        Map<String, Object> request = correctionBody(completed, "auth-" + UUID.randomUUID());

        String operatorName = uniqueName("correctionoperator");
        postJson("/api/v1/users", userBody(operatorName), token).andExpect(status().isCreated());
        String operatorToken = login(operatorName);
        postJson("/api/v1/shifts/" + shiftId + "/corrections", request, operatorToken)
                .andExpect(status().isForbidden());

        String viewerName = uniqueName("correctionviewer");
        Map<String, Object> viewerRequest = userBody(viewerName);
        viewerRequest.put("role", "VIEWER");
        postJson("/api/v1/users", viewerRequest, token).andExpect(status().isCreated());
        String viewerToken = login(viewerName);
        postJson("/api/v1/shifts/" + shiftId + "/corrections", request, viewerToken)
                .andExpect(status().isForbidden());

        String otherTenantToken = registerTenant("Other Correction Co", uniqueName("othercorrection"))
                .path("accessToken").asText();
        postJson("/api/v1/shifts/" + shiftId + "/corrections", request, otherTenantToken)
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                Integer.class, tenantId, shiftId)).isZero();
    }



    @Test
    void concurrentRetriesWithSameCorrectionKeyApplyExactlyOnce() throws Exception {
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        Map<String, Object> request = correctionBody(completed, "concurrent-correction-" + UUID.randomUUID());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentCorrection(shiftId, request, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentCorrection(shiftId, request, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.Arrays.asList(
                    first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 200);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=? AND status='APPLIED'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND transaction_type='CORRECTION_REVERSAL'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? "
                        + "AND source_type='SHIFT_WAGE_CORRECTION'",
                Integer.class, tenantId)).isEqualTo(1);
    }

    private int concurrentCorrection(long shiftId, Map<String, Object> request,
                                     CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent corrections.");
        }
        return postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andReturn().getResponse().getStatus();
    }

    @Test
    void correctionIsBlockedWhenTheShiftHasAnAdvanceRecovery() throws Exception {
        long cashId = seedCashBalanceAndGetCashId();
        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "correction-recovery-" + UUID.randomUUID());
        advance.put("advanceDate", today().minusDays(2).toString());
        advance.put("amount", new BigDecimal("100.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "سلفة قابلة للاسترداد قبل الوردية");
        advance.put("advanceType", "RECOVERABLE");
        postJson("/api/v1/drivers/" + driverId + "/ledger/advances", advance, token)
                .andExpect(status().isCreated());

        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND transaction_type='ADVANCE_RECOVERY'",
                Integer.class, tenantId, completed.path("id").asLong())).isEqualTo(1);

        Map<String, Object> request = correctionBody(completed, "recovery-correction-" + UUID.randomUUID());
        postJson("/api/v1/shifts/" + completed.path("id").asLong() + "/corrections", request, token)
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                Integer.class, tenantId, completed.path("id").asLong())).isZero();
    }

    @Test
    void correctionIsBlockedWhenDriverWasPaidAfterTheShift() throws Exception {
        long cashId = seedCashBalanceAndGetCashId();
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "correction-payment-" + UUID.randomUUID());
        payment.put("paymentDate", today().toString());
        payment.put("amount", new BigDecimal("10.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "صرف لاحق لاختبار منع التصحيح");
        postJson("/api/v1/drivers/" + driverId + "/ledger/payments", payment, token)
                .andExpect(status().isCreated());

        Map<String, Object> request = correctionBody(completed, "payment-correction-" + UUID.randomUUID());
        postJson("/api/v1/shifts/" + completed.path("id").asLong() + "/corrections", request, token)
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                Integer.class, tenantId, completed.path("id").asLong())).isZero();
    }


    @Test
    void driverLedgerDatabaseConstraintRejectsUnknownTransactionTypes() throws Exception {
        seedCashBalanceAndGetCashId();
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        Long openingJournalId = jdbc.queryForObject(
                "SELECT id FROM journal_entries WHERE tenant_id=? AND source_type='TEST_CASH_OPENING' "
                        + "ORDER BY id DESC LIMIT 1",
                Long.class, tenantId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO driver_ledger_entries (tenant_id, driver_id, transaction_type, transaction_date, "
                        + "amount, balance_effect, description, client_request_key, shift_id, journal_entry_id, "
                        + "created_by_user_id, created_at) VALUES (?, ?, 'NOT_ALLOWED', ?, 1.00, 1.00, "
                        + "'invalid transaction type test', ?, ?, ?, ?, UTC_TIMESTAMP(6))",
                tenantId, driverId, today(), "invalid-ledger-type-" + UUID.randomUUID(),
                started.path("id").asLong(), openingJournalId, actorUserId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long seedCashBalanceAndGetCashId() {
        defaultAccounts.initializeForTenant(tenantId);
        ChartAccount cash = accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Default cash account missing"));
        ChartAccount capital = accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "3000")
                .orElseThrow(() -> new IllegalStateException("Default capital account missing"));
        journals.post(tenantId, actorUserId, new JournalPostingRequest(
                "correction-test-opening-" + UUID.randomUUID(), "TEST_CASH_OPENING", null,
                today(), "Correction test opening cash", null, java.util.Arrays.asList(
                    new JournalLineRequest(cash.getId(), "Opening cash", new BigDecimal("1000.00"),
                            BigDecimal.ZERO, null, null),
                    new JournalLineRequest(capital.getId(), "Opening capital", BigDecimal.ZERO,
                            new BigDecimal("1000.00"), null, null))));
        return cash.getId();
    }

    private String login(String username) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", PASSWORD);
        return body(postJson("/api/v1/auth/login", request, null)
                .andExpect(status().isOk())).path("accessToken").asText();
    }

    private Map<String, Object> correctionBody(JsonNode shift, String key) {
        Map<String, Object> request = new HashMap<>();
        request.put("idempotencyKey", key);
        request.put("reason", "تصحيح بيانات الوردية بعد مراجعة المستندات");
        request.put("expectedVersion", shift.path("version").asLong());
        request.put("shiftDate", LocalDate.parse(shift.path("shiftDate").asText()).minusDays(1));
        request.put("shiftType", "EVENING");
        request.put("siteId", siteId);
        request.put("equipmentId", equipmentId);
        request.put("driverWage", new BigDecimal("180.00"));
        request.put("price", new BigDecimal("1300.00"));
        request.put("equipmentOperatingCost", new BigDecimal("520.00"));
        request.put("notes", "تم التصحيح بعد مراجعة المستندات");
        return request;
    }
}
