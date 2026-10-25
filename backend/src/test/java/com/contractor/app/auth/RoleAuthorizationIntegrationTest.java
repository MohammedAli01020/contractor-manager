package com.contractor.app.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies role boundaries at the HTTP/API layer, not just in the Flutter UI. */
class RoleAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private String adminToken;
    private String operatorToken;
    private String viewerToken;
    private long viewerId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode owner = registerTenant("Role Test Co", uniqueName("roleadmin"));
        adminToken = owner.path("accessToken").asText();

        String operatorName = uniqueName("operator");
        body(postJson("/api/v1/users", userBody(operatorName), adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OPERATOR")));
        operatorToken = login(operatorName).path("accessToken").asText();

        String viewerName = uniqueName("viewer");
        Map<String, Object> viewerRequest = userBody(viewerName);
        viewerRequest.put("role", "VIEWER");
        JsonNode viewer = body(postJson("/api/v1/users", viewerRequest, adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("VIEWER")));
        viewerId = viewer.path("id").asLong();
        viewerToken = login(viewerName).path("accessToken").asText();
    }

    @Test
    void viewerCanReadButCannotWriteMasterData() throws Exception {
        getJson("/api/v1/sites", viewerToken).andExpect(status().isOk());
        postJson("/api/v1/sites", Map.of("name", "Viewer Must Not Create"), viewerToken)
                .andExpect(status().isForbidden());
        putJson("/api/v1/users/" + viewerId, Map.of("fullName", "Forbidden", "active", true), viewerToken)
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReadOperationalDataButCannotManageUsersOrMasterData() throws Exception {
        getJson("/api/v1/sites", operatorToken).andExpect(status().isOk());
        getJson("/api/v1/users", operatorToken).andExpect(status().isForbidden());
        postJson("/api/v1/sites", Map.of("name", "Operator Must Not Create"), operatorToken)
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerAndOperatorCannotWriteMasterData() throws Exception {
        Map<String, Object> validDriver = Map.of(
                "name", "Authorization test",
                "workerType", "DRIVER",
                "morningRate", 0,
                "eveningRate", 0,
                "nightRate", 0);
        Map<String, Object> validEquipment = Map.of(
                "code", "AUTH-TEST",
                "equipmentTypeId", 1,
                "operatingCostPerShift", 0);
        Map<String, Object> validEquipmentType = Map.of("name", "Authorization test");

        for (String token : new String[] {viewerToken, operatorToken}) {
            // Valid request bodies ensure the response tests authorization rather than DTO validation.
            postJson("/api/v1/drivers", validDriver, token).andExpect(status().isForbidden());
            putJson("/api/v1/drivers/1", validDriver, token).andExpect(status().isForbidden());
            deleteJson("/api/v1/drivers/1", token).andExpect(status().isForbidden());

            postJson("/api/v1/equipment", validEquipment, token).andExpect(status().isForbidden());
            putJson("/api/v1/equipment/1", validEquipment, token).andExpect(status().isForbidden());
            deleteJson("/api/v1/equipment/1", token).andExpect(status().isForbidden());

            postJson("/api/v1/equipment-types", validEquipmentType, token).andExpect(status().isForbidden());
            putJson("/api/v1/equipment-types/1", validEquipmentType, token).andExpect(status().isForbidden());
            deleteJson("/api/v1/equipment-types/1", token).andExpect(status().isForbidden());
        }
    }

    @Test
    void viewerCannotPerformShiftOperations() throws Exception {
        Map<String, Object> validShift = Map.of(
                "shiftType", "MORNING",
                "siteId", 1,
                "driverId", 1,
                "equipmentId", 1);
        Map<String, Object> validVersion = Map.of("version", 0);
        Map<String, Object> validCancel = Map.of("reason", "Authorization test", "version", 0);

        postJson("/api/v1/shifts", validShift, viewerToken).andExpect(status().isForbidden());
        putJson("/api/v1/shifts/1", validShift, viewerToken).andExpect(status().isForbidden());
        postJson("/api/v1/shifts/1/finish", validVersion, viewerToken).andExpect(status().isForbidden());
        postJson("/api/v1/shifts/1/cancel", validCancel, viewerToken).andExpect(status().isForbidden());
        postJson("/api/v1/shifts/1/reinstate", validVersion, viewerToken).andExpect(status().isForbidden());
    }

    @Test
    void operatorCanStartAndFinishShifts() throws Exception {
        long siteId = body(postJson("/api/v1/sites", Map.of("name", "Operator Shift Site"), adminToken)
                .andExpect(status().isCreated())).path("id").asLong();

        Map<String, Object> driver = Map.of(
                "name", "Operator Shift Driver",
                "workerType", "DRIVER",
                "morningRate", 150,
                "eveningRate", 175,
                "nightRate", 200);
        long driverId = body(postJson("/api/v1/drivers", driver, adminToken)
                .andExpect(status().isCreated())).path("id").asLong();

        long typeId = body(postJson("/api/v1/equipment-types", Map.of("name", "Operator Shift Type"), adminToken)
                .andExpect(status().isCreated())).path("id").asLong();
        Map<String, Object> equipment = Map.of(
                "code", "OP-SHIFT-TEST",
                "equipmentTypeId", typeId,
                "operatingCostPerShift", 500,
                "defaultShiftPrice", 1200);
        long equipmentId = body(postJson("/api/v1/equipment", equipment, adminToken)
                .andExpect(status().isCreated())).path("id").asLong();

        Map<String, Object> shift = Map.of(
                "shiftType", "MORNING",
                "siteId", siteId,
                "driverId", driverId,
                "equipmentId", equipmentId);
        JsonNode started = body(postJson("/api/v1/shifts", shift, operatorToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS")));

        postJson("/api/v1/shifts/" + started.path("id").asLong() + "/finish",
                Map.of("version", started.path("version").asLong()), operatorToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void adminCanManageMasterDataAndCannotGrantAdminThroughUserCreation() throws Exception {
        postJson("/api/v1/sites", Map.of("name", "Admin Site"), adminToken)
                .andExpect(status().isCreated());

        Map<String, Object> escalation = userBody(uniqueName("escalation"));
        escalation.put("role", "ADMIN");
        postJson("/api/v1/users", escalation, adminToken)
                .andExpect(status().isBadRequest());
    }

    private JsonNode login(String username) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", PASSWORD);
        return body(postJson("/api/v1/auth/login", request, null).andExpect(status().isOk()));
    }
}
