package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.contractor.app.common.error.ApiError;
import com.contractor.app.common.error.GlobalExceptionHandler;
import com.contractor.app.shift.domain.Shift;
import com.contractor.app.shift.domain.ShiftType;
import com.contractor.app.shift.infra.ShiftRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.junit.jupiter.api.Test;

class ShiftIntegrationTest extends AbstractShiftIntegrationTest {

    @Autowired private ShiftRepository shiftRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private GlobalExceptionHandler globalExceptionHandler;

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

    // ---------------------------------------------------------------- start

    @Test
    void startCreatesARunningShiftWithSnapshots() throws Exception {
        JsonNode shift = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        assertThat(shift.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(shift.path("finishedAt").isNull()).isTrue();
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
    void dateDefaultsToTodayAndFutureDatesAreRejected() throws Exception {
        JsonNode shift = startShift(token, shiftBody(siteId, driverId, equipmentId, null, "MORNING"));
        assertThat(shift.path("shiftDate").asText()).isEqualTo(today().toString());

        postJson("/api/v1/shifts", shiftBody(siteId, driverId, equipmentId, today().plusDays(5), "EVENING"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void wageFollowsTheShiftType() throws Exception {
        JsonNode evening = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "EVENING"));
        assertThat(evening.path("driverWage").decimalValue()).isEqualByComparingTo("175.00");
        finishShift(token, evening);

        JsonNode night = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "NIGHT"));
        assertThat(night.path("driverWage").decimalValue()).isEqualByComparingTo("200.00");
    }

    @Test
    void explicitPriceAndWageOverrideTheDefaults() throws Exception {
        Map<String, Object> body = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        body.put("price", new BigDecimal("1500"));
        body.put("driverWage", new BigDecimal("180.50"));

        JsonNode shift = startShift(token, body);

        assertThat(shift.path("price").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(shift.path("driverWage").decimalValue()).isEqualByComparingTo("180.50");
    }

    @Test
    void activeDriverAndEquipmentCannotBeUsedByTwoRunningShifts() throws Exception {
        Map<String, Object> firstRequest = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        JsonNode first = startShift(token, firstRequest);

        // منع التكرار لا يعتمد على فحص Java فقط؛ قيد MySQL يحمي أيضًا عند التزامن بين instances.
        postJson("/api/v1/shifts",
                shiftBody(siteId, driverId, equipmentId, day(2), "EVENING"), token)
                .andExpect(status().isConflict());

        finishShift(token, first);
        startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
    }

    @Test
    void databaseUniqueIndexViolationMapsToActiveShiftConflict() throws Exception {
        JsonNode first = startShift(token,
                shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM shifts WHERE id = ?", Long.class, first.path("id").asLong());

        // Different equipment isolates the driver constraint as the source of the failure.
        long secondEquipmentId = createEquipment(token, "EX-02", typeId, "600", "1300");
        Shift duplicate = new Shift(tenantId, day(2), ShiftType.EVENING, siteId, driverId, secondEquipmentId);
        duplicate.setPrice(new BigDecimal("1300.00"));
        duplicate.setDriverWage(new BigDecimal("175.00"));
        duplicate.setEquipmentOperatingCost(new BigDecimal("600.00"));

        DataIntegrityViolationException violation = null;
        try {
            // Bypass ShiftService pre-checks so MySQL's unique index is exercised directly.
            shiftRepository.saveAndFlush(duplicate);
        } catch (DataIntegrityViolationException ex) {
            violation = ex;
        }

        assertThat(violation).as("MySQL must reject a second active shift for the same tenant and driver")
                .isNotNull();
        String causeMessage = violation.getMostSpecificCause() == null
                ? ""
                : String.valueOf(violation.getMostSpecificCause().getMessage());
        assertThat(causeMessage).contains("uq_shifts_one_active_driver");

        org.springframework.http.ResponseEntity<Object> response = globalExceptionHandler.handleDataIntegrity(
                violation, new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/shifts")));
        assertThat(response.getStatusCodeValue()).isEqualTo(409);
        assertThat(response.getBody()).isInstanceOf(ApiError.class);
        assertThat(((ApiError) response.getBody()).getCode()).isEqualTo("ACTIVE_SHIFT_CONFLICT");
    }

    @Test
    void concurrentStartsForSameResourcesAllowOnlyOneRequest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<org.springframework.test.web.servlet.MvcResult> request = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent request.");
            }
            return postJson("/api/v1/shifts",
                    shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"), token).andReturn();
        };

        try {
            Future<org.springframework.test.web.servlet.MvcResult> first = executor.submit(request);
            Future<org.springframework.test.web.servlet.MvcResult> second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            org.springframework.test.web.servlet.MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            org.springframework.test.web.servlet.MvcResult secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(java.util.Arrays.asList(firstResult.getResponse().getStatus(),
                    secondResult.getResponse().getStatus())).containsExactlyInAnyOrder(201, 409);

            org.springframework.test.web.servlet.MvcResult conflictResult =
                    firstResult.getResponse().getStatus() == 409 ? firstResult : secondResult;
            assertThat(conflictResult.getResponse().getContentAsString())
                    .contains("\"code\":\"ACTIVE_SHIFT_CONFLICT\"");
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
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

    // ---------------------------------------------------------------- finish

    @Test
    void finishChangesOnlyTheIndicator() throws Exception {
        JsonNode running = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        JsonNode done = finishShift(token, running);

        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.path("finishedAt").isNull()).isFalse();
        assertThat(done.path("price").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(done.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(done.path("version").asLong()).isGreaterThan(running.path("version").asLong());

        // لا إنهاء ثانٍ، ولا إنهاء بنسخة قديمة أو بدون نسخة
        postJson("/api/v1/shifts/" + running.path("id").asLong() + "/finish", versionBody(done.path("version").asLong()),
                token).andExpect(status().isConflict());
        JsonNode other = startShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING"));
        postJson("/api/v1/shifts/" + other.path("id").asLong() + "/finish", versionBody(999), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        postJson("/api/v1/shifts/" + other.path("id").asLong() + "/finish", new HashMap<String, Object>(), token)
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- snapshots

    @Test
    void oldShiftsAreNotRepricedWhenMasterDataChanges() throws Exception {
        JsonNode shift = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING")));
        long shiftId = shift.path("id").asLong();

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

        JsonNode stored = body(getJson("/api/v1/shifts/" + shiftId, token));
        assertThat(stored.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(stored.path("price").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(stored.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("500.00");

        // تعديل ملاحظة فقط (بدون سعر أو أجر) لا يعيد التسعير
        Map<String, Object> edit = shiftBody(siteId, driverId, equipmentId, day(2), "MORNING");
        edit.put("notes", "only a note");
        edit.put("version", stored.path("version").asLong());
        JsonNode edited = body(putJson("/api/v1/shifts/" + shiftId, edit, token).andExpect(status().isOk()));
        assertThat(edited.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(edited.path("price").decimalValue()).isEqualByComparingTo("1200.00");

        // الوردية الجديدة تأخذ القيم الجديدة
        JsonNode fresh = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        assertThat(fresh.path("driverWage").decimalValue()).isEqualByComparingTo("999.00");
        assertThat(fresh.path("price").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(fresh.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("900.00");
    }

    // ---------------------------------------------------------------- availability rules

    @Test
    void unavailableDriverOrEquipmentCannotBeScheduledOnAnyDate() throws Exception {
        // سبب عدم التوفر إلزامي
        setDriverAvailability(token, driverId, "UNAVAILABLE", null).andExpect(status().isBadRequest());

        setDriverAvailability(token, driverId, "UNAVAILABLE", "sick leave").andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableReason").value("sick leave"));
        postJson("/api/v1/shifts", shiftBody(siteId, driverId, equipmentId, today(), "MORNING"), token)
                .andExpect(status().isBadRequest());
        postJson("/api/v1/shifts", shiftBody(siteId, driverId, equipmentId, day(30), "MORNING"), token)
                .andExpect(status().isBadRequest());

        setDriverAvailability(token, driverId, "AVAILABLE", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.unavailableReason").doesNotExist());

        setEquipmentStatus(token, equipmentId, "MAINTENANCE").andExpect(status().isOk());
        postJson("/api/v1/shifts", shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"), token)
                .andExpect(status().isBadRequest());
        setEquipmentStatus(token, equipmentId, "OUT_OF_SERVICE").andExpect(status().isOk());
        postJson("/api/v1/shifts", shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"), token)
                .andExpect(status().isBadRequest());

        setEquipmentStatus(token, equipmentId, "AVAILABLE").andExpect(status().isOk());
        startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
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
    void statusChangesAreBlockedWhileAShiftIsRunning() throws Exception {
        JsonNode running = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        setDriverAvailability(token, driverId, "UNAVAILABLE", "leave").andExpect(status().isConflict());
        setEquipmentStatus(token, equipmentId, "MAINTENANCE").andExpect(status().isConflict());
        deleteJson("/api/v1/drivers/" + driverId, token).andExpect(status().isConflict());
        deleteJson("/api/v1/equipment/" + equipmentId, token).andExpect(status().isConflict());

        finishShift(token, running);

        setDriverAvailability(token, driverId, "UNAVAILABLE", "leave").andExpect(status().isOk());
        setEquipmentStatus(token, equipmentId, "MAINTENANCE").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- live lists

    @Test
    void liveListsReflectRunningShifts() throws Exception {
        long secondDriver = createDriver(token, "Driver Two", "1", "1", "1");
        long thirdDriver = createDriver(token, "Driver Three", "1", "1", "1");
        long secondEquipment = createEquipment(token, "EX-02", typeId, "1", "1");
        setDriverAvailability(token, secondDriver, "UNAVAILABLE", "sick").andExpect(status().isOk());
        setEquipmentStatus(token, secondEquipment, "MAINTENANCE").andExpect(status().isOk());

        JsonNode eqBefore = body(getJson("/api/v1/live/equipment", token).andExpect(status().isOk()));
        assertThat(findBy(eqBefore, "code", "EX-01").path("liveStatus").asText()).isEqualTo("AVAILABLE");
        assertThat(findBy(eqBefore, "code", "EX-02").path("liveStatus").asText()).isEqualTo("MAINTENANCE");

        JsonNode running = startShift(token, shiftBody(siteId, driverId, equipmentId, day(3), "MORNING"));

        JsonNode eq = body(getJson("/api/v1/live/equipment", token));
        JsonNode working = findBy(eq, "code", "EX-01");
        assertThat(working.path("liveStatus").asText()).isEqualTo("WORKING");
        assertThat(working.path("driverName").asText()).isEqualTo("Driver One");
        assertThat(working.path("siteName").asText()).isEqualTo("Main Site");
        assertThat(working.path("runningShiftDate").asText()).isEqualTo(day(3).toString());
        assertThat(working.path("runningShiftsCount").asInt()).isEqualTo(1);
        assertThat(working.path("currentShiftId").asLong()).isEqualTo(running.path("id").asLong());

        // قائمة الاختيار (onlyFree): لا شغالة ولا في الصيانة
        JsonNode freeEquipment = body(getJson("/api/v1/live/equipment?onlyFree=true", token));
        assertThat(freeEquipment.size()).isZero();

        JsonNode drivers = body(getJson("/api/v1/live/drivers", token));
        assertThat(findBy(drivers, "name", "Driver One").path("liveStatus").asText()).isEqualTo("WORKING");
        assertThat(findBy(drivers, "name", "Driver One").path("equipmentCode").asText()).isEqualTo("EX-01");
        assertThat(findBy(drivers, "name", "Driver Two").path("liveStatus").asText()).isEqualTo("UNAVAILABLE");
        assertThat(findBy(drivers, "name", "Driver Two").path("unavailableReason").asText()).isEqualTo("sick");
        assertThat(findBy(drivers, "name", "Driver Three").path("liveStatus").asText()).isEqualTo("AVAILABLE");

        JsonNode freeDrivers = body(getJson("/api/v1/live/drivers?onlyFree=true", token));
        assertThat(freeDrivers.size()).isEqualTo(1);
        assertThat(freeDrivers.get(0).path("id").asLong()).isEqualTo(thirdDriver);

        // قائمة الورديات الشغالة
        JsonNode list = body(getJson("/api/v1/shifts/running", token).andExpect(status().isOk()));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("id").asLong()).isEqualTo(running.path("id").asLong());

        finishShift(token, running);

        JsonNode after = body(getJson("/api/v1/live/equipment", token));
        assertThat(findBy(after, "code", "EX-01").path("liveStatus").asText()).isEqualTo("AVAILABLE");
        assertThat(body(getJson("/api/v1/shifts/running", token)).size()).isZero();
    }

    // ---------------------------------------------------------------- update & audit

    @Test
    void updatesAreAuditedAndVersionChecked() throws Exception {
        JsonNode shift = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
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
        JsonNode priceChange = null;
        boolean created = false;
        boolean wageChanged = false;
        for (JsonNode row : history) {
            if ("UPDATED".equals(row.path("action").asText()) && "price".equals(row.path("fieldName").asText())) {
                priceChange = row;
            }
            if ("CREATED".equals(row.path("action").asText())) {
                created = true;
            }
            if ("driverWage".equals(row.path("fieldName").asText())) {
                wageChanged = true;
            }
        }
        assertThat(priceChange).isNotNull();
        assertThat(priceChange.path("oldValue").asText()).isEqualTo("1200.00");
        assertThat(priceChange.path("newValue").asText()).isEqualTo("1300.00");
        assertThat(priceChange.path("userName").asText()).isNotBlank();
        assertThat(created).isTrue();
        assertThat(wageChanged).isFalse(); // الأجر لم يتغير فلا يظهر

        putJson("/api/v1/shifts/" + id, edit, token).andExpect(status().isConflict()); // نسخة قديمة
        edit.remove("version");
        putJson("/api/v1/shifts/" + id, edit, token).andExpect(status().isBadRequest()); // بدون نسخة
        Map<String, Object> noDate = shiftBody(siteId, driverId, equipmentId, null, "MORNING");
        noDate.put("version", updated.path("version").asLong());
        putJson("/api/v1/shifts/" + id, noDate, token).andExpect(status().isBadRequest()); // بدون تاريخ
    }

    // ---------------------------------------------------------------- cancel & reinstate

    @Test
    void completedShiftCanBeCancelledAndReinstated() throws Exception {
        JsonNode done = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING")));
        long id = done.path("id").asLong();

        Map<String, Object> noReason = versionBody(done.path("version").asLong());
        postJson("/api/v1/shifts/" + id + "/cancel", noReason, token).andExpect(status().isBadRequest());

        Map<String, Object> cancel = versionBody(done.path("version").asLong());
        cancel.put("reason", "work did not happen");
        JsonNode cancelled = body(postJson("/api/v1/shifts/" + id + "/cancel", cancel, token)
                .andExpect(status().isOk()));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("cancelReason").asText()).isEqualTo("work did not happen");

        // لا تعديل على الملغاة ولا إلغاء ثانٍ
        Map<String, Object> edit = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        edit.put("version", cancelled.path("version").asLong());
        putJson("/api/v1/shifts/" + id, edit, token).andExpect(status().isConflict());
        cancel.put("version", cancelled.path("version").asLong());
        postJson("/api/v1/shifts/" + id + "/cancel", cancel, token).andExpect(status().isConflict());

        postJson("/api/v1/shifts/" + id + "/reinstate", versionBody(cancelled.path("version").asLong()), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.cancelReason").doesNotExist());

        JsonNode history = body(getJson("/api/v1/shifts/" + id + "/history", token));
        assertThat(findBy(history, "action", "CANCELLED")).isNotNull();
        assertThat(findBy(history, "action", "REINSTATED")).isNotNull();
        assertThat(findBy(history, "action", "FINISHED")).isNotNull();
    }

    @Test
    void shiftCancelledWhileRunningCannotBeReinstated() throws Exception {
        JsonNode running = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        Map<String, Object> cancel = versionBody(running.path("version").asLong());
        cancel.put("reason", "started by mistake");
        JsonNode cancelled = body(postJson("/api/v1/shifts/" + running.path("id").asLong() + "/cancel", cancel,
                token).andExpect(status().isOk()));

        postJson("/api/v1/shifts/" + running.path("id").asLong() + "/reinstate",
                versionBody(cancelled.path("version").asLong()), token).andExpect(status().isConflict());

        // الملغاة لا تحجز السائق: يمكن تغيير حالته الآن
        setDriverAvailability(token, driverId, "UNAVAILABLE", "leave").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- list

    @Test
    void listIsFilteredPagedAndNewestFirst() throws Exception {
        JsonNode first = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        finishShift(token, first);
        JsonNode second = startShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "EVENING"));
        finishShift(token, second);
        startShift(token, shiftBody(siteId, driverId, equipmentId, day(3), "NIGHT"));

        JsonNode all = body(getJson("/api/v1/shifts", token).andExpect(status().isOk()));
        assertThat(all.path("totalElements").asLong()).isEqualTo(3);
        assertThat(all.path("items").get(0).path("shiftDate").asText()).isEqualTo(day(1).toString());
        assertThat(all.path("items").get(2).path("shiftDate").asText()).isEqualTo(day(3).toString());

        // Uniqueness rule: only the third shift may remain IN_PROGRESS.
        assertThat(body(getJson("/api/v1/shifts?status=IN_PROGRESS", token)).path("totalElements").asLong())
                .isEqualTo(1);
        assertThat(body(getJson("/api/v1/shifts?status=COMPLETED", token)).path("totalElements").asLong())
                .isEqualTo(2);
        assertThat(body(getJson("/api/v1/shifts?status=CANCELLED", token)).path("totalElements").asLong()).isZero();
        assertThat(body(getJson("/api/v1/shifts?type=NIGHT", token)).path("totalElements").asLong()).isEqualTo(1);
        assertThat(body(getJson("/api/v1/shifts?from=" + day(2) + "&to=" + day(1), token))
                .path("totalElements").asLong()).isEqualTo(2);
        assertThat(body(getJson("/api/v1/shifts?size=2&page=1", token)).path("items").size()).isEqualTo(1);

        long otherDriver = createDriver(token, "Driver Two", "1", "1", "1");
        assertThat(body(getJson("/api/v1/shifts?driverId=" + otherDriver, token)).path("totalElements").asLong())
                .isZero();

        getJson("/api/v1/shifts?from=" + day(1) + "&to=" + day(3), token).andExpect(status().isBadRequest());
    }
}
