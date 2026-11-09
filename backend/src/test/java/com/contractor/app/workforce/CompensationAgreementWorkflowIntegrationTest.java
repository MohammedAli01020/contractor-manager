package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import com.contractor.app.workforce.infra.CompensationAgreementRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

class CompensationAgreementWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private CompensationAgreementRepository agreements;

    private String token;
    private long memberId;
    private long siteId;
    private long driverAssignmentId;
    private long workerAssignmentId;

    @BeforeEach
    void setUp() throws Exception {
        reset(agreements);
        JsonNode owner = registerTenant("Compensation Workflow Co", uniqueName("compwf"));
        token = owner.path("accessToken").asText();
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long driverRole = roleId(roles, "DRIVER");
        long workerRole = roleId(roles, "WORKER");

        JsonNode site = body(postJson("/api/v1/sites", Map.of("name", uniqueName("Comp WF Site"), "address", "Cairo"), token)
                .andExpect(status().isCreated()));
        siteId = site.path("id").asLong();

        JsonNode driver = body(postJson("/api/v1/drivers", Map.of(
                "name", "Compensation Workflow Person", "workerType", "DRIVER",
                "morningRate", 0, "eveningRate", 0, "nightRate", 0), token)
                .andExpect(status().isCreated()));
        memberId = jdbc.queryForObject(
                "select workforce_member_id from drivers where id = ?",
                Long.class, driver.path("id").asLong());

        driverAssignmentId = createAssignment(driverRole);
        workerAssignmentId = createAssignment(workerRole);
    }

    @Test
    void differentAssignmentAgreementsMayOverlapButMemberLevelAgreementConflictsWithBoth() throws Exception {
        long driverAgreement = createAgreement(driverAssignmentId, "2026-01-01", null, null, null);
        approve(driverAgreement);
        long duplicateDriverAgreement = createAgreement(driverAssignmentId, "2026-02-01", null, null, null);
        postJson(path(duplicateDriverAgreement, "approve"), Map.of(), token)
                .andExpect(status().isConflict());
        long workerAgreement = createAgreement(workerAssignmentId, "2026-01-01", null, null, null);
        approve(workerAgreement);

        long memberAgreement = createAgreement(null, "2026-01-01", null, null, null);
        postJson(path(memberAgreement, "approve"), Map.of(), token).andExpect(status().isConflict());
    }

    @Test
    void concurrentOverlappingApprovalsForOneMemberAllowOnlyOneAgreement() throws Exception {
        long first = createAgreement(null, "2026-01-01", null, null, null);
        long second = createAgreement(null, "2026-01-01", null, null, null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> {
                start.await();
                return postJson(path(first, "approve"), Map.of(), token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> secondResult = executor.submit(() -> {
                start.await();
                return postJson(path(second, "approve"), Map.of(), token)
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            int a = firstResult.get();
            int b = secondResult.get();
            assertThat(List.of(a, b)).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void replacementClosesOldAgreementAndApprovesNewAgreementWithAudit() throws Exception {
        long oldId = createAgreement(null, "2026-01-01", null, null, null);
        JsonNode oldApproved = approve(oldId);

        long newId = createAgreement(null, "2026-12-01", null, oldId, "تعديل الأجر باتفاق الطرفين");
        postJson(path(newId, "approve"), Map.of(), token).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from compensation_agreements where id = ?",
                String.class, oldId)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select effective_to from compensation_agreements where id = ?",
                java.sql.Date.class, oldId)).isNull();

        JsonNode replacement = body(postJson(path(newId, "approve-replacement"), Map.of(), token)
                .andExpect(status().isOk()));

        assertThat(replacement.path("status").asText()).isEqualTo("APPROVED");
        assertThat(replacement.path("supersedesAgreementId").asLong()).isEqualTo(oldId);
        assertThat(replacement.path("changeReason").asText()).isEqualTo("تعديل الأجر باتفاق الطرفين");
        assertThat(replacement.path("approvedByUserId").asLong()).isPositive();
        assertThat(replacement.path("approvedAt").asText()).isNotBlank();

        JsonNode all = body(getJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", token).andExpect(status().isOk()));
        JsonNode old = find(all, oldId);
        assertThat(old.path("status").asText()).isEqualTo("SUPERSEDED");
        assertThat(old.path("effectiveTo").asText()).isEqualTo("2026-11-30");
        assertThat(old.path("approvedByUserId").asLong())
                .isEqualTo(oldApproved.path("approvedByUserId").asLong());
    }

    @Test
    void replacementFailureRollsBackOldAgreementClosure() throws Exception {
        long oldId = createAgreement(null, "2026-01-01", null, null, null);
        approve(oldId);
        long newId = createAgreement(null, "2026-12-01", null, oldId,
                "اختبار التراجع الذري عند فشل الحفظ");

        doThrow(new IllegalStateException("forced replacement persistence failure"))
                .when(agreements).saveAndFlush(argThat(
                        agreement -> agreement != null
                                && newId == agreement.getId()
                                && agreement.getSupersedesAgreementId() != null));

        int responseStatus;
        try {
            responseStatus = postJson(path(newId, "approve-replacement"), Map.of(), token)
                    .andReturn().getResponse().getStatus();
        } finally {
            reset(agreements);
        }
        assertThat(responseStatus).isEqualTo(500);
        assertThat(jdbc.queryForObject(
                "select status from compensation_agreements where id = ?",
                String.class, oldId)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "select effective_to from compensation_agreements where id = ?",
                java.sql.Date.class, oldId)).isNull();
        assertThat(jdbc.queryForObject(
                "select status from compensation_agreements where id = ?",
                String.class, newId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                "select approved_at from compensation_agreements where id = ?",
                java.sql.Timestamp.class, newId)).isNull();
    }

    @Test
    void cancelledDraftDoesNotBecomeApprovedAndIsVisibleInHistory() throws Exception {
        long draftId = createAgreement(null, "2026-01-01", null, null, null);
        postJson(path(draftId, "cancel"), Map.of(), token).andExpect(status().isNoContent());
        postJson(path(draftId, "approve"), Map.of(), token).andExpect(status().isConflict());

        JsonNode all = body(getJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", token).andExpect(status().isOk()));
        assertThat(find(all, draftId).path("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void mobileSupportedShiftHourlyAndQuantityAgreementPayloadsArePersisted() throws Exception {
        List<Map<String, Object>> shiftComponents = List.of(
                Map.of("componentCode", "BASE_SHIFT_MORNING", "displayName", "أجر الوردية الصباحية",
                        "basis", "SHIFT", "rate", 300, "shiftType", "MORNING", "sortOrder", 0),
                Map.of("componentCode", "BASE_SHIFT_EVENING", "displayName", "أجر الوردية المسائية",
                        "basis", "SHIFT", "rate", 325, "shiftType", "EVENING", "sortOrder", 1),
                Map.of("componentCode", "BASE_SHIFT_NIGHT", "displayName", "أجر الوردية الليلية",
                        "basis", "SHIFT", "rate", 350, "shiftType", "NIGHT", "sortOrder", 2));
        long shiftAgreement = createAgreementWithComponents(driverAssignmentId,
                "2026-01-01", null, shiftComponents);
        JsonNode approvedShift = approve(shiftAgreement);
        assertThat(approvedShift.path("components").size()).isEqualTo(3);
        assertThat(approvedShift.path("components").get(0).path("shiftType").asText()).isEqualTo("MORNING");
        assertThat(approvedShift.path("components").get(1).path("shiftType").asText()).isEqualTo("EVENING");
        assertThat(approvedShift.path("components").get(2).path("shiftType").asText()).isEqualTo("NIGHT");

        long hourlyAgreement = createAgreementWithComponents(workerAssignmentId,
                "2026-01-01", "2026-06-30", List.of(Map.of(
                        "componentCode", "BASE_HOURLY", "displayName", "أجر الساعة",
                        "basis", "HOUR", "rate", 125.50, "unitCode", "HOUR", "sortOrder", 0)));
        JsonNode approvedHourly = approve(hourlyAgreement);
        assertThat(approvedHourly.path("components").get(0).path("basis").asText()).isEqualTo("HOUR");
        assertThat(approvedHourly.path("components").get(0).path("unitCode").asText()).isEqualTo("HOUR");

        long quantityAgreement = createAgreementWithComponents(workerAssignmentId,
                "2026-07-01", null, List.of(Map.of(
                        "componentCode", "BASE_QUANTITY", "displayName", "أجر الوحدة",
                        "basis", "QUANTITY", "rate", 20, "unitCode", "TON", "sortOrder", 0)));
        JsonNode approvedQuantity = approve(quantityAgreement);
        assertThat(approvedQuantity.path("components").get(0).path("basis").asText()).isEqualTo("QUANTITY");
        assertThat(approvedQuantity.path("components").get(0).path("unitCode").asText()).isEqualTo("TON");
    }

    @Test
    void compensationAgreementCanUseTenantCatalogDefinitionAndKeepsItsSnapshot() throws Exception {
        JsonNode definition = body(postJson("/api/v1/workforce/financial-item-definitions", Map.of(
                "itemCode", "STANDARD_HOURLY_WAGE",
                "displayName", "أجر الساعة القياسي",
                "description", "تعريف قابل لإعادة الاستخدام",
                "family", "EARNING",
                "calculationMethod", "PER_HOUR",
                "allowRecurring", true,
                "allowOneOff", true,
                "requiresApproval", true), token).andExpect(status().isCreated()));

        long definitionId = definition.path("id").asLong();
        Map<String, Object> component = new HashMap<>();
        component.put("financialItemDefinitionId", definitionId);
        component.put("rate", 145.50);
        component.put("unitCode", "HOUR");
        component.put("sortOrder", 0);

        long agreementId = createAgreementWithComponents(workerAssignmentId,
                "2026-01-01", null, List.of(component));
        JsonNode agreement = body(getJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", token).andExpect(status().isOk()));
        JsonNode createdAgreement = find(agreement, agreementId);
        JsonNode savedComponent = createdAgreement.path("components").get(0);

        assertThat(savedComponent.path("financialItemDefinitionId").asLong()).isEqualTo(definitionId);
        assertThat(savedComponent.path("componentCode").asText()).isEqualTo("STANDARD_HOURLY_WAGE");
        assertThat(savedComponent.path("displayName").asText()).isEqualTo("أجر الساعة القياسي");
        assertThat(savedComponent.path("basis").asText()).isEqualTo("HOUR");
        assertThat(savedComponent.path("rate").decimalValue()).isEqualByComparingTo("145.50");

        Map<String, Object> deactivate = new HashMap<>();
        deactivate.put("version", definition.path("version").asLong());
        deactivate.put("displayName", definition.path("displayName").asText());
        deactivate.put("description", definition.path("description").asText());
        deactivate.put("allowRecurring", true);
        deactivate.put("allowOneOff", true);
        deactivate.put("requiresApproval", true);
        JsonNode updatedDefinition = body(putJson(
                "/api/v1/workforce/financial-item-definitions/" + definitionId,
                deactivate, token).andExpect(status().isOk()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/workforce/financial-item-definitions/" + definitionId + "/deactivate")
                .param("version", updatedDefinition.path("version").asText())
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        Map<String, Object> rejectedComponent = new HashMap<>();
        rejectedComponent.put("financialItemDefinitionId", definitionId);
        rejectedComponent.put("rate", 150);
        rejectedComponent.put("unitCode", "HOUR");
        rejectedComponent.put("sortOrder", 0);
        Map<String, Object> rejectedAgreement = new HashMap<>();
        rejectedAgreement.put("workforceAssignmentId", workerAssignmentId);
        rejectedAgreement.put("effectiveFrom", "2027-01-01");
        rejectedAgreement.put("currencyCode", "EGP");
        rejectedAgreement.put("components", List.of(rejectedComponent));
        postJson("/api/v1/workforce/members/" + memberId + "/compensation-agreements",
                rejectedAgreement, token).andExpect(status().isConflict());
    }

    @Test
    void anotherTenantCannotReadOrAttachAgreementToThisMember() throws Exception {
        String otherToken = registerTenant("Other Compensation Co", uniqueName("othercomp"))
                .path("accessToken").asText();
        getJson("/api/v1/workforce/members/" + memberId + "/compensation-agreements", otherToken)
                .andExpect(status().isNotFound());

        Map<String, Object> request = agreementBody(null, "2026-01-01", null, null, null);
        postJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", request, otherToken).andExpect(status().isNotFound());
    }

    private long createAssignment(long roleId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("roleId", roleId);
        request.put("siteId", siteId);
        request.put("effectiveFrom", "2026-01-01");
        JsonNode result = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                request, token).andExpect(status().isCreated()));
        return result.path("id").asLong();
    }

    private long createAgreement(Long assignmentId, String from, String to,
                                 Long supersedesId, String reason) throws Exception {
        JsonNode result = body(postJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements",
                agreementBody(assignmentId, from, to, supersedesId, reason), token)
                .andExpect(status().isCreated()));
        return result.path("id").asLong();
    }

    private long createAgreementWithComponents(Long assignmentId, String from, String to,
                                                List<Map<String, Object>> components) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("workforceAssignmentId", assignmentId);
        request.put("effectiveFrom", from);
        request.put("effectiveTo", to);
        request.put("currencyCode", "EGP");
        request.put("components", components);
        JsonNode result = body(postJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", request, token).andExpect(status().isCreated()));
        return result.path("id").asLong();
    }

    private Map<String, Object> agreementBody(Long assignmentId, String from, String to,
                                               Long supersedesId, String reason) {
        Map<String, Object> request = new HashMap<>();
        request.put("workforceAssignmentId", assignmentId);
        request.put("effectiveFrom", from);
        request.put("effectiveTo", to);
        request.put("currencyCode", "EGP");
        request.put("supersedesAgreementId", supersedesId);
        request.put("changeReason", reason);
        request.put("components", List.of(Map.of(
                "componentCode", "MORNING_RATE",
                "displayName", "أجر الوردية الصباحية",
                "basis", "SHIFT",
                "rate", 300,
                "shiftType", "MORNING",
                "sortOrder", 0)));
        return request;
    }

    private JsonNode approve(long agreementId) throws Exception {
        return body(postJson(path(agreementId, "approve"), Map.of(), token)
                .andExpect(status().isOk()));
    }

    private String path(long agreementId, String action) {
        return "/api/v1/workforce/members/" + memberId
                + "/compensation-agreements/" + agreementId + "/" + action;
    }

    private JsonNode find(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.path("id").asLong() == id) return item;
        }
        throw new AssertionError("Missing compensation agreement " + id);
    }

    private long roleId(JsonNode roles, String code) {
        for (JsonNode role : roles) {
            if (code.equals(role.path("code").asText())) return role.path("id").asLong();
        }
        throw new AssertionError("Missing seeded role: " + code);
    }
}
