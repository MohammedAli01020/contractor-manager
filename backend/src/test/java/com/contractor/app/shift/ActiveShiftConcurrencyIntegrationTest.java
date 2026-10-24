package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.Test;

/** Real-MySQL concurrency and trigger guards for active-shift resources. */
class ActiveShiftConcurrencyIntegrationTest extends AbstractShiftIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentStartAndDriverUnavailableTransitionPreserveInvariant() throws Exception {
        String token = registerTenant("Driver Race Co", uniqueName("owner")).path("accessToken").asText();
        long site = createSite(token, "Main");
        long driver = createDriver(token, "Driver", "150", "175", "200");
        long type = createType(token, "Excavator");
        long equipment = createEquipment(token, "EX-RACE-D", type, "500", "1200");

        JsonNode current = body(getJson("/api/v1/drivers/" + driver, token));
        Map<String, Object> update = new HashMap<>();
        update.put("name", current.path("name").asText());
        update.put("workerType", current.path("workerType").asText());
        update.put("morningRate", current.path("morningRate").decimalValue());
        update.put("eveningRate", current.path("eveningRate").decimalValue());
        update.put("nightRate", current.path("nightRate").decimalValue());
        update.put("availability", "UNAVAILABLE");
        update.put("unavailableReason", "maintenance");
        update.put("version", current.path("version").asLong());

        runRace(
                () -> postJson("/api/v1/shifts",
                        shiftBody(site, driver, equipment, LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).minusDays(1), "MORNING"), token).andReturn(),
                () -> putJson("/api/v1/drivers/" + driver, update, token).andReturn());

        String availability = jdbcTemplate.queryForObject(
                "SELECT availability FROM drivers WHERE id = ?", String.class, driver);
        int running = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shifts WHERE driver_id = ? AND status = 'IN_PROGRESS'", Integer.class, driver);
        assertThat(availability.equals("AVAILABLE") || running == 0)
                .as("a driver cannot be unavailable while an active shift references it").isTrue();
    }

    @Test
    void concurrentStartAndEquipmentMaintenanceTransitionPreserveInvariant() throws Exception {
        String token = registerTenant("Equipment Race Co", uniqueName("owner")).path("accessToken").asText();
        long site = createSite(token, "Main");
        long driver = createDriver(token, "Driver", "150", "175", "200");
        long type = createType(token, "Excavator");
        long equipment = createEquipment(token, "EX-RACE-E", type, "500", "1200");

        JsonNode current = body(getJson("/api/v1/equipment/" + equipment, token));
        Map<String, Object> update = new HashMap<>();
        update.put("code", current.path("code").asText());
        update.put("equipmentTypeId", current.path("equipmentTypeId").asLong());
        update.put("operatingCostPerShift", current.path("operatingCostPerShift").decimalValue());
        update.put("defaultShiftPrice", current.path("defaultShiftPrice").decimalValue());
        update.put("status", "MAINTENANCE");
        update.put("version", current.path("version").asLong());

        runRace(
                () -> postJson("/api/v1/shifts",
                        shiftBody(site, driver, equipment, LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).minusDays(1), "MORNING"), token).andReturn(),
                () -> putJson("/api/v1/equipment/" + equipment, update, token).andReturn());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM equipment WHERE id = ?", String.class, equipment);
        int running = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shifts WHERE equipment_id = ? AND status = 'IN_PROGRESS'", Integer.class, equipment);
        assertThat(status.equals("AVAILABLE") || running == 0)
                .as("equipment cannot leave service while an active shift references it").isTrue();
    }

    @Test
    void databaseTriggersRejectDirectUnavailableTransitionsButAllowUnrelatedEdits() throws Exception {
        String token = registerTenant("Trigger Co", uniqueName("owner")).path("accessToken").asText();
        long site = createSite(token, "Main");
        long driver = createDriver(token, "Driver", "150", "175", "200");
        long type = createType(token, "Excavator");
        long equipment = createEquipment(token, "EX-TRIGGER", type, "500", "1200");
        startShift(token, shiftBody(site, driver, equipment,
                LocalDate.now(java.time.ZoneId.of("Africa/Cairo")).minusDays(1), "MORNING"));

        // The database guard is intentionally tested outside DriverService/EquipmentService.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE drivers SET availability = 'UNAVAILABLE' WHERE id = ?", driver))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("ACTIVE_SHIFT_DRIVER_RUNNING");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE equipment SET status = 'MAINTENANCE' WHERE id = ?", equipment))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("ACTIVE_SHIFT_EQUIPMENT_RUNNING");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE drivers SET active = FALSE WHERE id = ?", driver))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("ACTIVE_SHIFT_DRIVER_RUNNING");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE equipment SET active = FALSE WHERE id = ?", equipment))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .hasMessageContaining("ACTIVE_SHIFT_EQUIPMENT_RUNNING");

        // Ordinary edits are deliberately not blocked during a running shift.
        assertThat(jdbcTemplate.update("UPDATE drivers SET phone = '01000000000' WHERE id = ?", driver)).isEqualTo(1);
        assertThat(jdbcTemplate.update("UPDATE equipment SET notes = 'checked' WHERE id = ?", equipment)).isEqualTo(1);
    }

    private void runRace(Callable<MvcResult> firstRequest, Callable<MvcResult> secondRequest) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<MvcResult> first = gated(firstRequest, ready, start);
        Callable<MvcResult> second = gated(secondRequest, ready, start);
        try {
            Future<MvcResult> a = executor.submit(first);
            Future<MvcResult> b = executor.submit(second);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult resultA = a.get(30, TimeUnit.SECONDS);
            MvcResult resultB = b.get(30, TimeUnit.SECONDS);
            java.util.List<Integer> statuses = Arrays.asList(
                    resultA.getResponse().getStatus(), resultB.getResponse().getStatus());
            assertThat(statuses)
                    .as("responses must be successful operations or expected business conflicts")
                    .allMatch(status -> status == 201 || status == 200 || status == 400 || status == 409);
            assertThat(statuses).as("at least one side of the race should make progress")
                    .anyMatch(status -> status == 201 || status == 200);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Callable<MvcResult> gated(Callable<MvcResult> request, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent start.");
            }
            return request.call();
        };
    }
}
