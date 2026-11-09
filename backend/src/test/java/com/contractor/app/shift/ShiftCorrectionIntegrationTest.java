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
import com.contractor.app.shift.domain.Shift;
import com.contractor.app.shift.domain.ShiftCorrection;
import com.contractor.app.shift.domain.ShiftType;
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
import org.springframework.jdbc.UncategorizedSQLException;
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
        Long workforceMemberId = jdbc.queryForObject(
                "SELECT workforce_member_id FROM shifts WHERE tenant_id=? AND id=?",
                Long.class, tenantId, shiftId);
        Long reversalJournalId = jdbc.queryForObject(
                "SELECT wage_reversal_journal_entry_id FROM shift_corrections "
                        + "WHERE tenant_id=? AND shift_id=?",
                Long.class, tenantId, shiftId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_lines WHERE tenant_id=? "
                        + "AND journal_entry_id IN (?, ?, ?) AND workforce_member_id=?",
                Integer.class, tenantId, originalJournalId, reversalJournalId,
                replacementJournalId, workforceMemberId)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_lines WHERE tenant_id=? "
                        + "AND journal_entry_id IN (?, ?, ?) AND driver_id=?",
                Integer.class, tenantId, originalJournalId, reversalJournalId,
                replacementJournalId, driverId)).isEqualTo(6);
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
                        + "AND transaction_type='CORRECTION_REVERSAL' AND amount=150.00 "
                        + "AND balance_effect=-150.00",
                Integer.class, tenantId, shiftId)).isEqualTo(1);

        // The transactional V56 mirror must preserve the complete correction chain
        // without reusing legacy IDs as Workforce primary keys.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries w "
                        + "JOIN driver_ledger_entries d ON d.id=w.legacy_source_entry_id "
                        + "AND d.tenant_id=w.tenant_id "
                        + "WHERE w.tenant_id=? AND w.workforce_member_id=? AND w.shift_id=? "
                        + "AND w.transaction_type IN ('EARNING','CORRECTION_REVERSAL') "
                        + "AND w.legacy_driver_id=? AND w.journal_entry_id=d.journal_entry_id",
                Integer.class, tenantId, workforceMemberId, shiftId, driverId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries w "
                        + "JOIN driver_ledger_entries d ON d.id=w.legacy_source_entry_id "
                        + "AND d.tenant_id=w.tenant_id "
                        + "WHERE w.tenant_id=? AND w.workforce_member_id=? AND w.shift_id=? "
                        + "AND w.transaction_type='EARNING' AND w.amount=180.00 "
                        + "AND w.balance_effect=180.00 AND w.journal_entry_id=?",
                Integer.class, tenantId, workforceMemberId, shiftId, replacementJournalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? "
                        + "AND workforce_member_id=? AND shift_id=?",
                Integer.class, tenantId, workforceMemberId, shiftId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, workforceMemberId)).isEqualByComparingTo("180.00");

        // A network retry with the same key and payload must replay the completed correction.
        // Numeric scale is not semantically relevant to an idempotent SHIFT wage replay (180.0 == 180.00).
        request.put("driverWage", new BigDecimal("180.0"));
        JsonNode replay = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk()));
        assertThat(replay.path("driverWage").decimalValue()).isEqualByComparingTo("180.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type='SHIFT_WAGE_CORRECTION'",
                Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                Integer.class, tenantId, shiftId)).isEqualTo(1);

        // A second, distinct correction must reverse the first replacement, not the original wage.
        Map<String, Object> secondRequest = correctionBody(corrected, "corrected-again-" + UUID.randomUUID());
        secondRequest.put("driverWage", new BigDecimal("210.00"));
        secondRequest.put("price", new BigDecimal("1400.00"));
        JsonNode correctedAgain = body(postJson("/api/v1/shifts/" + shiftId + "/corrections",
                secondRequest, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.driverWage").value(210.00)));
        Long secondReplacementJournalId = jdbc.queryForObject(
                "SELECT driver_wage_journal_entry_id FROM shifts WHERE id = ?", Long.class, shiftId);
        assertThat(secondReplacementJournalId).isNotEqualTo(replacementJournalId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND reversal_of_entry_id=?",
                Integer.class, tenantId, replacementJournalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type='SHIFT_WAGE_CORRECTION'",
                Integer.class, tenantId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=? AND status='APPLIED'",
                Integer.class, tenantId, shiftId)).isEqualTo(2);
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("210.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND transaction_type='CORRECTION_REVERSAL'",
                Integer.class, tenantId, shiftId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? "
                        + "AND workforce_member_id=? AND shift_id=? "
                        + "AND transaction_type IN ('EARNING','CORRECTION_REVERSAL')",
                Integer.class, tenantId, workforceMemberId, shiftId)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? "
                        + "AND workforce_member_id=? AND shift_id=? "
                        + "AND transaction_type='CORRECTION_REVERSAL' AND balance_effect=-180.00",
                Integer.class, tenantId, workforceMemberId, shiftId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, workforceMemberId)).isEqualByComparingTo("210.00");
        assertThat(correctedAgain.path("driverId").asLong()).isEqualTo(driverId);

        Long appliedCorrectionId = jdbc.queryForObject(
                "SELECT id FROM shift_corrections WHERE tenant_id=? AND shift_id=? "
                        + "ORDER BY id ASC LIMIT 1", Long.class, tenantId, shiftId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE shift_corrections SET reason='tampered audit reason' WHERE id=? AND tenant_id=?",
                appliedCorrectionId, tenantId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("SHIFT_CORRECTION_AUDIT_IMMUTABLE");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM shift_corrections WHERE id=? AND tenant_id=?", appliedCorrectionId, tenantId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("SHIFT_CORRECTION_AUDIT_IMMUTABLE");
    }

    @Test
    void databaseRejectsForgedCorrectionLedgerRowsWithoutApplyingCorrection() throws Exception {
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        Long originalJournalId = jdbc.queryForObject(
                "SELECT driver_wage_journal_entry_id FROM shifts WHERE tenant_id=? AND id=?",
                Long.class, tenantId, shiftId);
        LocalDate shiftDate = LocalDate.parse(completed.path("shiftDate").asText());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO driver_ledger_entries (tenant_id, driver_id, transaction_type, transaction_date, "
                        + "amount, balance_effect, description, client_request_key, shift_id, journal_entry_id, "
                        + "created_by_user_id, created_at) VALUES (?, ?, 'EARNING', ?, 1.00, 1.00, "
                        + "'forged correction earning', ?, ?, ?, ?, UTC_TIMESTAMP(6))",
                tenantId, driverId, shiftDate, "shift-wage-correction-forged-" + UUID.randomUUID(),
                shiftId, originalJournalId, actorUserId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("UNAUTHORIZED_SHIFT_CORRECTION_LEDGER_EARNING");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO driver_ledger_entries (tenant_id, driver_id, transaction_type, transaction_date, "
                        + "amount, balance_effect, description, client_request_key, shift_id, journal_entry_id, "
                        + "created_by_user_id, created_at) VALUES (?, ?, 'CORRECTION_REVERSAL', ?, 1.00, -1.00, "
                        + "'forged correction reversal', ?, ?, ?, ?, UTC_TIMESTAMP(6))",
                tenantId, driverId, shiftDate, "shift-wage-correction-reversal-forged-" + UUID.randomUUID(),
                shiftId, originalJournalId, actorUserId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("UNAUTHORIZED_SHIFT_CORRECTION_LEDGER_REVERSAL");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND client_request_key LIKE 'shift-wage-correction-%'",
                Integer.class, tenantId, shiftId)).isZero();
    }

    @Test
    void correctionSupportsZeroWageWithoutZeroValueFinancialEntries() throws Exception {
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        Long originalJournalId = completed.path("driverWageJournalEntryId").asLong();
        Map<String, Object> toZero = correctionBody(completed, "zero-" + UUID.randomUUID());
        toZero.put("driverWage", BigDecimal.ZERO);

        JsonNode zeroed = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", toZero, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverWage").value(0.00)));
        assertThat(zeroed.path("driverWageJournalEntryId").isNull()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT driver_wage_journal_entry_id FROM shifts WHERE tenant_id=? AND id=?",
                Long.class, tenantId, shiftId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND reversal_of_entry_id=?",
                Integer.class, tenantId, originalJournalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type='SHIFT_WAGE_CORRECTION'",
                Integer.class, tenantId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND transaction_type='CORRECTION_REVERSAL'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("0.00");

        // A later correction can restore a positive wage from the valid zero-wage snapshot.
        Map<String, Object> backToPositive = correctionBody(zeroed, "positive-" + UUID.randomUUID());
        backToPositive.put("driverWage", new BigDecimal("190.00"));
        JsonNode restored = body(postJson("/api/v1/shifts/" + shiftId + "/corrections",
                backToPositive, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.driverWage").value(190.00)));
        assertThat(restored.path("driverWageJournalEntryId").asLong()).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type='SHIFT_WAGE_CORRECTION'",
                Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND transaction_type='EARNING' AND amount=190.00 AND balance_effect=190.00",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("190.00");
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
    void correctionIsBlockedWhenDriverTookAnAdvanceAgainstEarnedWagesAfterTheShift() throws Exception {
        long cashId = seedCashBalanceAndGetCashId();
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));

        String advanceKey = "correction-earned-advance-" + UUID.randomUUID();
        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", advanceKey);
        advance.put("advanceDate", today().toString());
        advance.put("amount", new BigDecimal("10.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "سلفة من مستحقات السائق بعد الوردية");
        advance.put("advanceType", "AGAINST_EARNED");
        postJson("/api/v1/drivers/" + driverId + "/ledger/advances", advance, token)
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='ADVANCE' AND client_request_key=?",
                Integer.class, tenantId, driverId, advanceKey)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT balance_effect FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='ADVANCE' AND client_request_key=?",
                BigDecimal.class, tenantId, driverId, advanceKey)).isEqualByComparingTo("-10.00");

        Map<String, Object> request = correctionBody(completed, "earned-advance-correction-" + UUID.randomUUID());
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
    void completedShiftTenantOwnershipCannotBeChangedDirectly() throws Exception {
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();

        // The existing correction-aware journal guard must reject tenant reassignment.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE shifts SET tenant_id=? WHERE id=? AND tenant_id=?",
                tenantId + 9999, shiftId, tenantId))
                .isInstanceOf(UncategorizedSQLException.class);

        assertThat(jdbc.queryForObject(
                "SELECT tenant_id FROM shifts WHERE id=?", Long.class, shiftId))
                .isEqualTo(tenantId);
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
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("chk_driver_ledger_type");
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


    @Test
    void correctionRollsBackAllFinancialAndOperationalWritesWhenLedgerPostingFails() throws Exception {
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        Long originalJournalId = completed.path("driverWageJournalEntryId").asLong();
        BigDecimal originalBalance = ledger.sumBalance(tenantId, driverId);
        String trigger = "trg_test_fail_shift_correction_ledger";

        jdbc.execute("DROP TRIGGER IF EXISTS " + trigger);
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON driver_ledger_entries "
                + "FOR EACH ROW BEGIN "
                + "IF NEW.transaction_type = 'CORRECTION_REVERSAL' THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TEST_FORCED_CORRECTION_FAILURE'; "
                + "END IF; END");

        try {
            Map<String, Object> request = correctionBody(completed, "rollback-" + UUID.randomUUID());
            postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                    .andExpect(status().is5xxServerError());

            assertThat(jdbc.queryForObject(
                    "SELECT driver_wage_journal_entry_id FROM shifts WHERE tenant_id=? AND id=?",
                    Long.class, tenantId, shiftId)).isEqualTo(originalJournalId);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                    Integer.class, tenantId, shiftId)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type IN "
                            + "('SHIFT_WAGE_CORRECTION_REVERSAL','SHIFT_WAGE_CORRECTION')",
                    Integer.class, tenantId)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                            + "AND transaction_type='CORRECTION_REVERSAL'",
                    Integer.class, tenantId, shiftId)).isZero();
            assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo(originalBalance);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger);
        }
    }

    @Test
    void reusingCorrectionKeyWithDifferentPayloadReturnsConflict() throws Exception {
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        Map<String, Object> request = correctionBody(completed, "payload-" + UUID.randomUUID());

        postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk());

        Map<String, Object> changedPayload = new HashMap<>(request);
        changedPayload.put("driverWage", new BigDecimal("999.00"));
        postJson("/api/v1/shifts/" + shiftId + "/corrections", changedPayload, token)
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=? AND status='APPLIED'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type='SHIFT_WAGE_CORRECTION'",
                Integer.class, tenantId)).isEqualTo(1);
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("180.00");
    }


    @Test
    void compensationCorrectionSnapshotKeepsAgreementRateFrozenButUpdatesLegacyRate() {
        LocalDate shiftDate = LocalDate.now().minusDays(1);

        Shift agreementShift = new Shift(1L, shiftDate, ShiftType.MORNING, 10L, 20L, 30L);
        agreementShift.setPrice(new BigDecimal("500.00"));
        agreementShift.setEquipmentOperatingCost(new BigDecimal("25.00"));
        agreementShift.applyCompensationSnapshot("AGREEMENT", 40L, 50L,
                "AGR-40", "STANDARD_SHIFT", "Standard shift",
                "SHIFT", "EGP", "SHIFT", new BigDecimal("150.00"), new BigDecimal("150.00"));

        ShiftCorrection agreementCorrection = new ShiftCorrection(1L, 60L, "agreement-snapshot",
                "approved wage override", 70L, 0L, agreementShift,
                shiftDate, ShiftType.MORNING, 10L, 20L, 30L,
                new BigDecimal("180.00"), new BigDecimal("500.00"),
                new BigDecimal("25.00"), null);

        assertThat(agreementCorrection.getOldCompensationRate()).isEqualByComparingTo("150.00");
        assertThat(agreementCorrection.getNewCompensationRate()).isEqualByComparingTo("150.00");
        assertThat(agreementCorrection.getOldCompensationCalculatedAmount()).isEqualByComparingTo("150.00");
        assertThat(agreementCorrection.getNewCompensationCalculatedAmount()).isEqualByComparingTo("180.00");

        Shift legacyShift = new Shift(1L, shiftDate, ShiftType.MORNING, 10L, 20L, 30L);
        legacyShift.setPrice(new BigDecimal("500.00"));
        legacyShift.setEquipmentOperatingCost(new BigDecimal("25.00"));
        legacyShift.applyCompensationSnapshot("LEGACY_DRIVER_RATE", null, null,
                null, "LEGACY_DRIVER_RATE", "Legacy driver rate",
                "SHIFT", null, null, new BigDecimal("150.00"), new BigDecimal("150.00"));

        ShiftCorrection legacyCorrection = new ShiftCorrection(1L, 61L, "legacy-snapshot",
                "approved wage override", 70L, 0L, legacyShift,
                shiftDate, ShiftType.MORNING, 10L, 20L, 30L,
                new BigDecimal("180.00"), new BigDecimal("500.00"),
                new BigDecimal("25.00"), null);

        assertThat(legacyCorrection.getOldCompensationRate()).isEqualByComparingTo("150.00");
        assertThat(legacyCorrection.getNewCompensationRate()).isEqualByComparingTo("180.00");
        assertThat(legacyCorrection.getNewCompensationCalculatedAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void databaseRejectsMalformedAndMeasuredBasisCorrectionSnapshots() throws Exception {
        JsonNode shiftResponse = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = shiftResponse.path("id").asLong();

        // A SHIFT snapshot with a forged new rate must fail even when inserted outside the service.
        assertThatThrownBy(() -> insertCorrectionSnapshotDirectly(
                tenantId, shiftId, "forged-shift-snapshot-" + UUID.randomUUID(), new BigDecimal("999.00")))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("SHIFT_CORRECTION_COMPENSATION_SNAPSHOT_MISMATCH");

        // A snapshot copied from a real shift must still be rejected if its tenant_id
        // is changed to another tenant. This exercises the tenant predicate in the DB guard.
        JsonNode otherTenant = registerTenant("Other Correction Co", uniqueName("other-correction"));
        long otherTenantId = otherTenant.path("user").path("tenantId").asLong();
        assertThat(otherTenantId).isNotEqualTo(tenantId);
        assertThatThrownBy(() -> insertCorrectionSnapshotDirectly(
                otherTenantId, shiftId, "cross-tenant-snapshot-" + UUID.randomUUID(),
                new BigDecimal("150.00")))
                .isInstanceOf(UncategorizedSQLException.class);

        // Build otherwise matching in-progress HOUR and QUANTITY snapshots. The DB guard
        // must reject the compensation basis itself until the WF-08B workflow exists.
        for (String basis : new String[] {"HOUR", "QUANTITY"}) {
            jdbc.update("UPDATE shifts SET compensation_basis=?, compensation_unit_code=?, "
                            + "compensation_rate=10.00, compensation_calculated_amount=0.00, "
                            + "actual_hours=NULL, actual_quantity=NULL WHERE tenant_id=? AND id=?",
                    basis, basis.equals("HOUR") ? "HOUR" : "UNIT", tenantId, shiftId);

            BigDecimal expectedNewRate = jdbc.queryForObject(
                    "SELECT driver_wage FROM shifts WHERE tenant_id=? AND id=?",
                    BigDecimal.class, tenantId, shiftId);
            assertThatThrownBy(() -> insertCorrectionSnapshotDirectly(
                    tenantId, shiftId, "forged-" + basis.toLowerCase() + "-" + UUID.randomUUID(), expectedNewRate))
                    .isInstanceOf(UncategorizedSQLException.class)
                    .hasMessageContaining("SHIFT_CORRECTION_COMPENSATION_SNAPSHOT_MISMATCH");
        }
    }

    @Test
    void hourlyCorrectionRecalculatesFromFrozenRateAndReversesFinancialEntries() throws Exception {
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = started.path("id").asLong();
        jdbc.update("UPDATE shifts SET compensation_basis='HOUR', compensation_unit_code='HOUR', "
                + "compensation_rate=10.00, compensation_calculated_amount=0.00 "
                + "WHERE tenant_id=? AND id=?", tenantId, shiftId);

        Map<String, Object> finish = versionBody(started.path("version").asLong());
        finish.put("actualHours", new BigDecimal("4.00"));
        JsonNode completed = body(postJson("/api/v1/shifts/" + shiftId + "/finish", finish, token)
                .andExpect(status().isOk()));
        assertThat(completed.path("driverWage").decimalValue()).isEqualByComparingTo("40.00");

        Map<String, Object> request = correctionBody(completed, "hour-correction-" + UUID.randomUUID());
        request.remove("driverWage");
        request.put("actualHours", new BigDecimal("5.50"));
        JsonNode corrected = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk()));
        assertThat(corrected.path("driverWage").decimalValue()).isEqualByComparingTo("55.00");

        Map<String, Object> snapshot = jdbc.queryForMap(
                "SELECT old_actual_hours, new_actual_hours, old_compensation_rate, new_compensation_rate, "
                        + "old_compensation_calculated_amount, new_compensation_calculated_amount "
                        + "FROM shift_corrections WHERE tenant_id=? AND shift_id=?", tenantId, shiftId);
        assertThat(snapshot.get("old_actual_hours")).isEqualTo(new BigDecimal("4.00"));
        assertThat(snapshot.get("new_actual_hours")).isEqualTo(new BigDecimal("5.50"));
        assertThat(snapshot.get("old_compensation_rate")).isEqualTo(new BigDecimal("10.00"));
        assertThat(snapshot.get("new_compensation_rate")).isEqualTo(new BigDecimal("10.00"));
        assertThat(snapshot.get("old_compensation_calculated_amount")).isEqualTo(new BigDecimal("40.00"));
        assertThat(snapshot.get("new_compensation_calculated_amount")).isEqualTo(new BigDecimal("55.00"));
        assertThat(jdbc.queryForObject(
                "SELECT compensation_rate FROM shifts WHERE tenant_id=? AND id=?",
                BigDecimal.class, tenantId, shiftId)).isEqualByComparingTo("10.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND reversal_of_entry_id IS NOT NULL",
                Integer.class, tenantId)).isEqualTo(1);
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("55.00");

        JsonNode replay = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk()));
        assertThat(replay.path("driverWage").decimalValue()).isEqualByComparingTo("55.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
    }

    @Test
    void hourlyCorrectionRejectsMissingOrConflictingMeasurementAndClientWage() throws Exception {
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = started.path("id").asLong();
        jdbc.update("UPDATE shifts SET compensation_basis='HOUR', compensation_unit_code='HOUR', "
                + "compensation_rate=10.00, compensation_calculated_amount=0.00 "
                + "WHERE tenant_id=? AND id=?", tenantId, shiftId);
        Map<String, Object> finish = versionBody(started.path("version").asLong());
        finish.put("actualHours", new BigDecimal("4.00"));
        JsonNode completed = body(postJson("/api/v1/shifts/" + shiftId + "/finish", finish, token)
                .andExpect(status().isOk()));

        Map<String, Object> missing = correctionBody(completed, "hour-missing-" + UUID.randomUUID());
        missing.remove("driverWage");
        postJson("/api/v1/shifts/" + shiftId + "/corrections", missing, token)
                .andExpect(status().isBadRequest());

        Map<String, Object> forgedWage = correctionBody(completed, "hour-forged-wage-" + UUID.randomUUID());
        forgedWage.remove("driverWage");
        forgedWage.put("actualHours", new BigDecimal("5.00"));
        forgedWage.put("driverWage", new BigDecimal("1.00"));
        postJson("/api/v1/shifts/" + shiftId + "/corrections", forgedWage, token)
                .andExpect(status().isBadRequest());

        Map<String, Object> conflictingMeasures = correctionBody(completed, "hour-conflict-" + UUID.randomUUID());
        conflictingMeasures.remove("driverWage");
        conflictingMeasures.put("actualHours", new BigDecimal("5.00"));
        conflictingMeasures.put("actualQuantity", new BigDecimal("2.00"));
        postJson("/api/v1/shifts/" + shiftId + "/corrections", conflictingMeasures, token)
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                Integer.class, tenantId, shiftId)).isZero();
    }

    @Test
    void quantityCorrectionRecalculatesFromFrozenUnitRate() throws Exception {
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = started.path("id").asLong();
        jdbc.update("UPDATE shifts SET compensation_basis='QUANTITY', compensation_unit_code='TON', "
                + "compensation_rate=3.25, compensation_calculated_amount=0.00 "
                + "WHERE tenant_id=? AND id=?", tenantId, shiftId);

        Map<String, Object> finish = versionBody(started.path("version").asLong());
        finish.put("actualQuantity", new BigDecimal("12.500"));
        JsonNode completed = body(postJson("/api/v1/shifts/" + shiftId + "/finish", finish, token)
                .andExpect(status().isOk()));
        assertThat(completed.path("driverWage").decimalValue()).isEqualByComparingTo("40.63");

        Map<String, Object> request = correctionBody(completed, "quantity-correction-" + UUID.randomUUID());
        request.remove("driverWage");
        request.put("actualQuantity", new BigDecimal("8.250"));
        JsonNode corrected = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk()));
        assertThat(corrected.path("driverWage").decimalValue()).isEqualByComparingTo("26.81");
        assertThat(jdbc.queryForObject(
                "SELECT actual_quantity FROM shifts WHERE tenant_id=? AND id=?",
                BigDecimal.class, tenantId, shiftId)).isEqualByComparingTo("8.250");
        assertThat(jdbc.queryForObject(
                "SELECT compensation_rate FROM shifts WHERE tenant_id=? AND id=?",
                BigDecimal.class, tenantId, shiftId)).isEqualByComparingTo("3.25");
        assertThat(ledger.sumBalance(tenantId, driverId)).isEqualByComparingTo("26.81");
    }

    @Test
    void zeroRateHourlyCorrectionCreatesNoZeroFinancialEntries() throws Exception {
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = started.path("id").asLong();
        jdbc.update("UPDATE shifts SET compensation_basis='HOUR', compensation_unit_code='HOUR', "
                + "compensation_rate=0.00, compensation_calculated_amount=0.00 "
                + "WHERE tenant_id=? AND id=?", tenantId, shiftId);

        Map<String, Object> finish = versionBody(started.path("version").asLong());
        finish.put("actualHours", new BigDecimal("3.00"));
        JsonNode completed = body(postJson("/api/v1/shifts/" + shiftId + "/finish", finish, token)
                .andExpect(status().isOk()));
        assertThat(completed.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");

        Map<String, Object> request = correctionBody(completed, "zero-hour-correction-" + UUID.randomUUID());
        request.remove("driverWage");
        request.put("actualHours", new BigDecimal("4.00"));
        JsonNode corrected = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk()));
        assertThat(corrected.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type IN "
                        + "('SHIFT_WAGE_CORRECTION','SHIFT_WAGE_CORRECTION_REVERSAL')",
                Integer.class, tenantId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND transaction_type IN ('EARNING','CORRECTION_REVERSAL')",
                Integer.class, tenantId, shiftId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT actual_hours FROM shifts WHERE tenant_id=? AND id=?",
                BigDecimal.class, tenantId, shiftId)).isEqualByComparingTo("4.00");
    }

    @Test
    void zeroRateQuantityCorrectionCreatesNoZeroFinancialEntries() throws Exception {
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = started.path("id").asLong();
        jdbc.update("UPDATE shifts SET compensation_basis='QUANTITY', compensation_unit_code='TON', "
                + "compensation_rate=0.00, compensation_calculated_amount=0.00 "
                + "WHERE tenant_id=? AND id=?", tenantId, shiftId);

        Map<String, Object> finish = versionBody(started.path("version").asLong());
        finish.put("actualQuantity", new BigDecimal("3.000"));
        JsonNode completed = body(postJson("/api/v1/shifts/" + shiftId + "/finish", finish, token)
                .andExpect(status().isOk()));
        assertThat(completed.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");

        Map<String, Object> request = correctionBody(completed, "zero-quantity-correction-" + UUID.randomUUID());
        request.remove("driverWage");
        request.put("actualQuantity", new BigDecimal("4.500"));
        JsonNode corrected = body(postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk()));
        assertThat(corrected.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id=? AND source_type IN "
                        + "('SHIFT_WAGE_CORRECTION','SHIFT_WAGE_CORRECTION_REVERSAL')",
                Integer.class, tenantId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND shift_id=? "
                        + "AND transaction_type IN ('EARNING','CORRECTION_REVERSAL')",
                Integer.class, tenantId, shiftId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT actual_quantity FROM shifts WHERE tenant_id=? AND id=?",
                BigDecimal.class, tenantId, shiftId)).isEqualByComparingTo("4.500");
        assertThat(jdbc.queryForObject(
                "SELECT compensation_rate FROM shifts WHERE tenant_id=? AND id=?",
                BigDecimal.class, tenantId, shiftId)).isEqualByComparingTo("0.00");
    }

    private void insertCorrectionSnapshotDirectly(long correctionTenantId, long shiftId,
                                                   String idempotencyKey, BigDecimal newCompensationRate) {
        jdbc.update(
                "INSERT INTO shift_corrections (tenant_id, shift_id, idempotency_key, reason, status, "
                        + "actor_user_id, expected_shift_version, old_shift_date, new_shift_date, "
                        + "old_shift_type, new_shift_type, old_site_id, new_site_id, old_driver_id, "
                        + "new_driver_id, old_equipment_id, new_equipment_id, old_driver_wage, "
                        + "new_driver_wage, old_price, new_price, old_equipment_operating_cost, "
                        + "new_equipment_operating_cost, old_compensation_basis, new_compensation_basis, "
                        + "old_compensation_rate, new_compensation_rate, old_compensation_unit_code, "
                        + "new_compensation_unit_code, old_compensation_currency_code, "
                        + "new_compensation_currency_code, old_compensation_calculated_amount, "
                        + "new_compensation_calculated_amount, old_actual_hours, new_actual_hours, "
                        + "old_actual_quantity, new_actual_quantity, created_at, updated_at) "
                        + "SELECT ?, id, ?, 'direct database guard test', 'APPLYING', ?, version, "
                        + "shift_date, shift_date, shift_type, shift_type, site_id, site_id, driver_id, "
                        + "driver_id, equipment_id, equipment_id, driver_wage, driver_wage, price, price, "
                        + "equipment_operating_cost, equipment_operating_cost, compensation_basis, "
                        + "compensation_basis, compensation_rate, ?, compensation_unit_code, "
                        + "compensation_unit_code, compensation_currency_code, compensation_currency_code, "
                        + "compensation_calculated_amount, driver_wage, actual_hours, actual_hours, "
                        + "actual_quantity, actual_quantity, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) "
                        + "FROM shifts WHERE tenant_id=? AND id=?",
                correctionTenantId, idempotencyKey, actorUserId, newCompensationRate, tenantId, shiftId);
    }

    @Test
    void correctionPersistsFrozenCompensationSnapshotAndDatabaseRejectsTampering() throws Exception {
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        Map<String, Object> request = correctionBody(completed, "snapshot-" + UUID.randomUUID());

        postJson("/api/v1/shifts/" + shiftId + "/corrections", request, token)
                .andExpect(status().isOk());

        Map<String, Object> snapshot = jdbc.queryForMap(
                "SELECT old_compensation_basis, new_compensation_basis, "
                        + "old_compensation_rate, new_compensation_rate, "
                        + "old_compensation_calculated_amount, new_compensation_calculated_amount, "
                        + "old_actual_hours, new_actual_hours, old_actual_quantity, new_actual_quantity "
                        + "FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                tenantId, shiftId);
        assertThat(snapshot.get("old_compensation_basis")).isEqualTo("SHIFT");
        assertThat(snapshot.get("new_compensation_basis")).isEqualTo("SHIFT");
        assertThat(snapshot.get("old_compensation_rate")).isEqualTo(new BigDecimal("150.00"));
        assertThat(snapshot.get("new_compensation_rate")).isEqualTo(new BigDecimal("180.00"));
        assertThat(snapshot.get("old_compensation_calculated_amount"))
                .isEqualTo(new BigDecimal("150.00"));
        assertThat(snapshot.get("new_compensation_calculated_amount"))
                .isEqualTo(new BigDecimal("180.00"));
        assertThat(jdbc.queryForObject(
                "SELECT compensation_calculated_amount FROM shifts WHERE tenant_id=? AND id=?",
                BigDecimal.class, tenantId, shiftId))
                .isEqualByComparingTo("180.00");
        assertThat(snapshot.get("old_actual_hours")).isNull();
        assertThat(snapshot.get("new_actual_hours")).isNull();
        assertThat(snapshot.get("old_actual_quantity")).isNull();
        assertThat(snapshot.get("new_actual_quantity")).isNull();

        // Completed shift wage and compensation metadata cannot be changed outside the audited correction path.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE shifts SET driver_wage=driver_wage+1 WHERE tenant_id=? AND id=?",
                tenantId, shiftId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("COMPLETED_SHIFT_IMMUTABLE");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE shifts SET compensation_rate=compensation_rate+1 WHERE tenant_id=? AND id=?",
                tenantId, shiftId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("COMPLETED_SHIFT_COMPENSATION_IMMUTABLE");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE shifts SET compensation_calculated_amount=compensation_calculated_amount+1 "
                        + "WHERE tenant_id=? AND id=?", tenantId, shiftId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("COMPLETED_SHIFT_COMPENSATION_IMMUTABLE");

        Long correctionId = jdbc.queryForObject(
                "SELECT id FROM shift_corrections WHERE tenant_id=? AND shift_id=?",
                Long.class, tenantId, shiftId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE shift_corrections SET old_compensation_rate=old_compensation_rate+1 "
                        + "WHERE tenant_id=? AND id=?", tenantId, correctionId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("SHIFT_CORRECTION_AUDIT_IMMUTABLE");
    }


}
