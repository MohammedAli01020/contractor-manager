package com.contractor.app.shift;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** أدوات مشتركة لاختبارات الورديات: إنشاء موقع/سائق/نوع/معدة ووردية بسرعة. */
abstract class AbstractShiftIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    protected static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    protected long createSite(String token, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        return body(postJson("/api/v1/sites", body, token).andExpect(status().isCreated())).path("id").asLong();
    }

    protected long createDriver(String token, String name, String morning, String evening, String night)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("workerType", "DRIVER");
        body.put("morningRate", new BigDecimal(morning));
        body.put("eveningRate", new BigDecimal(evening));
        body.put("nightRate", new BigDecimal(night));
        return body(postJson("/api/v1/drivers", body, token).andExpect(status().isCreated())).path("id").asLong();
    }

    protected long createType(String token, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        return body(postJson("/api/v1/equipment-types", body, token).andExpect(status().isCreated()))
                .path("id").asLong();
    }

    protected long createEquipment(String token, String code, long typeId, String operatingCost,
                                   String defaultPrice) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("equipmentTypeId", typeId);
        body.put("operatingCostPerShift", new BigDecimal(operatingCost));
        body.put("defaultShiftPrice", new BigDecimal(defaultPrice));
        return body(postJson("/api/v1/equipment", body, token).andExpect(status().isCreated()))
                .path("id").asLong();
    }

    protected Map<String, Object> shiftBody(long siteId, long driverId, long equipmentId, LocalDate date,
                                            String type) {
        Map<String, Object> body = new HashMap<>();
        body.put("shiftType", type);
        body.put("siteId", siteId);
        body.put("driverId", driverId);
        body.put("equipmentId", equipmentId);
        return body;
    }

    protected JsonNode createShift(String token, Map<String, Object> body) throws Exception {
        return body(postJson("/api/v1/shifts/start", body, token).andExpect(status().isCreated()));
    }
}
