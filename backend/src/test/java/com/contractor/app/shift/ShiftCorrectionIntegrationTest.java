package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.contractor.app.driver.infra.DriverLedgerEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ShiftCorrectionIntegrationTest extends AbstractShiftIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DriverLedgerEntryRepository ledger;

    private String token;
    private long tenantId;
    private long siteId;
    private long driverId;
    private long equipmentId;
    private long typeId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode registered = registerTenant("Correction Co", uniqueName("correction"));
        token = registered.path("accessToken").asText();
        tenantId = registered.path("user").path("tenantId").asLong();
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
