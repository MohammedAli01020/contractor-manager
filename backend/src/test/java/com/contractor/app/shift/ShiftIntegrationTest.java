package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    private LocalDate day(int daysAgo) {
        return today().minusDays(daysAgo);
    }

    @Test
    void createTakesDefaultsAndSnapshotsThem() throws Exception {
        JsonNode shift = createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        assertThat(shift.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(shift.path("price").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(shift.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(shift.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(shift.path("priceAfterWage").decimalValue()).isEqualByComparingTo("1050.00");
        assertThat(shift.path("siteName").asText()).isEqualTo("Main Site");
        assertThat(shift.path("driverName").asText()).isEqualTo("Driver One");
        assertThat(shift.path("equipmentCode").asText()).isEqualTo("EX-01");
        assertThat(shift.path("shiftDate").asText()).isEqualTo(day(1).toString());
    }

    @Test
    void wageFollowsTheShiftType() throws Exception {
        JsonNode evening = createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "EVENING"));
        JsonNode night = createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "NIGHT"));

        assertThat(evening.path("driverWage").decimalValue()).isEqualByComparingTo("175.00");
        assertThat(night.path("driverWage").decimalValue()).isEqualByComparingTo("200.00");
    }

    @Test
    void explicitPriceAndWageOverrideTheDefaults() throws Exception {
        Map<String, Object> body = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        body.put("price", new BigDecimal("1500"));
        body.put("driverWage", new BigDecimal("180.50"));

        JsonNode shift = createShift(token, body);

        assertThat(shift.path("price").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(shift.path("driverWage").decimalValue()).isEqualByComparingTo("180.50");
    }

    @Test
    void oldShiftsAreNotRepricedWhenMasterDataChanges() throws Exception {
        JsonNode shift = createShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING"));
        long shiftId = shift.path("id").asLong();

        // رفع أجر السائق وسعر المعدة وتكلفتها
        JsonNode driver = body(getJson("/api/v1/drivers/" + driverId, token));
        Map<String, Object> driverUpdate = new HashMap<>();
        driverUpdate.put("name", "Driver One");
        driverUpdate.put("workerType", "DRIVER");
        driverUpdate.put("morningRate", new BigDecimal("999"));
        driverUpdate.put("eveningRate", new BigDecimal("175"));
        driverUpdate.put("nightRate", new BigDecimal("200"));
        driverUpdate.put("version", driver.path("version").asLong());
        putJson("/api/v1/drivers/" + driverId, driverUpdate, token).andExpect(status().isOk());

        JsonNode equipment = body(getJson("/api/v1/equipment/" + equipmentId, token));
        Map<String, Object> equipmentUpdate = new HashMap<>();
        equipmentUpdate.put("code", "EX-01");
        equipmentUpdate.put("equipmentTypeId", typeId);
        equipmentUpdate.put("operatingCostPerShift", new BigDecimal("900"));
        equipmentUpdate.put("defaultShiftPrice", new BigDecimal("5000"));
        equipmentUpdate.put("version", equipment.path("version").asLong());
        putJson("/api/v1/equipment/" + equipmentId, equipmentUpdate, token).andExpect(status().isOk());

        // الوردية القديمة كما هي
        JsonNode stored = body(getJson("/api/v1/shifts/" + shiftId, token));
        assertThat(stored.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(stored.path("price").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(stored.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("500.00");

        // وتعديل ملاحظة فقط (بدون إرسال سعر أو أجر) لا يعيد التسعير
        Map<String, Object> edit = shiftBody(siteId, driverId, equipmentId, day(2), "MORNING");
        edit.put("notes", "only a note");
        edit.put("version", stored.path("version").asLong());
        JsonNode edited = body(putJson("/api/v1/shifts/" + shiftId, edit, token).andExpect(status().isOk()));
        assertThat(edited.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(edited.path("price").decimalValue()).isEqualByComparingTo("1200.00");

        // أما الوردية الجديدة فتأخذ القيم الجديدة
        JsonNode fresh = createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        assertThat(fresh.path("driverWage").decimalValue()).isEqualByComparingTo("999.00");
        assertThat(fresh.path("price").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(fresh.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("900.00");
    }

    @Test
    void sameEquipmentOrDriverCannotWorkTheSameSlotTwice() throws Exception {
        createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        long otherDriver = createDriver(token, "Driver Two", "100", "100", "100");
        long otherEquipment = createEquipment(token, "EX-02", typeId, "400", "1000");

        postJson("/api/v1/shifts", shiftBody(siteId, otherDriver, equipmentId, day(1), "MORNING"), token)
                .andExpect(status().isConflict());
        postJson("/api/v1/shifts", shiftBody(siteId, driverId, otherEquipment, day(1), "MORNING"), token)
                .andExpect(status().isConflict());

        // وردية أخرى لنفس الشخص والمعدة مسموحة (مسائية) كما أن تاريخًا آخر مسموح
        createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "EVENING"));
        createShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING"));
        // وسائق ومعدة مختلفان في نفس الموعد مسموح
        createShift(token, shiftBody(siteId, otherDriver, otherEquipment, day(1), "MORNING"));
    }

    @Test
    void futureDatesAreRejected() throws Exception {
        postJson("/api/v1/shifts", shiftBody(siteId, driverId, equipmentId, today().plusDays(5), "MORNING"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void inactiveReferencesAreRejected() throws Exception {
        deleteJson("/api/v1/sites/" + siteId, token).andExpect(status().isNoContent());
        postJson("/api/v1/shifts", shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"), token)
                .andExpect(status().isBadRequest());

        long activeSite = createSite(token, "Second Site");
        deleteJson("/api/v1/drivers/" + driverId, token).andExpect(status().isNoContent());
        postJson("/api/v1/shifts", shiftBody(activeSite, driverId, equipmentId, day(1), "MORNING"), token)
                .andExpect(status().isBadRequest());
    }

    @Test
    void hoursAndQuantityAreValidatedAndStored() throws Exception {
        Map<String, Object> tooManyHours = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        tooManyHours.put("hours", new BigDecimal("25"));
        postJson("/api/v1/shifts", tooManyHours, token).andExpect(status().isBadRequest());

        Map<String, Object> negativeQuantity = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        negativeQuantity.put("quantity", new BigDecimal("-1"));
        postJson("/api/v1/shifts", negativeQuantity, token).andExpect(status().isBadRequest());

        Map<String, Object> valid = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        valid.put("hours", new BigDecimal("8.5"));
        valid.put("quantity", new BigDecimal("120.5"));
        valid.put("quantityUnit", "m3");
        JsonNode shift = createShift(token, valid);
        assertThat(shift.path("hours").decimalValue()).isEqualByComparingTo("8.50");
        assertThat(shift.path("quantity").decimalValue()).isEqualByComparingTo("120.50");
        assertThat(shift.path("quantityUnit").asText()).isEqualTo("m3");
    }

    @Test
    void updatesAreAuditedAndVersionChecked() throws Exception {
        JsonNode shift = createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        long id = shift.path("id").asLong();
        long version = shift.path("version").asLong();

        Map<String, Object> edit = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        edit.put("price", new BigDecimal("1300"));
        edit.put("notes", "second thoughts");
        edit.put("version", version);
        JsonNode updated = body(putJson("/api/v1/shifts/" + id, edit, token).andExpect(status().isOk()));
        assertThat(updated.path("price").decimalValue()).isEqualByComparingTo("1300.00");
        assertThat(updated.path("version").asLong()).isGreaterThan(version);

        JsonNode history = body(getJson("/api/v1/shifts/" + id + "/history", token).andExpect(status().isOk()));
        JsonNode priceChange = findHistory(history, "UPDATED", "price");
        assertThat(priceChange).isNotNull();
        assertThat(priceChange.path("oldValue").asText()).isEqualTo("1200.00");
        assertThat(priceChange.path("newValue").asText()).isEqualTo("1300.00");
        assertThat(priceChange.path("userName").asText()).isNotBlank();
        assertThat(findHistory(history, "UPDATED", "notes")).isNotNull();
        assertThat(findHistory(history, "CREATED", null)).isNotNull();
        // لم يتغير الأجر فلا يظهر في السجل
        assertThat(findHistory(history, "UPDATED", "driverWage")).isNull();

        // نسخة قديمة -> 409 ، وبدون نسخة -> 400
        putJson("/api/v1/shifts/" + id, edit, token).andExpect(status().isConflict());
        edit.remove("version");
        putJson("/api/v1/shifts/" + id, edit, token).andExpect(status().isBadRequest());
    }

    @Test
    void cancelFreesTheSlotAndReinstateChecksIt() throws Exception {
        JsonNode first = createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        long firstId = first.path("id").asLong();

        Map<String, Object> cancel = new HashMap<>();
        cancel.put("reason", "rain stopped work");
        cancel.put("version", first.path("version").asLong());
        JsonNode cancelled = body(postJson("/api/v1/shifts/" + firstId + "/cancel", cancel, token)
                .andExpect(status().isOk()));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("cancelReason").asText()).isEqualTo("rain stopped work");

        // لا تعديل على الملغاة ولا إلغاء ثانٍ، والسبب مطلوب
        Map<String, Object> edit = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        edit.put("version", cancelled.path("version").asLong());
        putJson("/api/v1/shifts/" + firstId, edit, token).andExpect(status().isConflict());
        cancel.put("version", cancelled.path("version").asLong());
        postJson("/api/v1/shifts/" + firstId + "/cancel", cancel, token).andExpect(status().isConflict());
        Map<String, Object> noReason = new HashMap<>();
        noReason.put("version", 0);
        postJson("/api/v1/shifts/" + firstId + "/cancel", noReason, token).andExpect(status().isBadRequest());

        // الموعد تحرر: يمكن تسجيل وردية بديلة
        JsonNode second = createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        // وإعادة تفعيل الأولى مرفوضة لأن الموعد صار مشغولًا
        Map<String, Object> reinstate = new HashMap<>();
        reinstate.put("version", cancelled.path("version").asLong());
        postJson("/api/v1/shifts/" + firstId + "/reinstate", reinstate, token).andExpect(status().isConflict());

        // بعد إلغاء البديلة تصبح ممكنة
        Map<String, Object> cancelSecond = new HashMap<>();
        cancelSecond.put("reason", "duplicate entry");
        cancelSecond.put("version", second.path("version").asLong());
        postJson("/api/v1/shifts/" + second.path("id").asLong() + "/cancel", cancelSecond, token)
                .andExpect(status().isOk());
        postJson("/api/v1/shifts/" + firstId + "/reinstate", reinstate, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        JsonNode history = body(getJson("/api/v1/shifts/" + firstId + "/history", token));
        assertThat(findHistory(history, "CANCELLED", "status")).isNotNull();
        assertThat(findHistory(history, "REINSTATED", "status")).isNotNull();
    }

    @Test
    void listIsFilteredPagedAndNewestFirst() throws Exception {
        createShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        createShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING"));
        createShift(token, shiftBody(siteId, driverId, equipmentId, day(3), "MORNING"));

        JsonNode all = body(getJson("/api/v1/shifts", token).andExpect(status().isOk()));
        assertThat(all.path("totalElements").asLong()).isEqualTo(3);
        assertThat(all.path("items").get(0).path("shiftDate").asText()).isEqualTo(day(1).toString());
        assertThat(all.path("items").get(2).path("shiftDate").asText()).isEqualTo(day(3).toString());

        JsonNode range = body(getJson("/api/v1/shifts?from=" + day(2) + "&to=" + day(1), token));
        assertThat(range.path("totalElements").asLong()).isEqualTo(2);

        JsonNode page2 = body(getJson("/api/v1/shifts?size=2&page=1", token));
        assertThat(page2.path("items").size()).isEqualTo(1);

        long otherDriver = createDriver(token, "Driver Two", "1", "1", "1");
        assertThat(body(getJson("/api/v1/shifts?driverId=" + otherDriver, token)).path("totalElements").asLong())
                .isZero();
        assertThat(body(getJson("/api/v1/shifts?status=CANCELLED", token)).path("totalElements").asLong()).isZero();
        assertThat(body(getJson("/api/v1/shifts?type=NIGHT", token)).path("totalElements").asLong()).isZero();

        getJson("/api/v1/shifts?from=" + day(1) + "&to=" + day(3), token).andExpect(status().isBadRequest());
    }

    @Test
    void defaultsEndpointSuggestsWageAndPrice() throws Exception {
        getJson("/api/v1/shifts/defaults?driverId=" + driverId + "&equipmentId=" + equipmentId
                + "&shiftType=EVENING", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverWage").value(175.0))
                .andExpect(jsonPath("$.price").value(1200.0))
                .andExpect(jsonPath("$.equipmentOperatingCost").value(500.0));
    }

    private static JsonNode findHistory(JsonNode history, String action, String field) {
        for (JsonNode row : history) {
            boolean sameField = field == null ? row.path("fieldName").isNull() || row.path("fieldName").isMissingNode()
                    : field.equals(row.path("fieldName").asText());
            if (action.equals(row.path("action").asText()) && sameField) {
                return row;
            }
        }
        return null;
    }
}
