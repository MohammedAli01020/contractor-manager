package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShiftTenantIsolationIntegrationTest extends AbstractShiftIntegrationTest {

    private String tokenA;
    private String tokenB;
    private long siteA;
    private long driverA;
    private long equipmentA;
    private long siteB;
    private long driverB;
    private long equipmentB;
    private long shiftA;
    private long shiftAVersion;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = registerTenant("Shift Iso A", uniqueName("owa")).path("accessToken").asText();
        tokenB = registerTenant("Shift Iso B", uniqueName("owb")).path("accessToken").asText();

        siteA = createSite(tokenA, "A Site");
        driverA = createDriver(tokenA, "A Driver", "100", "100", "100");
        equipmentA = createEquipment(tokenA, "A-01", createType(tokenA, "Type"), "10", "20");

        siteB = createSite(tokenB, "B Site");
        driverB = createDriver(tokenB, "B Driver", "100", "100", "100");
        equipmentB = createEquipment(tokenB, "B-01", createType(tokenB, "Type"), "10", "20");

        JsonNode shift = createShift(tokenA, shiftBody(siteA, driverA, equipmentA, today(), "MORNING"));
        shiftA = shift.path("id").asLong();
        shiftAVersion = shift.path("version").asLong();
    }

    @Test
    void shiftsOfAnotherTenantAreInvisible() throws Exception {
        getJson("/api/v1/shifts/" + shiftA, tokenB).andExpect(status().isNotFound());
        getJson("/api/v1/shifts/" + shiftA + "/history", tokenB).andExpect(status().isNotFound());
        assertThat(body(getJson("/api/v1/shifts", tokenB)).path("totalElements").asLong()).isZero();
    }

    @Test
    void cannotEndOrCancelAnotherTenantsShift() throws Exception {
        Map<String, Object> end = new HashMap<>();
        end.put("version", shiftAVersion);
        postJson("/api/v1/shifts/" + shiftA + "/end", end, tokenB).andExpect(status().isNotFound());

        Map<String, Object> cancel = new HashMap<>();
        cancel.put("reason", "hostile cancel");
        cancel.put("version", shiftAVersion);
        postJson("/api/v1/shifts/" + shiftA + "/cancel", cancel, tokenB).andExpect(status().isNotFound());

        JsonNode stored = body(getJson("/api/v1/shifts/" + shiftA, tokenA).andExpect(status().isOk()));
        assertThat(stored.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(stored.path("version").asLong()).isEqualTo(shiftAVersion);
    }

    @Test
    void cannotStartShiftsWithAnotherTenantsReferences() throws Exception {
        postJson("/api/v1/shifts/start", shiftBody(siteA, driverB, equipmentB, today(), "MORNING"), tokenB)
                .andExpect(status().isNotFound());
        postJson("/api/v1/shifts/start", shiftBody(siteB, driverA, equipmentB, today(), "MORNING"), tokenB)
                .andExpect(status().isNotFound());
        postJson("/api/v1/shifts/start", shiftBody(siteB, driverB, equipmentA, today(), "MORNING"), tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    void defaultsAndStartOptionsAreTenantScoped() throws Exception {
        getJson("/api/v1/shifts/defaults?driverId=" + driverA + "&equipmentId=" + equipmentB + "&shiftType=MORNING", tokenB)
                .andExpect(status().isNotFound());
        getJson("/api/v1/shifts/defaults?driverId=" + driverB + "&equipmentId=" + equipmentA + "&shiftType=MORNING", tokenB)
                .andExpect(status().isNotFound());

        JsonNode options = body(getJson("/api/v1/shifts/start-options", tokenB).andExpect(status().isOk()));
        assertThat(containsId(options.path("drivers"), driverB)).isTrue();
        assertThat(containsId(options.path("drivers"), driverA)).isFalse();
        assertThat(containsId(options.path("equipment"), equipmentB)).isTrue();
        assertThat(containsId(options.path("equipment"), equipmentA)).isFalse();
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
