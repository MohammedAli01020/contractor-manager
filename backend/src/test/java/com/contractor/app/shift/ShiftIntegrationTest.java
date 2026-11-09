package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.common.error.ApiError;
import com.contractor.app.common.error.GlobalExceptionHandler;
import com.contractor.app.shift.domain.Shift;
import com.contractor.app.shift.domain.ShiftType;
import com.contractor.app.shift.infra.ShiftRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.ServletWebRequest;
import org.junit.jupiter.api.Test;

class ShiftIntegrationTest extends AbstractShiftIntegrationTest {

    @Autowired private ShiftRepository shiftRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private GlobalExceptionHandler globalExceptionHandler;
    @Autowired private DefaultChartOfAccountsService defaultAccounts;
    @Autowired private ChartAccountRepository chartAccounts;
    @Autowired private JournalPostingService journalPostingService;

    private String token;
    private long tenantId;
    private long actorUserId;
    private long siteId;
    private long driverId;
    private long typeId;
    private long equipmentId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode registered = registerTenant("Shift Co", uniqueName("owner"));
        token = registered.path("accessToken").asText();
        tenantId = registered.path("user").path("tenantId").asLong();
        actorUserId = registered.path("user").path("id").asLong();
        defaultAccounts.initializeForTenant(tenantId);
        siteId = createSite(token, "Main Site");
        driverId = createDriver(token, "Driver One", "150", "175", "200");
        typeId = createType(token, "Excavator");
        equipmentId = createEquipment(token, "EX-01", typeId, "500", "1200");
    }


    private void seedCashBalance(BigDecimal amount) {
        ChartAccount cash = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Default cash account was not created"));
        ChartAccount capital = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "3000")
                .orElseThrow(() -> new IllegalStateException("Default capital account was not created"));
        journalPostingService.post(tenantId, actorUserId, new JournalPostingRequest(
                "test-cash-opening:" + java.util.UUID.randomUUID(), "TEST_CASH_OPENING", null,
                today(), "Test fixture opening cash", null, java.util.Arrays.asList(
                new JournalLineRequest(cash.getId(), "Opening cash", amount, BigDecimal.ZERO, null, null),
                new JournalLineRequest(capital.getId(), "Opening capital", BigDecimal.ZERO, amount, null, null))));
    }

    private LocalDate day(int daysAgo) {
        return today().minusDays(daysAgo);
    }

    // ---------------------------------------------------------------- start

    private long createHourlyAgreement(String hourlyRate) throws Exception {
        Long memberId = jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ?", Long.class, driverId);
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long driverRoleId = 0L;
        for (JsonNode role : roles) {
            if ("DRIVER".equals(role.path("code").asText())) {
                driverRoleId = role.path("id").asLong();
                break;
            }
        }
        assertThat(driverRoleId).isPositive();

        Map<String, Object> assignmentBody = new HashMap<>();
        assignmentBody.put("roleId", driverRoleId);
        assignmentBody.put("siteId", siteId);
        assignmentBody.put("effectiveFrom", day(30).toString());
        JsonNode assignment = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignmentBody, token).andExpect(status().isCreated()));

        Map<String, Object> component = new HashMap<>();
        component.put("componentCode", "HOURLY_RATE");
        component.put("displayName", "Hourly wage");
        component.put("basis", "HOUR");
        component.put("rate", new BigDecimal(hourlyRate));
        component.put("unitCode", "HOUR");
        component.put("sortOrder", 0);
        Map<String, Object> agreementBody = new HashMap<>();
        agreementBody.put("workforceAssignmentId", assignment.path("id").asLong());
        agreementBody.put("effectiveFrom", day(30).toString());
        agreementBody.put("currencyCode", "EGP");
        agreementBody.put("components", java.util.Collections.singletonList(component));
        JsonNode agreement = body(postJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", agreementBody, token).andExpect(status().isCreated()));
        long agreementId = agreement.path("id").asLong();
        postJson("/api/v1/workforce/members/" + memberId + "/compensation-agreements/"
                + agreementId + "/approve", java.util.Collections.emptyMap(), token)
                .andExpect(status().isOk());
        return agreementId;
    }

    private long createQuantityAgreement(String unitRate, String unitCode) throws Exception {
        Long memberId = jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ?", Long.class, driverId);
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long driverRoleId = 0L;
        for (JsonNode role : roles) {
            if ("DRIVER".equals(role.path("code").asText())) {
                driverRoleId = role.path("id").asLong();
                break;
            }
        }
        assertThat(driverRoleId).isPositive();

        Map<String, Object> assignmentBody = new HashMap<>();
        assignmentBody.put("roleId", driverRoleId);
        assignmentBody.put("siteId", siteId);
        assignmentBody.put("effectiveFrom", day(30).toString());
        JsonNode assignment = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignmentBody, token).andExpect(status().isCreated()));

        Map<String, Object> component = new HashMap<>();
        component.put("componentCode", "QUANTITY_RATE");
        component.put("displayName", "Quantity wage");
        component.put("basis", "QUANTITY");
        component.put("rate", new BigDecimal(unitRate));
        component.put("unitCode", unitCode);
        component.put("sortOrder", 0);
        Map<String, Object> agreementBody = new HashMap<>();
        agreementBody.put("workforceAssignmentId", assignment.path("id").asLong());
        agreementBody.put("effectiveFrom", day(30).toString());
        agreementBody.put("currencyCode", "EGP");
        agreementBody.put("components", java.util.Collections.singletonList(component));
        JsonNode agreement = body(postJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", agreementBody, token).andExpect(status().isCreated()));
        long agreementId = agreement.path("id").asLong();
        postJson("/api/v1/workforce/members/" + memberId + "/compensation-agreements/"
                + agreementId + "/approve", java.util.Collections.emptyMap(), token)
                .andExpect(status().isOk());
        return agreementId;
    }

    @Test
    void quantityShiftRequiresMeasuredQuantityAndPostsCalculatedWageOnce() throws Exception {
        createQuantityAgreement("10.01", "TON");
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        assertThat(started.path("compensationBasis").asText()).isEqualTo("QUANTITY");
        assertThat(started.path("compensationUnitCode").asText()).isEqualTo("TON");
        assertThat(started.path("compensationRate").decimalValue()).isEqualByComparingTo("10.01");
        assertThat(started.path("actualQuantity").isNull()).isTrue();
        assertThat(started.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(started.path("priceAfterWage").isNull()).isTrue();

        long shiftId = started.path("id").asLong();
        postJson("/api/v1/shifts/" + shiftId + "/finish",
                versionBody(started.path("version").asLong()), token)
                .andExpect(status().isBadRequest());

        Map<String, Object> invalid = versionBody(started.path("version").asLong());
        invalid.put("actualQuantity", new BigDecimal("0"));
        postJson("/api/v1/shifts/" + shiftId + "/finish", invalid, token)
                .andExpect(status().isBadRequest());
        invalid.put("actualQuantity", new BigDecimal("-1"));
        postJson("/api/v1/shifts/" + shiftId + "/finish", invalid, token)
                .andExpect(status().isBadRequest());
        invalid.put("actualQuantity", new BigDecimal("1.2345"));
        postJson("/api/v1/shifts/" + shiftId + "/finish", invalid, token)
                .andExpect(status().isBadRequest());

        Map<String, Object> finishBody = versionBody(started.path("version").asLong());
        finishBody.put("actualQuantity", new BigDecimal("2.345"));
        JsonNode finished = body(postJson("/api/v1/shifts/" + shiftId + "/finish",
                finishBody, token).andExpect(status().isOk()));

        assertThat(finished.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.path("actualQuantity").decimalValue()).isEqualByComparingTo("2.345");
        assertThat(finished.path("compensationCalculatedAmount").decimalValue()).isEqualByComparingTo("23.47");
        assertThat(finished.path("driverWage").decimalValue()).isEqualByComparingTo("23.47");
        assertThat(finished.path("driverWageJournalEntryId").asLong()).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from driver_ledger_entries where tenant_id = ? and shift_id = ? and transaction_type = 'EARNING'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update shifts set actual_quantity = 3.000 where tenant_id = ? and id = ?",
                tenantId, shiftId)).hasMessageContaining("COMPLETED_SHIFT_IMMUTABLE");
        postJson("/api/v1/shifts/" + shiftId + "/finish", finishBody, token)
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from driver_ledger_entries where tenant_id = ? and shift_id = ? and transaction_type = 'EARNING'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
    }

    @Test
    void zeroRateQuantityShiftCompletesWithoutZeroValueFinancialEntries() throws Exception {
        createQuantityAgreement("0.00", "LOAD");
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        Map<String, Object> finishBody = versionBody(started.path("version").asLong());
        finishBody.put("actualQuantity", new BigDecimal("3.500"));
        JsonNode finished = body(postJson("/api/v1/shifts/" + started.path("id").asLong()
                + "/finish", finishBody, token).andExpect(status().isOk()));

        assertThat(finished.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(finished.path("driverWageJournalEntryId").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from driver_ledger_entries where tenant_id = ? and shift_id = ? and transaction_type in ('EARNING', 'ADVANCE_RECOVERY')",
                Integer.class, tenantId, started.path("id").asLong())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from journal_entries where tenant_id = ? and source_type = 'SHIFT_WAGE' and source_id = ?",
                Integer.class, tenantId, started.path("id").asLong())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from journal_entries where tenant_id = ? and source_type = 'DRIVER_ADVANCE_RECOVERY' and source_id = ?",
                Integer.class, tenantId, started.path("id").asLong())).isZero();
    }

    @Test
    void hourlyShiftRequiresMeasuredHoursAndCalculatesWageOnlyAtFinish() throws Exception {
        createHourlyAgreement("100.01");
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        assertThat(started.path("compensationBasis").asText()).isEqualTo("HOUR");
        assertThat(started.path("compensationRate").decimalValue()).isEqualByComparingTo("100.01");
        assertThat(started.path("actualHours").isNull()).isTrue();
        assertThat(started.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(started.path("priceAfterWage").isNull()).isTrue();

        postJson("/api/v1/shifts/" + started.path("id").asLong() + "/finish",
                versionBody(started.path("version").asLong()), token)
                .andExpect(status().isBadRequest());

        Map<String, Object> invalidHours = versionBody(started.path("version").asLong());
        invalidHours.put("actualHours", new BigDecimal("0"));
        postJson("/api/v1/shifts/" + started.path("id").asLong() + "/finish",
                invalidHours, token).andExpect(status().isBadRequest());
        invalidHours.put("actualHours", new BigDecimal("-1"));
        postJson("/api/v1/shifts/" + started.path("id").asLong() + "/finish",
                invalidHours, token).andExpect(status().isBadRequest());

        Map<String, Object> finishBody = versionBody(started.path("version").asLong());
        finishBody.put("actualHours", new BigDecimal("7.25"));
        JsonNode finished = body(postJson("/api/v1/shifts/" + started.path("id").asLong()
                + "/finish", finishBody, token).andExpect(status().isOk()));

        assertThat(finished.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.path("actualHours").decimalValue()).isEqualByComparingTo("7.25");
        assertThat(finished.path("compensationCalculatedAmount").decimalValue())
                .isEqualByComparingTo("725.07");
        assertThat(finished.path("driverWage").decimalValue()).isEqualByComparingTo("725.07");
        assertThat(finished.path("driverWageJournalEntryId").asLong()).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from driver_ledger_entries where tenant_id = ? and shift_id = ? and transaction_type = 'EARNING'",
                Integer.class, tenantId, started.path("id").asLong())).isEqualTo(1);
    }

    @Test
    void shiftsCanBeFilteredByCanonicalWorkforceMemberId() throws Exception {
        Long memberId = jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ?", Long.class, driverId);
        startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        JsonNode response = body(getJson("/api/v1/shifts?workforceMemberId=" + memberId, token)
                .andExpect(status().isOk()));

        assertThat(response.path("items").isArray()).isTrue();
        assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("workforceMemberId").asLong())
                .isEqualTo(memberId);
    }

    @Test
    void shiftCanBeStartedUsingCanonicalWorkforceMemberId() throws Exception {
        Long memberId = jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ?", Long.class, driverId);
        Map<String, Object> request = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        request.remove("driverId");
        request.put("workforceMemberId", memberId);

        JsonNode started = startShift(token, request);

        assertThat(started.path("workforceMemberId").asLong()).isEqualTo(memberId);
        assertThat(started.path("driverId").asLong()).isEqualTo(driverId);
    }

    @Test
    void workforceCandidatesExposeCanonicalMemberIdentityAlongsideLegacyProjection() throws Exception {
        Long memberId = jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ?", Long.class, driverId);
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long driverRoleId = 0L;
        for (JsonNode role : roles) {
            if ("DRIVER".equals(role.path("code").asText())) {
                driverRoleId = role.path("id").asLong();
                break;
            }
        }
        assertThat(driverRoleId).isPositive();

        Map<String, Object> assignmentBody = new HashMap<>();
        assignmentBody.put("roleId", driverRoleId);
        assignmentBody.put("siteId", siteId);
        assignmentBody.put("effectiveFrom", day(30).toString());
        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignmentBody, token).andExpect(status().isCreated());

        JsonNode candidates = body(getJson("/api/v1/live/workforce-candidates?shiftDate="
                + today() + "&siteId=" + siteId, token).andExpect(status().isOk()));
        JsonNode candidate = null;
        for (JsonNode item : candidates) {
            if (item.path("id").asLong() == driverId) {
                candidate = item;
                break;
            }
        }

        assertThat(candidate).isNotNull();
        assertThat(candidate.path("workforceMemberId").asLong()).isEqualTo(memberId);
    }

    @Test
    void startCreatesARunningShiftWithSnapshots() throws Exception {
        JsonNode shift = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        assertThat(shift.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(shift.path("finishedAt").isNull()).isTrue();
        assertThat(shift.path("startedAt").isNull()).isFalse();
        assertThat(shift.path("price").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(shift.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(shift.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(shift.path("priceAfterWage").decimalValue()).isEqualByComparingTo("1050.00");
        assertThat(shift.path("siteName").asText()).isEqualTo("Main Site");
        assertThat(shift.path("driverName").asText()).isEqualTo("Driver One");
        Long expectedWorkforceMemberId = jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ? and tenant_id = ?",
                Long.class, driverId, tenantId);
        assertThat(expectedWorkforceMemberId).isNotNull();
        assertThat(shift.path("workforceMemberId").asLong()).isEqualTo(expectedWorkforceMemberId);
        assertThat(jdbcTemplate.queryForObject(
                "select workforce_member_id from shifts where id = ? and tenant_id = ?",
                Long.class, shift.path("id").asLong(), tenantId)).isEqualTo(expectedWorkforceMemberId);
        assertThat(shift.path("equipmentCode").asText()).isEqualTo("EX-01");
        assertThat(shift.path("shiftDate").asText()).isEqualTo(day(1).toString());
    }

    @Test
    void databaseRejectsShiftDriverAndWorkforceIdentityMismatch() throws Exception {
        JsonNode shift = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        long secondDriverId = createDriver(token, "Driver Two", "160", "180", "210");
        Long otherMemberId = jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ? and tenant_id = ?",
                Long.class, secondDriverId, tenantId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update shifts set workforce_member_id = ? where id = ? and tenant_id = ?",
                otherMemberId, shift.path("id").asLong(), tenantId))
                .isInstanceOf(DataIntegrityViolationException.class);
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
        duplicate.setWorkforceMemberId(jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ? and tenant_id = ?",
                Long.class, driverId, tenantId));
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
    void databaseUniqueIndexViolationMapsEquipmentConflictToActiveShiftConflict() throws Exception {
        JsonNode first = startShift(token,
                shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM shifts WHERE id = ?", Long.class, first.path("id").asLong());

        long secondDriverId = createDriver(token, "Driver Two", "160", "180", "210");
        Shift duplicate = new Shift(tenantId, day(2), ShiftType.EVENING, siteId, secondDriverId, equipmentId);
        duplicate.setWorkforceMemberId(jdbcTemplate.queryForObject(
                "select workforce_member_id from drivers where id = ? and tenant_id = ?",
                Long.class, secondDriverId, tenantId));
        duplicate.setPrice(new BigDecimal("1200.00"));
        duplicate.setDriverWage(new BigDecimal("180.00"));
        duplicate.setEquipmentOperatingCost(new BigDecimal("500.00"));

        DataIntegrityViolationException violation = null;
        try {
            // Use a different driver so only the active-equipment unique index should reject this row.
            shiftRepository.saveAndFlush(duplicate);
        } catch (DataIntegrityViolationException ex) {
            violation = ex;
        }

        assertThat(violation).as("MySQL must reject a second active shift for the same tenant and equipment")
                .isNotNull();
        String causeMessage = violation.getMostSpecificCause() == null
                ? ""
                : String.valueOf(violation.getMostSpecificCause().getMessage());
        assertThat(causeMessage).contains("uq_shifts_one_active_equipment");

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
                + "&shiftType=EVENING&siteId=" + siteId + "&shiftDate=" + day(1), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverWage").value(175.0))
                .andExpect(jsonPath("$.wageSource").value("LEGACY_DRIVER_RATE"))
                .andExpect(jsonPath("$.price").value(1200.0))
                .andExpect(jsonPath("$.equipmentOperatingCost").value(500.0));
    }

    // ---------------------------------------------------------------- finish

    @Test
    void finishRecognizesDriverWageExactlyOnce() throws Exception {
        JsonNode running = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        JsonNode done = finishShift(token, running);

        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.path("finishedAt").isNull()).isFalse();
        assertThat(done.path("price").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(done.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(done.path("version").asLong()).isGreaterThan(running.path("version").asLong());
        assertThat(done.path("driverWageJournalEntryId").isNull()).isFalse();
        Long postedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id = (SELECT tenant_id FROM shifts WHERE id = ?) AND source_type = 'SHIFT_WAGE' AND source_id = ?",
                Long.class, running.path("id").asLong(), running.path("id").asLong());
        assertThat(postedCount).isEqualTo(1L);
        Long ledgerEarnings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id = (SELECT tenant_id FROM shifts WHERE id = ?) AND shift_id = ? AND transaction_type = 'EARNING'",
                Long.class, running.path("id").asLong(), running.path("id").asLong());
        assertThat(ledgerEarnings).isEqualTo(1L);
        BigDecimal debit = jdbcTemplate.queryForObject(
                "SELECT SUM(debit) FROM journal_lines WHERE journal_entry_id = ?", BigDecimal.class,
                done.path("driverWageJournalEntryId").asLong());
        BigDecimal credit = jdbcTemplate.queryForObject(
                "SELECT SUM(credit) FROM journal_lines WHERE journal_entry_id = ?", BigDecimal.class,
                done.path("driverWageJournalEntryId").asLong());
        assertThat(debit).isEqualByComparingTo("150.00");
        assertThat(credit).isEqualByComparingTo("150.00");
        Long wageExpenseAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM chart_of_accounts WHERE tenant_id = (SELECT tenant_id FROM shifts WHERE id = ?) AND account_code = '5000'",
                Long.class, running.path("id").asLong());
        Long driverPayableAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM chart_of_accounts WHERE tenant_id = (SELECT tenant_id FROM shifts WHERE id = ?) AND account_code = '2100'",
                Long.class, running.path("id").asLong());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT account_id FROM journal_lines WHERE journal_entry_id = ? AND debit > 0",
                Long.class, done.path("driverWageJournalEntryId").asLong())).isEqualTo(wageExpenseAccountId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT account_id FROM journal_lines WHERE journal_entry_id = ? AND credit > 0",
                Long.class, done.path("driverWageJournalEntryId").asLong())).isEqualTo(driverPayableAccountId);

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

    @Test
    void zeroEquipmentOperatingCostDoesNotPreventShiftCompletion() throws Exception {
        long zeroCostEquipmentId = createEquipment(token, "EX-ZERO", typeId, "0", "1200");
        JsonNode running = startShift(token,
                shiftBody(siteId, driverId, zeroCostEquipmentId, day(1), "MORNING"));

        assertThat(running.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(running.path("driverWage").decimalValue()).isGreaterThan(BigDecimal.ZERO);

        JsonNode completed = finishShift(token, running);

        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("equipmentOperatingCost").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(completed.path("driverWageJournalEntryId").isNull()).isFalse();
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

    @Test
    void shiftHistoryIsIsolatedByTenant() throws Exception {
        JsonNode shift = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        long shiftId = shift.path("id").asLong();

        String otherTenantToken = registerTenant("Other Shift Co", uniqueName("otherowner"))
                .path("accessToken").asText();

        // History must not disclose whether another tenant's shift exists.
        getJson("/api/v1/shifts/" + shiftId + "/history", otherTenantToken)
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- cancel & reinstate

    @Test
    void postedWageShiftRejectsChangesToWageSourceFields() throws Exception {
        JsonNode done = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING")));
        long id = done.path("id").asLong();
        long otherDriver = createDriver(token, "Driver Two", "160", "180", "210");
        long otherEquipment = createEquipment(token, "EX-02", typeId, "600", "1300");

        Map<String, Object> changedDate = shiftBody(siteId, driverId, equipmentId, day(2), "MORNING");
        changedDate.put("version", done.path("version").asLong());
        putJson("/api/v1/shifts/" + id, changedDate, token).andExpect(status().isConflict());

        Map<String, Object> changedType = shiftBody(siteId, driverId, equipmentId, day(1), "EVENING");
        changedType.put("version", done.path("version").asLong());
        putJson("/api/v1/shifts/" + id, changedType, token).andExpect(status().isConflict());

        Map<String, Object> changedDriver = shiftBody(siteId, otherDriver, equipmentId, day(1), "MORNING");
        changedDriver.put("version", done.path("version").asLong());
        putJson("/api/v1/shifts/" + id, changedDriver, token).andExpect(status().isConflict());

        Map<String, Object> changedEquipment = shiftBody(siteId, driverId, otherEquipment, day(1), "MORNING");
        changedEquipment.put("version", done.path("version").asLong());
        putJson("/api/v1/shifts/" + id, changedEquipment, token).andExpect(status().isConflict());

        Map<String, Object> changedWage = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        changedWage.put("driverWage", new BigDecimal("999.00"));
        changedWage.put("version", done.path("version").asLong());
        putJson("/api/v1/shifts/" + id, changedWage, token).andExpect(status().isConflict());

        JsonNode unchanged = body(getJson("/api/v1/shifts/" + id, token));
        assertThat(unchanged.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(unchanged.path("driverId").asLong()).isEqualTo(driverId);
        assertThat(unchanged.path("equipmentId").asLong()).isEqualTo(equipmentId);
        assertThat(unchanged.path("shiftDate").asText()).isEqualTo(day(1).toString());
        assertThat(unchanged.path("driverWage").decimalValue()).isEqualByComparingTo("150.00");
    }

    @Test
    void databaseRejectsDirectChangesToPostedShiftFinancialFields() throws Exception {
        JsonNode done = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING")));
        long id = done.path("id").asLong();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE shifts SET driver_wage = ? WHERE id = ?", new BigDecimal("999.00"), id))
                .hasMessageContaining("COMPLETED_SHIFT_IMMUTABLE");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE shifts SET status = 'CANCELLED' WHERE id = ?", id))
                .hasMessageContaining("COMPLETED_SHIFT_IMMUTABLE");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM shifts WHERE id = ?", id))
                .hasMessageContaining("POSTED_SHIFT_WAGE_CANNOT_BE_DELETED");

        Shift unchanged = shiftRepository.findById(id).orElseThrow(AssertionError::new);
        assertThat(unchanged.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(unchanged.getDriverWage()).isEqualByComparingTo("150.00");
    }

    @Test
    void approvedZeroWageShiftCanFinishWithoutWageOrAdvanceRecoveryPostings() throws Exception {
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ?", Long.class, driverId);
        String reference = "WF05-ZERO-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO compensation_agreements "
                + "(tenant_id, workforce_member_id, agreement_reference, status, effective_from, "
                + "currency_code, source, approved_by_user_id, approved_at, created_by_user_id, updated_by_user_id) "
                + "VALUES (?, ?, ?, 'APPROVED', ?, 'EGP', 'MANUAL', ?, ?, ?, ?)",
                tenantId, memberId, reference, day(10), actorUserId,
                LocalDateTime.now(java.time.ZoneOffset.UTC), actorUserId, actorUserId);
        Long agreementId = jdbcTemplate.queryForObject(
                "SELECT id FROM compensation_agreements WHERE tenant_id = ? AND agreement_reference = ?",
                Long.class, tenantId, reference);
        jdbcTemplate.update("INSERT INTO compensation_components "
                + "(tenant_id, compensation_agreement_id, component_code, display_name, basis, rate, shift_type, sort_order) "
                + "VALUES (?, ?, 'MORNING_BASE', 'وردية بدون أجر', 'SHIFT', 0.00, 'MORNING', 0)",
                tenantId, agreementId);

        seedCashBalance(new BigDecimal("1000.00"));
        Long cashId = jdbcTemplate.queryForObject(
                "SELECT id FROM chart_of_accounts WHERE tenant_id = ? AND account_code = '1000'",
                Long.class, tenantId);
        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "zero-wage-recoverable-" + java.util.UUID.randomUUID());
        advance.put("advanceDate", day(2).toString());
        advance.put("amount", new BigDecimal("200.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "سلفة قابلة للاسترداد");
        advance.put("advanceType", "RECOVERABLE");
        postJson("/api/v1/drivers/" + driverId + "/ledger/advances", advance, token)
                .andExpect(status().isCreated());

        JsonNode running = startShift(token,
                shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        long shiftId = running.path("id").asLong();
        assertThat(running.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(running.path("wageSource").asText()).isEqualTo("AGREEMENT");

        JsonNode completed = finishShift(token, running);

        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("finishedAt").isNull()).isFalse();
        assertThat(completed.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(completed.path("driverWageJournalEntryId").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id = ? AND source_type = 'SHIFT_WAGE' AND source_id = ?",
                Integer.class, tenantId, shiftId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id = ? AND posting_key = ?",
                Integer.class, tenantId, "shift-wage-" + shiftId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id = ? AND shift_id = ? "
                        + "AND transaction_type = 'EARNING'",
                Integer.class, tenantId, shiftId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id = ? AND shift_id = ? "
                        + "AND transaction_type = 'ADVANCE_RECOVERY'",
                Integer.class, tenantId, shiftId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id = ? AND posting_key = ?",
                Integer.class, tenantId, "shift-advance-recovery-" + shiftId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id = ? AND driver_id = ? "
                        + "AND transaction_type = 'RECOVERABLE_ADVANCE'",
                Integer.class, tenantId, driverId)).isEqualTo(1);
    }

    @Test
    void databaseRejectsCompletingPositiveWageShiftWithoutJournal() throws Exception {
        JsonNode running = startShift(token,
                shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        long id = running.path("id").asLong();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE shifts SET status = 'COMPLETED', finished_at = UTC_TIMESTAMP(6) WHERE id = ?", id))
                .hasMessageContaining("COMPLETED_SHIFT_WAGE_JOURNAL_REQUIRED");

        Shift unchanged = shiftRepository.findById(id).orElseThrow(AssertionError::new);
        assertThat(unchanged.getStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(unchanged.getDriverWageJournalEntryId()).isNull();

        // The supported service flow posts the journal first and can still complete the shift.
        JsonNode completed = finishShift(token, running);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("driverWageJournalEntryId").isNull()).isFalse();
    }



    @Test
    void previouslyFinishedZeroWageShiftCanBeReinstatedWithoutWageJournal() throws Exception {
        Map<String, Object> startBody = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        startBody.put("driverWage", BigDecimal.ZERO);
        JsonNode running = startShift(token, startBody);
        long id = running.path("id").asLong();

        // Reproduce a legacy cancelled record that still carries a finish timestamp.
        jdbcTemplate.update(
                "UPDATE shifts SET status = 'CANCELLED', finished_at = UTC_TIMESTAMP(6) WHERE id = ?", id);

        JsonNode reinstated = body(postJson("/api/v1/shifts/" + id + "/reinstate",
                versionBody(running.path("version").asLong()), token).andExpect(status().isOk()));

        assertThat(reinstated.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(reinstated.path("driverWage").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(reinstated.path("driverWageJournalEntryId").isNull()).isTrue();
    }

    @Test
    void databaseKeepsDriverLedgerAppendOnlyAndAllowsOnlyOneShiftEarning() throws Exception {
        JsonNode done = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING")));
        long shiftId = done.path("id").asLong();
        Long ledgerId = jdbcTemplate.queryForObject(
                "SELECT id FROM driver_ledger_entries WHERE shift_id = ? AND transaction_type = 'EARNING'",
                Long.class, shiftId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE driver_ledger_entries SET description = 'tampered' WHERE id = ?", ledgerId))
                .hasMessageContaining("DRIVER_LEDGER_IS_APPEND_ONLY");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM driver_ledger_entries WHERE id = ?", ledgerId))
                .hasMessageContaining("DRIVER_LEDGER_IS_APPEND_ONLY");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO driver_ledger_entries (tenant_id, driver_id, transaction_type, transaction_date, amount, balance_effect, description, client_request_key, shift_id, journal_entry_id, created_by_user_id, created_at) "
                + "SELECT tenant_id, driver_id, 'EARNING', transaction_date, amount, balance_effect, 'duplicate earning', 'duplicate-earning-test', shift_id, journal_entry_id, created_by_user_id, created_at "
                + "FROM driver_ledger_entries WHERE id = ?", ledgerId))
                .isNotNull();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE shift_id = ? AND transaction_type = 'EARNING'",
                Integer.class, shiftId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void completedShiftWithPostedWageCannotBeCancelled() throws Exception {
        JsonNode done = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING")));
        long id = done.path("id").asLong();

        Map<String, Object> cancel = versionBody(done.path("version").asLong());
        cancel.put("reason", "work did not happen");
        postJson("/api/v1/shifts/" + id + "/cancel", cancel, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());

        JsonNode unchanged = body(getJson("/api/v1/shifts/" + id, token));
        assertThat(unchanged.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(unchanged.path("driverWageJournalEntryId").asLong()).isEqualTo(
                done.path("driverWageJournalEntryId").asLong());
        Long postedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE source_type = 'SHIFT_WAGE' AND source_id = ?",
                Long.class, id);
        assertThat(postedCount).isEqualTo(1L);

        JsonNode history = body(getJson("/api/v1/shifts/" + id + "/history", token));
        assertThat(findBy(history, "action", "CANCELLED")).isNull();
        assertThat(findBy(history, "action", "FINISHED")).isNotNull();
    }

    @Test
    void shiftCancelledWhileRunningCanBeReinstatedWhenResourcesAreFree() throws Exception {
        JsonNode running = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        Map<String, Object> cancel = versionBody(running.path("version").asLong());
        cancel.put("reason", "started by mistake");
        JsonNode cancelled = body(postJson("/api/v1/shifts/" + running.path("id").asLong() + "/cancel", cancel,
                token).andExpect(status().isOk()));

        // Simulate an old activation timestamp; reactivation must reset it without touching created_at.
        jdbcTemplate.update("UPDATE shifts SET started_at = '2000-01-01 00:00:00.000000' WHERE id = ?",
                running.path("id").asLong());
        long shiftId = running.path("id").asLong();
        // Read the persisted value rather than the initial API response, which can contain
        // sub-microsecond precision before MySQL stores DATETIME(6).
        LocalDateTime createdAtBeforeReinstate = jdbcTemplate.queryForObject(
                "SELECT created_at FROM shifts WHERE id = ?", LocalDateTime.class, shiftId);
        JsonNode reinstated = body(postJson("/api/v1/shifts/" + shiftId + "/reinstate",
                versionBody(cancelled.path("version").asLong()), token).andExpect(status().isOk()));
        assertThat(reinstated.path("startedAt").asText()).doesNotStartWith("2000-01-01");
        LocalDateTime createdAtAfterReinstate = jdbcTemplate.queryForObject(
                "SELECT created_at FROM shifts WHERE id = ?", LocalDateTime.class, shiftId);
        assertThat(createdAtAfterReinstate).isEqualTo(createdAtBeforeReinstate);
        assertThat(reinstated.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(reinstated.path("finishedAt").isNull()).isTrue();
        assertThat(reinstated.path("cancelReason").isNull()).isTrue();

        JsonNode live = body(getJson("/api/v1/shifts/running", token).andExpect(status().isOk()));
        assertThat(findBy(live, "id", Long.toString(running.path("id").asLong()))).isNotNull();
        JsonNode history = body(getJson("/api/v1/shifts/" + running.path("id").asLong() + "/history", token));
        assertThat(findBy(history, "action", "CANCELLED")).isNotNull();
        assertThat(findBy(history, "action", "REINSTATED")).isNotNull();
    }

    @Test
    void reinstatingRunningCancelledShiftFailsWhenDriverIsBusy() throws Exception {
        JsonNode first = startShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING"));
        Map<String, Object> cancel = versionBody(first.path("version").asLong());
        cancel.put("reason", "temporary cancellation");
        JsonNode cancelled = body(postJson("/api/v1/shifts/" + first.path("id").asLong() + "/cancel", cancel,
                token).andExpect(status().isOk()));

        long secondEquipment = createEquipment(token, "EX-02", typeId, "1", "1200");
        JsonNode second = startShift(token, shiftBody(siteId, driverId, secondEquipment, day(1), "EVENING"));
        postJson("/api/v1/shifts/" + first.path("id").asLong() + "/reinstate",
                versionBody(cancelled.path("version").asLong()), token).andExpect(status().isConflict());

        assertThat(second.path("status").asText()).isEqualTo("IN_PROGRESS");
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

    @Test
    void recoverableAdvanceIsAnAssetAndIsAutomaticallyRecoveredFromLaterWages() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        JsonNode first = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(3), "MORNING")));
        Long cashId = jdbcTemplate.queryForObject(
                "SELECT id FROM chart_of_accounts WHERE tenant_id=(SELECT tenant_id FROM shifts WHERE id=?) AND account_code='1000'",
                Long.class, first.path("id").asLong());
        Map<String,Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "recoverable-" + first.path("id").asLong());
        advance.put("advanceDate", day(2).toString());
        advance.put("amount", new BigDecimal("200.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "سلفة قابلة للاسترداد");
        advance.put("advanceType", "RECOVERABLE");
        JsonNode advanced = body(postJson("/api/v1/drivers/" + driverId + "/ledger/advances", advance, token)
                .andExpect(status().isCreated()));
        assertThat(advanced.path("transactionType").asText()).isEqualTo("RECOVERABLE_ADVANCE");
        assertThat(advanced.path("balanceEffect").decimalValue()).isEqualByComparingTo("0.00");
        JsonNode second = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING")));
        Long recoveries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=(SELECT tenant_id FROM shifts WHERE id=?) AND shift_id=? AND transaction_type='ADVANCE_RECOVERY'",
                Long.class, second.path("id").asLong(), second.path("id").asLong());
        assertThat(recoveries).isEqualTo(1L);
        BigDecimal recovered = jdbcTemplate.queryForObject(
                "SELECT amount FROM driver_ledger_entries WHERE shift_id=? AND transaction_type='ADVANCE_RECOVERY'",
                BigDecimal.class, second.path("id").asLong());
        assertThat(recovered).isEqualByComparingTo("150.00");
        BigDecimal outstanding = jdbcTemplate.queryForObject(
                "SELECT SUM(CASE WHEN transaction_type='RECOVERABLE_ADVANCE' THEN amount WHEN transaction_type='ADVANCE_RECOVERY' THEN -amount ELSE 0 END) FROM driver_ledger_entries WHERE driver_id=?",
                BigDecimal.class, driverId);
        assertThat(outstanding).isEqualByComparingTo("50.00");

        Long workforceMemberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        Long secondShiftId = second.path("id").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries w "
                        + "JOIN driver_ledger_entries d ON d.id=w.legacy_source_entry_id "
                        + "AND d.tenant_id=w.tenant_id "
                        + "WHERE w.tenant_id=? AND w.workforce_member_id=? "
                        + "AND w.transaction_type='RECOVERABLE_ADVANCE' "
                        + "AND w.amount=200.00 AND w.balance_effect=0.00 "
                        + "AND w.legacy_driver_id=? AND w.journal_entry_id=d.journal_entry_id",
                Integer.class, tenantId, workforceMemberId, driverId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries w "
                        + "JOIN driver_ledger_entries d ON d.id=w.legacy_source_entry_id "
                        + "AND d.tenant_id=w.tenant_id "
                        + "WHERE w.tenant_id=? AND w.workforce_member_id=? AND w.shift_id=? "
                        + "AND w.transaction_type='ADVANCE_RECOVERY' "
                        + "AND w.amount=150.00 AND w.balance_effect=-150.00 "
                        + "AND w.journal_entry_id=d.journal_entry_id",
                Integer.class, tenantId, workforceMemberId, secondShiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN transaction_type='RECOVERABLE_ADVANCE' THEN amount "
                        + "WHEN transaction_type='ADVANCE_RECOVERY' THEN -amount ELSE 0 END),0) "
                        + "FROM workforce_ledger_entries WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, workforceMemberId)).isEqualByComparingTo("50.00");
    }

    @Test
    void concurrentWorkerPaymentsCannotSpendTheSameEarnedBalanceTwice() throws Exception {
        seedCashBalance(new BigDecimal("500.00"));
        JsonNode earned = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING")));
        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM drivers WHERE id = ?", Long.class, driverId);
        Long cashId = jdbcTemplate.queryForObject(
                "SELECT id FROM chart_of_accounts WHERE tenant_id = ? AND account_code = '1000'",
                Long.class, tenantId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentWorkerPayment(
                    cashId, "worker-pay-race-a", ready, start));
            Future<Integer> second = executor.submit(() -> concurrentWorkerPayment(
                    cashId, "worker-pay-race-b", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.Arrays.asList(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 400);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        Integer payments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE driver_id = ? AND transaction_type = 'PAYMENT'",
                Integer.class, driverId);
        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect), 0) FROM driver_ledger_entries WHERE driver_id = ?",
                BigDecimal.class, driverId);
        assertThat(payments).isEqualTo(1);
        assertThat(balance).isEqualByComparingTo("50.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE tenant_id = ? AND source_type = 'DRIVER_PAYMENT'",
                Integer.class, tenantId)).isEqualTo(1);
    }

    private int concurrentWorkerPayment(Long cashId, String key, CountDownLatch ready,
                                        CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent worker payment.");
        }
        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", key);
        payment.put("paymentDate", day(1).toString());
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Concurrent worker payment");
        MvcResult result = postJson("/api/v1/drivers/" + driverId + "/ledger/payments", payment, token)
                .andReturn();
        return result.getResponse().getStatus();
    }

    @Test
    void advanceAgainstEarnedPayableReducesAvailableWorkerBalance() throws Exception {
        seedCashBalance(new BigDecimal("500.00"));
        JsonNode earned = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING")));
        Long cashId = jdbcTemplate.queryForObject(
                "SELECT id FROM chart_of_accounts WHERE tenant_id=(SELECT tenant_id FROM shifts WHERE id=?) AND account_code='1000'",
                Long.class, earned.path("id").asLong());
        Map<String,Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "earned-advance-" + earned.path("id").asLong());
        advance.put("advanceDate", day(1).toString());
        advance.put("amount", new BigDecimal("50.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "سلفة من أجر مستحق");
        advance.put("advanceType", "AGAINST_EARNED");
        JsonNode result = body(postJson("/api/v1/drivers/" + driverId + "/ledger/advances", advance, token)
                .andExpect(status().isCreated()));
        assertThat(result.path("transactionType").asText()).isEqualTo("ADVANCE");
        assertThat(result.path("balanceAfter").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void databaseRejectsCompletingShiftWithAnotherShiftsWageJournal() throws Exception {
        JsonNode running = startShift(token,
                shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));
        long shiftId = running.path("id").asLong();

        ChartAccount wageExpense = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "5000")
                .orElseThrow(() -> new AssertionError("Wage expense account missing"));
        ChartAccount driverPayable = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "2100")
                .orElseThrow(() -> new AssertionError("Driver payable account missing"));
        com.contractor.app.accounting.domain.JournalEntry unrelatedJournal = journalPostingService.post(
                tenantId, actorUserId, new JournalPostingRequest(
                        "unrelated-shift-wage-test-" + shiftId, "TEST_UNRELATED_WAGE", null,
                        today(), "Unrelated balanced journal", null, java.util.Arrays.asList(
                        new JournalLineRequest(wageExpense.getId(), "Test debit",
                                BigDecimal.ONE, BigDecimal.ZERO, null, null),
                        new JournalLineRequest(driverPayable.getId(), "Test credit",
                                BigDecimal.ZERO, BigDecimal.ONE, null, null))));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE shifts SET driver_wage_journal_entry_id = ?, status = 'COMPLETED', "
                        + "finished_at = UTC_TIMESTAMP(6) WHERE id = ?",
                unrelatedJournal.getId(), shiftId))
                .hasMessageContaining("COMPLETED_SHIFT_WAGE_JOURNAL_MISMATCH");

        Shift unchanged = shiftRepository.findById(shiftId).orElseThrow(AssertionError::new);
        assertThat(unchanged.getStatus()).isEqualTo(com.contractor.app.shift.domain.ShiftStatus.IN_PROGRESS);
        assertThat(unchanged.getDriverWageJournalEntryId()).isNull();
    }

    @Test
    void databaseRejectsUnrelatedJournalLinkEvenWhenShiftWageIsZero() throws Exception {
        Map<String, Object> startBody = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        startBody.put("driverWage", BigDecimal.ZERO);
        JsonNode running = startShift(token, startBody);
        long shiftId = running.path("id").asLong();

        ChartAccount wageExpense = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "5000")
                .orElseThrow(() -> new AssertionError("Wage expense account missing"));
        ChartAccount driverPayable = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "2100")
                .orElseThrow(() -> new AssertionError("Driver payable account missing"));
        com.contractor.app.accounting.domain.JournalEntry unrelatedJournal = journalPostingService.post(
                tenantId, actorUserId, new JournalPostingRequest(
                        "unrelated-zero-shift-test-" + shiftId, "TEST_UNRELATED_WAGE", null,
                        today(), "Unrelated balanced journal", null, java.util.Arrays.asList(
                        new JournalLineRequest(wageExpense.getId(), "Test debit",
                                BigDecimal.ONE, BigDecimal.ZERO, null, null),
                        new JournalLineRequest(driverPayable.getId(), "Test credit",
                                BigDecimal.ZERO, BigDecimal.ONE, null, null))));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE shifts SET driver_wage_journal_entry_id = ?, status = 'COMPLETED', "
                        + "finished_at = UTC_TIMESTAMP(6) WHERE id = ?",
                unrelatedJournal.getId(), shiftId))
                .hasMessageContaining("COMPLETED_SHIFT_WAGE_JOURNAL_MISMATCH");

        Shift unchanged = shiftRepository.findById(shiftId).orElseThrow(AssertionError::new);
        assertThat(unchanged.getStatus()).isEqualTo(com.contractor.app.shift.domain.ShiftStatus.IN_PROGRESS);
        assertThat(unchanged.getDriverWage()).isEqualByComparingTo("0.00");
        assertThat(unchanged.getDriverWageJournalEntryId()).isNull();
    }


    @Test
    void completedShiftCorrectionReversesOriginalWageAndIsIdempotent() throws Exception {
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, day(2), "MORNING")));
        long shiftId = completed.path("id").asLong();
        long originalJournalId = completed.path("driverWageJournalEntryId").asLong();

        Map<String, Object> correction = new HashMap<>();
        correction.put("idempotencyKey", "shift-correction-test-" + java.util.UUID.randomUUID());
        correction.put("reason", "تصحيح أجر الوردية بعد مراجعة المستندات");
        correction.put("expectedVersion", completed.path("version").asLong());
        correction.put("shiftDate", completed.path("shiftDate").asText());
        correction.put("shiftType", completed.path("shiftType").asText());
        correction.put("siteId", siteId);
        correction.put("equipmentId", equipmentId);
        correction.put("driverWage", new BigDecimal("210.00"));
        correction.put("price", new BigDecimal("1400.00"));
        correction.put("equipmentOperatingCost", BigDecimal.ZERO);
        correction.put("notes", completed.path("notes").isNull() ? null : completed.path("notes").asText());

        JsonNode corrected = body(postJson("/api/v1/shifts/" + shiftId + "/corrections",
                correction, token).andExpect(status().isOk()));

        assertThat(corrected.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(corrected.path("driverWage").decimalValue()).isEqualByComparingTo("210.00");
        assertThat(corrected.path("price").decimalValue()).isEqualByComparingTo("1400.00");
        long replacementJournalId = corrected.path("driverWageJournalEntryId").asLong();
        assertThat(replacementJournalId).isNotEqualTo(originalJournalId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE id = ? AND status = 'POSTED'",
                Integer.class, originalJournalId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE reversal_of_entry_id = ?",
                Integer.class, originalJournalId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE id = ? AND source_type = 'SHIFT_WAGE_CORRECTION' AND source_id = ?",
                Integer.class, replacementJournalId,
                jdbcTemplate.queryForObject("SELECT id FROM shift_corrections WHERE shift_id = ?",
                        Long.class, shiftId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE shift_id = ? AND transaction_type = 'CORRECTION_REVERSAL'",
                Integer.class, shiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE shift_id = ? AND transaction_type = 'EARNING' AND journal_entry_id = ?",
                Integer.class, shiftId, replacementJournalId)).isEqualTo(1);

        // A retry with the same key and payload returns the completed result, without
        // posting another reversal or another replacement earning.
        JsonNode replay = body(postJson("/api/v1/shifts/" + shiftId + "/corrections",
                correction, token).andExpect(status().isOk()));
        assertThat(replay.path("driverWageJournalEntryId").asLong()).isEqualTo(replacementJournalId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE reversal_of_entry_id = ?",
                Integer.class, originalJournalId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE shift_id = ? AND transaction_type = 'CORRECTION_REVERSAL'",
                Integer.class, shiftId)).isEqualTo(1);

        JsonNode history = body(getJson("/api/v1/shifts/" + shiftId + "/history", token));
        boolean correctionAudited = false;
        for (JsonNode row : history) {
            if ("CORRECTED".equals(row.path("action").asText())) {
                correctionAudited = true;
                break;
            }
        }
        assertThat(correctionAudited).isTrue();
    }


    @Test
    void approvedGeneralAgreementOverridesLegacyDriverRateAndIsFrozenOnShift() throws Exception {
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ?", Long.class, driverId);
        String reference = "WF05-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO compensation_agreements "
                + "(tenant_id, workforce_member_id, agreement_reference, status, effective_from, "
                + "currency_code, source, approved_by_user_id, approved_at, created_by_user_id, updated_by_user_id) "
                + "VALUES (?, ?, ?, 'APPROVED', ?, 'EGP', 'MANUAL', ?, ?, ?, ?)",
                tenantId, memberId, reference, day(10), actorUserId,
                LocalDateTime.now(java.time.ZoneOffset.UTC), actorUserId, actorUserId);
        Long agreementId = jdbcTemplate.queryForObject(
                "SELECT id FROM compensation_agreements WHERE tenant_id = ? AND agreement_reference = ?",
                Long.class, tenantId, reference);
        jdbcTemplate.update("INSERT INTO compensation_components "
                + "(tenant_id, compensation_agreement_id, component_code, display_name, basis, rate, shift_type, sort_order) "
                + "VALUES (?, ?, 'MORNING_BASE', 'أجر الوردية الصباحية', 'SHIFT', 275.00, 'MORNING', 0)",
                tenantId, agreementId);

        getJson("/api/v1/shifts/defaults?driverId=" + driverId + "&equipmentId=" + equipmentId
                + "&shiftType=MORNING&siteId=" + siteId + "&shiftDate=" + day(1), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverWage").value(275.0))
                .andExpect(jsonPath("$.wageSource").value("AGREEMENT"));

        Map<String, Object> manualOverride = shiftBody(siteId, driverId, equipmentId, day(1), "MORNING");
        manualOverride.put("driverWage", 999.00);
        postJson("/api/v1/shifts", manualOverride, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        JsonNode shift = startShift(token, shiftBody(siteId, driverId, equipmentId, day(1), "MORNING"));

        assertThat(shift.path("driverWage").decimalValue()).isEqualByComparingTo("275.00");
        assertThat(shift.path("wageSource").asText()).isEqualTo("AGREEMENT");
        assertThat(shift.path("compensationAgreementId").asLong()).isEqualTo(agreementId);
        assertThat(shift.path("compensationAgreementReference").asText()).isEqualTo(reference);
        assertThat(shift.path("compensationBasis").asText()).isEqualTo("SHIFT");
        assertThat(shift.path("compensationRate").decimalValue()).isEqualByComparingTo("275.00");

        jdbcTemplate.update("UPDATE compensation_components SET rate = 999.00 "
                + "WHERE tenant_id = ? AND compensation_agreement_id = ?", tenantId, agreementId);
        JsonNode reread = body(getJson("/api/v1/shifts/" + shift.path("id").asLong(), token)
                .andExpect(status().isOk()));
        assertThat(reread.path("driverWage").decimalValue()).isEqualByComparingTo("275.00");
        assertThat(reread.path("compensationRate").decimalValue()).isEqualByComparingTo("275.00");
    }

    @Test
    void contractualGapDoesNotSilentlyFallBackToLegacyDriverRate() throws Exception {
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ?", Long.class, driverId);
        String reference = "WF05-GAP-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO compensation_agreements "
                + "(tenant_id, workforce_member_id, agreement_reference, status, effective_from, "
                + "currency_code, source, approved_by_user_id, approved_at, created_by_user_id, updated_by_user_id) "
                + "VALUES (?, ?, ?, 'APPROVED', ?, 'EGP', 'MANUAL', ?, ?, ?, ?)",
                tenantId, memberId, reference, today().plusDays(1), actorUserId,
                LocalDateTime.now(java.time.ZoneOffset.UTC), actorUserId, actorUserId);

        postJson("/api/v1/shifts", shiftBody(siteId, driverId, equipmentId, today(), "MORNING"), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }



    @Test
    void concurrentShiftFinishAndDriverPaymentSerializeWithoutDeadlock() throws Exception {
        seedCashBalance(new BigDecimal("2000.00"));
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = started.path("id").asLong();
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        long cashId = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Missing cash account")).getId();

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "finish-payment-race-" + java.util.UUID.randomUUID());
        payment.put("paymentDate", today());
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Payment racing shift completion");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int finishStatus;
        int paymentStatus;
        try {
            Future<Integer> finish = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/v1/shifts/" + shiftId + "/finish",
                        versionBody(started.path("version").asLong()), token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> pay = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/v1/drivers/" + driverId + "/ledger/payments",
                        payment, token).andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            finishStatus = finish.get(30, TimeUnit.SECONDS);
            paymentStatus = pay.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(finishStatus).isEqualTo(200);
        assertThat(paymentStatus == 201 || paymentStatus == 400).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shifts WHERE tenant_id=? AND id=? AND status='COMPLETED'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND shift_id=? AND transaction_type='EARNING'",
                Integer.class, tenantId, driverId, shiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='PAYMENT' AND client_request_key=?",
                Integer.class, tenantId, driverId, payment.get("clientRequestKey")))
                .isEqualTo(paymentStatus == 201 ? 1 : 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? "
                        + "AND workforce_member_id=?",
                Integer.class, tenantId, memberId))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                        Integer.class, tenantId, driverId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, memberId))
                .isEqualByComparingTo(paymentStatus == 201 ? "50.00" : "150.00");
    }

    @Test
    void concurrentShiftFinishAndRecoverableAdvanceSerializeWithoutDeadlock() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = started.path("id").asLong();
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        long cashId = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Missing cash account")).getId();

        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "finish-recoverable-race-" + java.util.UUID.randomUUID());
        advance.put("advanceDate", today());
        advance.put("amount", new BigDecimal("75.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "Recoverable advance racing shift completion");
        advance.put("advanceType", "RECOVERABLE");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int finishStatus;
        int advanceStatus;
        try {
            Future<Integer> finish = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/v1/shifts/" + shiftId + "/finish",
                        versionBody(started.path("version").asLong()), token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> issueAdvance = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/v1/drivers/" + driverId + "/ledger/advances",
                        advance, token).andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            finishStatus = finish.get(30, TimeUnit.SECONDS);
            advanceStatus = issueAdvance.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(finishStatus).isEqualTo(200);
        assertThat(advanceStatus).isEqualTo(201);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shifts WHERE tenant_id=? AND id=? AND status='COMPLETED'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='EARNING' AND shift_id=?",
                Integer.class, tenantId, driverId, shiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='RECOVERABLE_ADVANCE' AND client_request_key=?",
                Integer.class, tenantId, driverId, advance.get("clientRequestKey"))).isEqualTo(1);

        BigDecimal recoverableOutstanding = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN transaction_type='RECOVERABLE_ADVANCE' THEN amount "
                        + "WHEN transaction_type='ADVANCE_RECOVERY' THEN -amount ELSE 0 END),0) "
                        + "FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                BigDecimal.class, tenantId, driverId);
        BigDecimal payableBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM driver_ledger_entries "
                        + "WHERE tenant_id=? AND driver_id=?",
                BigDecimal.class, tenantId, driverId);
        assertThat(recoverableOutstanding).isIn(new BigDecimal("0.00"), new BigDecimal("75.00"));
        BigDecimal recoveredAmount = new BigDecimal("75.00").subtract(recoverableOutstanding);
        assertThat(payableBalance.add(recoveredAmount)).isEqualByComparingTo("150.00");
        assertThat(payableBalance).isEqualByComparingTo(
                recoverableOutstanding.signum() == 0 ? "75.00" : "150.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='ADVANCE_RECOVERY' AND shift_id=?",
                Integer.class, tenantId, driverId, shiftId))
                .isEqualTo(recoverableOutstanding.signum() == 0 ? 1 : 0);

        Integer driverEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                Integer.class, tenantId, driverId);
        Integer workforceEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? AND workforce_member_id=?",
                Integer.class, tenantId, memberId);
        assertThat(workforceEntries).isEqualTo(driverEntries);
        BigDecimal workforceBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, memberId);
        assertThat(workforceBalance).isEqualByComparingTo(payableBalance);
    }

    @Test
    void concurrentCanonicalIdentityUpdateAndPaymentSerializeWithoutDeadlock() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        JsonNode member = body(getJson("/api/v1/workforce/members/" + memberId, token)
                .andExpect(status().isOk()));
        long cashId = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Missing cash account")).getId();

        Map<String, Object> identityUpdate = new HashMap<>();
        identityUpdate.put("fullName", "Concurrent Canonical Identity");
        identityUpdate.put("phone", "01055551234");
        identityUpdate.put("nationalId", null);
        identityUpdate.put("notes", "Updated during payment");
        identityUpdate.put("version", member.path("version").asLong());

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "identity-payment-race-" + java.util.UUID.randomUUID());
        payment.put("paymentDate", today());
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Payment racing canonical identity update");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int identityStatus;
        int paymentStatus;
        try {
            Future<Integer> identity = executor.submit(() -> {
                ready.countDown();
                start.await();
                return putJson("/api/v1/workforce/members/" + memberId, identityUpdate, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> pay = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/v1/drivers/" + driverId + "/ledger/payments", payment, token)
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            identityStatus = identity.get(30, TimeUnit.SECONDS);
            paymentStatus = pay.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(identityStatus).isEqualTo(200);
        assertThat(paymentStatus).isEqualTo(201);
        Map<String, Object> linkedIdentity = jdbcTemplate.queryForMap(
                "SELECT d.name, d.phone, wm.full_name, wm.phone "
                        + "FROM drivers d JOIN workforce_members wm "
                        + "ON wm.id=d.workforce_member_id AND wm.tenant_id=d.tenant_id "
                        + "WHERE d.tenant_id=? AND d.id=?",
                tenantId, driverId);
        assertThat(linkedIdentity.get("name")).isEqualTo("Concurrent Canonical Identity");
        assertThat(linkedIdentity.get("full_name")).isEqualTo("Concurrent Canonical Identity");
        assertThat(linkedIdentity.get("phone")).isEqualTo("01055551234");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='PAYMENT' AND client_request_key=?",
                Integer.class, tenantId, driverId, payment.get("clientRequestKey"))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? AND workforce_member_id=?",
                Integer.class, tenantId, memberId))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                        Integer.class, tenantId, driverId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, memberId)).isEqualByComparingTo("50.00");
    }


    @Test
    void concurrentDriverProfileUpdateAndPaymentSerializeWithoutDeadlock() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING")));
        JsonNode driver = body(getJson("/api/v1/drivers/" + driverId, token)
                .andExpect(status().isOk()));
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        long cashId = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Missing cash account")).getId();

        Map<String, Object> driverUpdate = new HashMap<>();
        driverUpdate.put("name", "Concurrent Driver Projection Update");
        driverUpdate.put("phone", "01055559876");
        driverUpdate.put("nationalId", null);
        driverUpdate.put("workerType", driver.path("workerType").asText());
        driverUpdate.put("morningRate", driver.path("morningRate").decimalValue());
        driverUpdate.put("eveningRate", driver.path("eveningRate").decimalValue());
        driverUpdate.put("nightRate", driver.path("nightRate").decimalValue());
        driverUpdate.put("version", driver.path("version").asLong());

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "driver-profile-payment-race-" + java.util.UUID.randomUUID());
        payment.put("paymentDate", today());
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Payment racing Driver profile update");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int updateStatus;
        int paymentStatus;
        try {
            Future<Integer> update = executor.submit(() -> {
                ready.countDown();
                start.await();
                return putJson("/api/v1/drivers/" + driverId, driverUpdate, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> pay = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/v1/drivers/" + driverId + "/ledger/payments", payment, token)
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            updateStatus = update.get(30, TimeUnit.SECONDS);
            paymentStatus = pay.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(updateStatus).isEqualTo(200);
        assertThat(paymentStatus).isEqualTo(201);
        Map<String, Object> linkedIdentity = jdbcTemplate.queryForMap(
                "SELECT d.name, d.phone, wm.full_name, wm.phone "
                        + "FROM drivers d JOIN workforce_members wm "
                        + "ON wm.id=d.workforce_member_id AND wm.tenant_id=d.tenant_id "
                        + "WHERE d.tenant_id=? AND d.id=?",
                tenantId, driverId);
        assertThat(linkedIdentity.get("name")).isEqualTo("Concurrent Driver Projection Update");
        assertThat(linkedIdentity.get("full_name")).isEqualTo("Concurrent Driver Projection Update");
        assertThat(linkedIdentity.get("phone")).isEqualTo("01055559876");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='PAYMENT' AND client_request_key=?",
                Integer.class, tenantId, driverId, payment.get("clientRequestKey"))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? AND workforce_member_id=?",
                Integer.class, tenantId, memberId))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                        Integer.class, tenantId, driverId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, memberId)).isEqualByComparingTo("50.00");
    }


    @Test
    void concurrentCompletedShiftCorrectionAndPaymentSerializeWithoutDeadlock() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId,
                        today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        BigDecimal originalWage = completed.path("driverWage").decimalValue();
        long cashId = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Missing cash account")).getId();

        Map<String, Object> correction = new HashMap<>();
        correction.put("idempotencyKey", "correction-payment-race-" + java.util.UUID.randomUUID());
        correction.put("reason", "Concurrent correction and payment serialization test");
        correction.put("expectedVersion", completed.path("version").asLong());
        correction.put("shiftDate", completed.path("shiftDate").asText());
        correction.put("shiftType", completed.path("shiftType").asText());
        correction.put("siteId", siteId);
        correction.put("equipmentId", equipmentId);
        correction.put("driverWage", new BigDecimal("210.00"));
        correction.put("price", completed.path("price").decimalValue());
        correction.put("equipmentOperatingCost", completed.path("equipmentOperatingCost").decimalValue());
        correction.put("notes", completed.path("notes").isNull() ? null : completed.path("notes").asText());

        Map<String, Object> payment = new HashMap<>();
        payment.put("clientRequestKey", "correction-payment-race-pay-" + java.util.UUID.randomUUID());
        payment.put("paymentDate", today());
        payment.put("amount", new BigDecimal("100.00"));
        payment.put("paymentAccountId", cashId);
        payment.put("description", "Payment racing completed shift correction");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int correctionStatus;
        int paymentStatus;
        try {
            Future<Integer> correct = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start correction.");
                }
                return postJson("/api/v1/shifts/" + shiftId + "/corrections", correction, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> pay = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start payment.");
                }
                return postJson("/api/v1/drivers/" + driverId + "/ledger/payments", payment, token)
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            correctionStatus = correct.get(30, TimeUnit.SECONDS);
            paymentStatus = pay.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(correctionStatus).isIn(200, 409);
        assertThat(paymentStatus).isEqualTo(201);
        BigDecimal expectedBalance = (correctionStatus == 200
                ? new BigDecimal("210.00") : originalWage).subtract(new BigDecimal("100.00"));
        BigDecimal payableBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM driver_ledger_entries "
                        + "WHERE tenant_id=? AND driver_id=?",
                BigDecimal.class, tenantId, driverId);
        assertThat(payableBalance).isEqualByComparingTo(expectedBalance);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='PAYMENT' AND client_request_key=?",
                Integer.class, tenantId, driverId, payment.get("clientRequestKey"))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=? AND status='APPLIED'",
                Integer.class, tenantId, shiftId)).isEqualTo(correctionStatus == 200 ? 1 : 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND shift_id=? AND transaction_type='CORRECTION_REVERSAL'",
                Integer.class, tenantId, driverId, shiftId)).isEqualTo(correctionStatus == 200 ? 1 : 0);

        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        BigDecimal workforceBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, memberId);
        assertThat(workforceBalance).isEqualByComparingTo(payableBalance);
    }


    @Test
    void concurrentCompletedShiftCorrectionAndRecoverableAdvanceSerializeWithoutDeadlock() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId,
                        today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        long cashId = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Missing cash account")).getId();

        Map<String, Object> correction = new HashMap<>();
        correction.put("idempotencyKey", "correction-recoverable-race-" + java.util.UUID.randomUUID());
        correction.put("reason", "Correction racing independent recoverable advance");
        correction.put("expectedVersion", completed.path("version").asLong());
        correction.put("shiftDate", completed.path("shiftDate").asText());
        correction.put("shiftType", completed.path("shiftType").asText());
        correction.put("siteId", siteId);
        correction.put("equipmentId", equipmentId);
        correction.put("driverWage", new BigDecimal("210.00"));
        correction.put("price", completed.path("price").decimalValue());
        correction.put("equipmentOperatingCost", completed.path("equipmentOperatingCost").decimalValue());
        correction.put("notes", completed.path("notes").isNull() ? null : completed.path("notes").asText());

        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "correction-recoverable-race-advance-" + java.util.UUID.randomUUID());
        advance.put("advanceDate", today());
        advance.put("amount", new BigDecimal("75.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "Recoverable advance racing completed shift correction");
        advance.put("advanceType", "RECOVERABLE");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int correctionStatus;
        int advanceStatus;
        try {
            Future<Integer> correct = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start correction.");
                }
                return postJson("/api/v1/shifts/" + shiftId + "/corrections", correction, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> issueAdvance = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start recoverable advance.");
                }
                return postJson("/api/v1/drivers/" + driverId + "/ledger/advances", advance, token)
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            correctionStatus = correct.get(30, TimeUnit.SECONDS);
            advanceStatus = issueAdvance.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(correctionStatus).isEqualTo(200);
        assertThat(advanceStatus).isEqualTo(201);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=? AND status='APPLIED'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='RECOVERABLE_ADVANCE' AND client_request_key=?",
                Integer.class, tenantId, driverId, advance.get("clientRequestKey"))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND shift_id=? AND transaction_type='ADVANCE_RECOVERY'",
                Integer.class, tenantId, driverId, shiftId)).isZero();

        BigDecimal payableBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM driver_ledger_entries "
                        + "WHERE tenant_id=? AND driver_id=?",
                BigDecimal.class, tenantId, driverId);
        BigDecimal recoverableOutstanding = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN transaction_type='RECOVERABLE_ADVANCE' THEN amount "
                        + "WHEN transaction_type='ADVANCE_RECOVERY' THEN -amount ELSE 0 END),0) "
                        + "FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                BigDecimal.class, tenantId, driverId);
        assertThat(payableBalance).isEqualByComparingTo("210.00");
        assertThat(recoverableOutstanding).isEqualByComparingTo("75.00");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? AND workforce_member_id=?",
                Integer.class, tenantId, memberId))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                        Integer.class, tenantId, driverId));
        BigDecimal workforceBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, memberId);
        assertThat(workforceBalance).isEqualByComparingTo(payableBalance);
    }


    @Test
    void concurrentCompletedShiftCorrectionAndAgainstEarnedAdvanceSerializeWithoutDeadlock() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        JsonNode completed = finishShift(token,
                startShift(token, shiftBody(siteId, driverId, equipmentId,
                        today().minusDays(1), "MORNING")));
        long shiftId = completed.path("id").asLong();
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        long cashId = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Missing cash account")).getId();

        Map<String, Object> correction = new HashMap<>();
        correction.put("idempotencyKey", "correction-earned-race-" + java.util.UUID.randomUUID());
        correction.put("reason", "Correction racing earned-balance advance");
        correction.put("expectedVersion", completed.path("version").asLong());
        correction.put("shiftDate", completed.path("shiftDate").asText());
        correction.put("shiftType", completed.path("shiftType").asText());
        correction.put("siteId", siteId);
        correction.put("equipmentId", equipmentId);
        correction.put("driverWage", new BigDecimal("210.00"));
        correction.put("price", completed.path("price").decimalValue());
        correction.put("equipmentOperatingCost", completed.path("equipmentOperatingCost").decimalValue());
        correction.put("notes", completed.path("notes").isNull() ? null : completed.path("notes").asText());

        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "correction-earned-race-advance-" + java.util.UUID.randomUUID());
        advance.put("advanceDate", today());
        advance.put("amount", new BigDecimal("75.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "Earned-balance advance racing completed shift correction");
        advance.put("advanceType", "AGAINST_EARNED");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int correctionStatus;
        int advanceStatus;
        try {
            Future<Integer> correct = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start correction.");
                }
                return postJson("/api/v1/shifts/" + shiftId + "/corrections", correction, token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> issueAdvance = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start earned-balance advance.");
                }
                return postJson("/api/v1/drivers/" + driverId + "/ledger/advances", advance, token)
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            correctionStatus = correct.get(30, TimeUnit.SECONDS);
            advanceStatus = issueAdvance.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(correctionStatus).isIn(200, 409);
        assertThat(advanceStatus).isEqualTo(201);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND shift_id=? AND status='APPLIED'",
                Integer.class, tenantId, shiftId)).isEqualTo(correctionStatus == 200 ? 1 : 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='ADVANCE' AND client_request_key=?",
                Integer.class, tenantId, driverId, advance.get("clientRequestKey"))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND shift_id=? AND transaction_type='ADVANCE_RECOVERY'",
                Integer.class, tenantId, driverId, shiftId)).isZero();

        BigDecimal payableBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM driver_ledger_entries "
                        + "WHERE tenant_id=? AND driver_id=?",
                BigDecimal.class, tenantId, driverId);
        assertThat(payableBalance).isEqualByComparingTo(
                correctionStatus == 200 ? "135.00" : "75.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='RECOVERABLE_ADVANCE'",
                Integer.class, tenantId, driverId)).isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? AND workforce_member_id=?",
                Integer.class, tenantId, memberId))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                        Integer.class, tenantId, driverId));
        BigDecimal workforceBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, memberId);
        assertThat(workforceBalance).isEqualByComparingTo(payableBalance);
    }


    @Test
    void concurrentShiftFinishAndAgainstEarnedAdvanceSerializeWithoutDeadlock() throws Exception {
        seedCashBalance(new BigDecimal("1000.00"));
        JsonNode started = startShift(token,
                shiftBody(siteId, driverId, equipmentId, today().minusDays(1), "MORNING"));
        long shiftId = started.path("id").asLong();
        long memberId = jdbcTemplate.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE tenant_id=? AND id=?",
                Long.class, tenantId, driverId);
        long cashId = chartAccounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, "1000")
                .orElseThrow(() -> new IllegalStateException("Missing cash account")).getId();

        Map<String, Object> advance = new HashMap<>();
        advance.put("clientRequestKey", "finish-earned-race-" + java.util.UUID.randomUUID());
        advance.put("advanceDate", today());
        advance.put("amount", new BigDecimal("75.00"));
        advance.put("paymentAccountId", cashId);
        advance.put("description", "Earned-balance advance racing shift completion");
        advance.put("advanceType", "AGAINST_EARNED");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int finishStatus;
        int advanceStatus;
        try {
            Future<Integer> finish = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/v1/shifts/" + shiftId + "/finish",
                        versionBody(started.path("version").asLong()), token)
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> issueAdvance = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/v1/drivers/" + driverId + "/ledger/advances",
                        advance, token).andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            finishStatus = finish.get(30, TimeUnit.SECONDS);
            advanceStatus = issueAdvance.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(finishStatus).isEqualTo(200);
        assertThat(advanceStatus).isIn(201, 400);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shifts WHERE tenant_id=? AND id=? AND status='COMPLETED'",
                Integer.class, tenantId, shiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='EARNING' AND shift_id=?",
                Integer.class, tenantId, driverId, shiftId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='ADVANCE' AND client_request_key=?",
                Integer.class, tenantId, driverId, advance.get("clientRequestKey")))
                .isEqualTo(advanceStatus == 201 ? 1 : 0);

        BigDecimal payableBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM driver_ledger_entries "
                        + "WHERE tenant_id=? AND driver_id=?",
                BigDecimal.class, tenantId, driverId);
        assertThat(payableBalance).isEqualByComparingTo(
                advanceStatus == 201 ? "75.00" : "150.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=? "
                        + "AND transaction_type='RECOVERABLE_ADVANCE'",
                Integer.class, tenantId, driverId)).isZero();

        Integer driverEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM driver_ledger_entries WHERE tenant_id=? AND driver_id=?",
                Integer.class, tenantId, driverId);
        Integer workforceEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workforce_ledger_entries WHERE tenant_id=? AND workforce_member_id=?",
                Integer.class, tenantId, memberId);
        assertThat(workforceEntries).isEqualTo(driverEntries);
        BigDecimal workforceBalance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance_effect),0) FROM workforce_ledger_entries "
                        + "WHERE tenant_id=? AND workforce_member_id=?",
                BigDecimal.class, tenantId, memberId);
        assertThat(workforceBalance).isEqualByComparingTo(payableBalance);
    }

}