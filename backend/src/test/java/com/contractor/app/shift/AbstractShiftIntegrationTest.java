package com.contractor.app.shift;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.test.web.servlet.ResultActions;

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
        if (date != null) {
            body.put("shiftDate", date);
        }
        body.put("shiftType", type);
        body.put("siteId", siteId);
        body.put("driverId", driverId);
        body.put("equipmentId", equipmentId);
        return body;
    }

    /** "بدء الوردية": تُسجَّل شغالة. */
    protected JsonNode startShift(String token, Map<String, Object> body) throws Exception {
        return body(postJson("/api/v1/shifts", body, token).andExpect(status().isCreated()));
    }

    protected Map<String, Object> versionBody(long version) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        return body;
    }

    protected JsonNode finishShift(String token, JsonNode shift) throws Exception {
        return body(postJson("/api/v1/shifts/" + shift.path("id").asLong() + "/finish",
                versionBody(shift.path("version").asLong()), token).andExpect(status().isOk()));
    }

    /** يغيّر حالة السائق (يقرأ بياناته الحالية ثم يعدّل). */
    protected ResultActions setDriverAvailability(String token, long driverId, String availability, String reason)
            throws Exception {
        JsonNode d = body(getJson("/api/v1/drivers/" + driverId, token));
        Map<String, Object> body = new HashMap<>();
        body.put("name", d.path("name").asText());
        body.put("workerType", d.path("workerType").asText());
        body.put("morningRate", d.path("morningRate").decimalValue());
        body.put("eveningRate", d.path("eveningRate").decimalValue());
        body.put("nightRate", d.path("nightRate").decimalValue());
        body.put("availability", availability);
        if (reason != null) {
            body.put("unavailableReason", reason);
        }
        body.put("version", d.path("version").asLong());
        return putJson("/api/v1/drivers/" + driverId, body, token);
    }

    /** يغيّر الحالة التشغيلية للمعدة (يقرأ بياناتها الحالية ثم يعدّل). */
    protected ResultActions setEquipmentStatus(String token, long equipmentId, String statusValue)
            throws Exception {
        JsonNode e = body(getJson("/api/v1/equipment/" + equipmentId, token));
        Map<String, Object> body = new HashMap<>();
        body.put("code", e.path("code").asText());
        body.put("equipmentTypeId", e.path("equipmentTypeId").asLong());
        body.put("operatingCostPerShift", e.path("operatingCostPerShift").decimalValue());
        body.put("defaultShiftPrice", e.path("defaultShiftPrice").decimalValue());
        body.put("status", statusValue);
        body.put("version", e.path("version").asLong());
        return putJson("/api/v1/equipment/" + equipmentId, body, token);
    }

    protected static JsonNode findBy(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        return null;
    }
}
