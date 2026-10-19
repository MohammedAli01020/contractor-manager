package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** المقاول (ب) لا يرى ولا يعدّل ولا يلغي ولا يربط بورديات وسجلات المقاول (أ). */
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

        JsonNode shift = createShift(tokenA, shiftBody(siteA, driverA, equipmentA, today().minusDays(1), "MORNING"));
        shiftA = shift.path("id").asLong();
        shiftAVersion = shift.path("version").asLong();
    }

    @Test
    void shiftsOfAnotherTenantAreInvisible() throws Exception {
        getJson("/api/v1/shifts/" + shiftA, tokenB).andExpect(status().isNotFound());
        getJson("/api/v1/shifts/" + shiftA + "/history", tokenB).andExpect(status().isNotFound());
        assertThat(body(getJson("/api/v1/shifts", tokenB)).path("totalElements").asLong()).isZero();
        // حتى لو فلتر بمعرّفات المقاول (أ)
        assertThat(body(getJson("/api/v1/shifts?driverId=" + driverA + "&siteId=" + siteA, tokenB))
                .path("totalElements").asLong()).isZero();
    }

    @Test
    void cannotModifyCancelOrReinstateAnotherTenantsShift() throws Exception {
        Map<String, Object> edit = shiftBody(siteB, driverB, equipmentB, today().minusDays(1), "MORNING");
        edit.put("version", shiftAVersion);
        putJson("/api/v1/shifts/" + shiftA, edit, tokenB).andExpect(status().isNotFound());

        Map<String, Object> cancel = new HashMap<>();
        cancel.put("reason", "hostile cancel");
        cancel.put("version", shiftAVersion);
        postJson("/api/v1/shifts/" + shiftA + "/cancel", cancel, tokenB).andExpect(status().isNotFound());

        Map<String, Object> reinstate = new HashMap<>();
        reinstate.put("version", shiftAVersion);
        postJson("/api/v1/shifts/" + shiftA + "/reinstate", reinstate, tokenB).andExpect(status().isNotFound());

        // الوردية سليمة عند صاحبها
        JsonNode stored = body(getJson("/api/v1/shifts/" + shiftA, tokenA).andExpect(status().isOk()));
        assertThat(stored.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(stored.path("version").asLong()).isEqualTo(shiftAVersion);
    }

    @Test
    void cannotCreateShiftsWithAnotherTenantsReferences() throws Exception {
        postJson("/api/v1/shifts", shiftBody(siteA, driverB, equipmentB, today().minusDays(2), "MORNING"), tokenB)
                .andExpect(status().isNotFound());
        postJson("/api/v1/shifts", shiftBody(siteB, driverA, equipmentB, today().minusDays(2), "MORNING"), tokenB)
                .andExpect(status().isNotFound());
        postJson("/api/v1/shifts", shiftBody(siteB, driverB, equipmentA, today().minusDays(2), "MORNING"), tokenB)
                .andExpect(status().isNotFound());

        getJson("/api/v1/shifts/defaults?driverId=" + driverA + "&equipmentId=" + equipmentB
                + "&shiftType=MORNING", tokenB).andExpect(status().isNotFound());
        getJson("/api/v1/shifts/defaults?driverId=" + driverB + "&equipmentId=" + equipmentA
                + "&shiftType=MORNING", tokenB).andExpect(status().isNotFound());
    }

    @Test
    void sameSlotInAnotherTenantIsNotAConflict() throws Exception {
        // نفس التاريخ والنوع عند المقاول (ب) لا يتعارض مع وردية المقاول (أ)
        createShift(tokenB, shiftBody(siteB, driverB, equipmentB, today().minusDays(1), "MORNING"));
    }
}
