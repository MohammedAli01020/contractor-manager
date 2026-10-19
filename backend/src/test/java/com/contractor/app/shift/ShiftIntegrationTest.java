package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShiftIntegrationTest extends AbstractShiftIntegrationTest {

    private String token;
    private long siteId;
    private long driverId;
    private long typeId;
    private long equipmentId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerTenant("Shift Co", uniqueName("owner")).path("accessToken").asText();
        siteId = createSite(token, "Main Site");
        driverId = createDriver(token, "Driver One", "150", "175", "200");
        typeId = createType(token, "Excavator");
        equipmentId = createEquipment(token, "EX-01", typeId, "500", "1200");
    }

    @Test
    void startShiftSuccessfullyUsesDefaultsAndInProgressStatus() throws Exception {
        JsonNode shift = createShift(token, shiftBody(siteId, driverId, equipmentId, today(), "MORNING"));

        assertThat(shift.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(shift.path("price").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(shift.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(shift.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(shift.path("priceAfterWage").decimalValue()).isEqualByComparingTo("1050.00");
        assertThat(shift.path("startedAt").asText()).isNotBlank();
        assertThat(shift.path("endedAt").isNull()).isTrue();
        assertThat(shift.path("cancelledAt").isNull()).isTrue();
        assertThat(shift.path("hours").isMissingNode()).isTrue();
        assertThat(shift.path("quantity").isMissingNode()).isTrue();
        assertThat(shift.path("quantityUnit").isMissingNode()).isTrue();
    }

    @Test
    void rejectsStartingWhenDriverAlreadyHasInProgressShift() throws Exception {
        createShift(token, shiftBody(siteId, driverId, equipmentId, today(), "MORNING"));
        long otherEquipment = createEquipment(token, "EX-02", typeId, "500", "1200");

        createShiftExpectingConflict(shiftBody(siteId, driverId, otherEquipment, today(), "EVENING"));
    }

    @Test
    void rejectsStartingWhenEquipmentIsNotAvailable() throws Exception {
        setEquipmentStatus(token, equipmentId, "MAINTENANCE");

        postJson("/api/v1/shifts/start", shiftBody(siteId, driverId, equipmentId, today(), "MORNING"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void rejectsStartingWhenEquipmentAlreadyHasInProgressShift() throws Exception {
        createShift(token, shiftBody(siteId, driverId, equipmentId, today(), "MORNING"));
        long otherDriver = createDriver(token, "Driver Two", "100", "100", "100");

        createShiftExpectingConflict(shiftBody(siteId, otherDriver, equipmentId, today(), "EVENING"));
    }

    @Test
    void endShiftSuccessfullyAndDurationIsCalculatedFromTimestamps() throws Exception {
        JsonNode started = createShift(token, shiftBody(siteId, driverId, equipmentId, today(), "MORNING"));

        Map<String, Object> end = new HashMap<>();
        end.put("version", started.path("version").asLong());
        JsonNode ended = body(postJson("/api/v1/shifts/" + started.path("id").asLong() + "/end", end, token)
                .andExpect(status().isOk()));

        assertThat(ended.path("status").asText()).isEqualTo("COMPLETED");
        LocalDateTime startedAt = LocalDateTime.parse(ended.path("startedAt").asText());
        LocalDateTime endedAt = LocalDateTime.parse(ended.path("endedAt").asText());
        long expectedMinutes = ChronoUnit.MINUTES.between(startedAt, endedAt);
        assertThat(ended.path("durationMinutes").asLong()).isEqualTo(expectedMinutes);
    }

    @Test
    void cancelRequiresReasonAndStoresCancelledAt() throws Exception {
        JsonNode started = createShift(token, shiftBody(siteId, driverId, equipmentId, today(), "MORNING"));

        Map<String, Object> noReason = new HashMap<>();
        noReason.put("version", started.path("version").asLong());
        postJson("/api/v1/shifts/" + started.path("id").asLong() + "/cancel", noReason, token)
                .andExpect(status().isBadRequest());

        Map<String, Object> cancel = new HashMap<>();
        cancel.put("reason", "عطل مفاجئ");
        cancel.put("version", started.path("version").asLong());
        JsonNode cancelled = body(postJson("/api/v1/shifts/" + started.path("id").asLong() + "/cancel", cancel, token)
                .andExpect(status().isOk()));

        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("cancelReason").asText()).isEqualTo("عطل مفاجئ");
        assertThat(cancelled.path("cancelledAt").asText()).isNotBlank();
    }

    @Test
    void optimisticLockingConflictOnEnd() throws Exception {
        JsonNode started = createShift(token, shiftBody(siteId, driverId, equipmentId, today(), "MORNING"));

        Map<String, Object> end = new HashMap<>();
        end.put("version", started.path("version").asLong() + 5);
        postJson("/api/v1/shifts/" + started.path("id").asLong() + "/end", end, token)
                .andExpect(status().isConflict());
    }

    @Test
    void startOptionsOnlyReturnAvailableResources() throws Exception {
        long busyDriver = createDriver(token, "Busy Driver", "90", "90", "90");
        long busyEquipment = createEquipment(token, "EX-03", typeId, "500", "1200");
        createShift(token, shiftBody(siteId, busyDriver, busyEquipment, today(), "MORNING"));

        long maintenanceEquipment = createEquipment(token, "EX-04", typeId, "500", "1200");
        setEquipmentStatus(token, maintenanceEquipment, "MAINTENANCE");

        JsonNode options = body(getJson("/api/v1/shifts/start-options", token).andExpect(status().isOk()));
        assertThat(containsId(options.path("drivers"), driverId)).isTrue();
        assertThat(containsId(options.path("drivers"), busyDriver)).isFalse();
        assertThat(containsId(options.path("equipment"), equipmentId)).isTrue();
        assertThat(containsId(options.path("equipment"), busyEquipment)).isFalse();
        assertThat(containsId(options.path("equipment"), maintenanceEquipment)).isFalse();
    }

    private void createShiftExpectingConflict(Map<String, Object> body) throws Exception {
        postJson("/api/v1/shifts/start", body, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private void setEquipmentStatus(String accessToken, long id, String statusValue) throws Exception {
        JsonNode equipment = body(getJson("/api/v1/equipment/" + id, accessToken));
        Map<String, Object> edit = new HashMap<>();
        edit.put("code", equipment.path("code").asText());
        edit.put("equipmentTypeId", equipment.path("equipmentTypeId").asLong());
        edit.put("operatingCostPerShift", new BigDecimal(equipment.path("operatingCostPerShift").asText()));
        edit.put("defaultShiftPrice", new BigDecimal(equipment.path("defaultShiftPrice").asText()));
        edit.put("status", statusValue);
        edit.put("version", equipment.path("version").asLong());
        putJson("/api/v1/equipment/" + id, edit, accessToken).andExpect(status().isOk());
    }

    private static boolean containsId(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }
}
