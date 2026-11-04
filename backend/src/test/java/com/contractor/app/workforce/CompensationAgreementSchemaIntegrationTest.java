package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class CompensationAgreementSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long tenantId;
    private long userId;
    private long memberId;
    private long assignmentId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String username = uniqueName("compowner");
        JsonNode owner = registerTenant("Compensation Co", username);
        token = owner.path("accessToken").asText();
        Map<String, Object> user = jdbc.queryForMap(
                "select id, tenant_id from users where username = ?", username);
        userId = ((Number) user.get("id")).longValue();
        tenantId = ((Number) user.get("tenant_id")).longValue();

        JsonNode driver = body(postJson("/api/v1/drivers", Map.of(
                "name", "Compensation Test Person", "workerType", "DRIVER",
                "morningRate", 0, "eveningRate", 0, "nightRate", 0), token)
                .andExpect(status().isCreated()));
        memberId = jdbc.queryForObject(
                "select workforce_member_id from drivers where id = ?",
                Long.class, driver.path("id").asLong());

        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long roleId = roleId(roles, "DRIVER");
        JsonNode assignment = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                Map.of("roleId", roleId, "effectiveFrom", "2026-01-01"), token)
                .andExpect(status().isCreated()));
        assignmentId = assignment.path("id").asLong();
    }

    @Test
    void schemaStoresZeroRatesAndAllowsIndependentShiftComponents() {
        long agreementId = insertDraft(memberId, assignmentId, userId, tenantId);
        insertComponent(agreementId, tenantId, "MORNING", "Morning", "SHIFT",
                new BigDecimal("0.00"), null, "MORNING");

        assertThat(jdbc.queryForObject(
                "select rate from compensation_components where compensation_agreement_id = ?",
                BigDecimal.class, agreementId)).isEqualByComparingTo("0.00");

        insertComponent(agreementId, tenantId, "EVENING", "Evening", "SHIFT",
                new BigDecimal("125.50"), null, "EVENING");
        insertComponent(agreementId, tenantId, "NIGHT", "Night", "SHIFT",
                new BigDecimal("150.00"), null, "NIGHT");
        assertThat(jdbc.queryForObject(
                "select count(*) from compensation_components where compensation_agreement_id = ?",
                Integer.class, agreementId)).isEqualTo(3);
    }

    @Test
    void schemaRejectsInvalidBasisUnitAndShiftCombinations() {
        long agreementId = insertDraft(memberId, assignmentId, userId, tenantId);

        assertThatThrownBy(() -> insertComponent(agreementId, tenantId, "BAD_SHIFT", "Bad shift",
                "SHIFT", new BigDecimal("10.00"), null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertComponent(agreementId, tenantId, "BAD_FIXED", "Bad fixed",
                "FIXED", new BigDecimal("10.00"), null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertComponent(agreementId, tenantId, "BAD_HOUR", "Bad hour",
                "HOUR", new BigDecimal("10.00"), null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertComponent(agreementId, tenantId, "NEGATIVE", "Negative",
                "SHIFT", new BigDecimal("-1.00"), null, "MORNING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void assignmentAndMemberMustBelongToSameTenantAndApprovedStateRequiresAudit() throws Exception {
        String otherUsername = uniqueName("othercomp");
        registerTenant("Other Compensation Co", otherUsername);
        Map<String, Object> otherUser = jdbc.queryForMap(
                "select id, tenant_id from users where username = ?", otherUsername);
        long otherTenantId = ((Number) otherUser.get("tenant_id")).longValue();

        assertThatThrownBy(() -> insertDraft(memberId, assignmentId,
                ((Number) otherUser.get("id")).longValue(), otherTenantId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "insert into compensation_agreements (tenant_id, workforce_member_id, workforce_assignment_id, " +
                "status, effective_from, currency_code, source, created_by_user_id, updated_by_user_id) " +
                "values (?, ?, ?, 'APPROVED', ?, 'EGP', 'MANUAL', ?, ?)",
                tenantId, memberId, assignmentId, LocalDate.of(2026, 1, 1), userId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertDraft(long member, long assignment, long actor, long tenant) {
        jdbc.update(
                "insert into compensation_agreements (tenant_id, workforce_member_id, workforce_assignment_id, " +
                "status, effective_from, currency_code, source, created_by_user_id, updated_by_user_id) " +
                "values (?, ?, ?, 'DRAFT', ?, 'EGP', 'MANUAL', ?, ?)",
                tenant, member, assignment, LocalDate.of(2026, 1, 1), actor, actor);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    private void insertComponent(long agreement, long tenant, String code, String name,
                                 String basis, BigDecimal rate, String unit, String shift) {
        jdbc.update(
                "insert into compensation_components (tenant_id, compensation_agreement_id, component_code, " +
                "display_name, basis, rate, unit_code, shift_type) values (?, ?, ?, ?, ?, ?, ?, ?)",
                tenant, agreement, code, name, basis, rate, unit, shift);
    }

    private long roleId(JsonNode roles, String code) {
        for (JsonNode role : roles) {
            if (code.equals(role.path("code").asText())) return role.path("id").asLong();
        }
        throw new AssertionError("Missing seeded role: " + code);
    }
}
