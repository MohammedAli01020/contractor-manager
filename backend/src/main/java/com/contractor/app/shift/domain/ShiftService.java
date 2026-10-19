package com.contractor.app.shift.domain;

import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.money.Money;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.util.Texts;
import com.contractor.app.driver.domain.Driver;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.equipment.domain.Equipment;
import com.contractor.app.equipment.domain.EquipmentStatus;
import com.contractor.app.equipment.infra.EquipmentRepository;
import com.contractor.app.shift.api.dto.EndShiftRequest;
import com.contractor.app.shift.api.dto.ShiftCancelRequest;
import com.contractor.app.shift.api.dto.ShiftDefaultsResponse;
import com.contractor.app.shift.api.dto.ShiftHistoryResponse;
import com.contractor.app.shift.api.dto.ShiftResponse;
import com.contractor.app.shift.api.dto.ShiftStartOptionsResponse;
import com.contractor.app.shift.api.dto.StartShiftRequest;
import com.contractor.app.shift.infra.ShiftAuditLogRepository;
import com.contractor.app.shift.infra.ShiftRepository;
import com.contractor.app.site.domain.Site;
import com.contractor.app.site.infra.SiteRepository;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftService {

    private static final String NOT_FOUND = "الوردية غير موجودة.";

    private final ShiftRepository shifts;
    private final ShiftAuditLogRepository audit;
    private final SiteRepository sites;
    private final DriverRepository drivers;
    private final EquipmentRepository equipment;
    private final UserRepository users;
    private final ZoneId businessZone;

    public ShiftService(ShiftRepository shifts, ShiftAuditLogRepository audit, SiteRepository sites,
                        DriverRepository drivers, EquipmentRepository equipment, UserRepository users,
                        @Value("${app.business-timezone:Africa/Cairo}") String businessTimezone) {
        this.shifts = shifts;
        this.audit = audit;
        this.sites = sites;
        this.drivers = drivers;
        this.equipment = equipment;
        this.users = users;
        this.businessZone = ZoneId.of(businessTimezone);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShiftResponse> list(Long tenantId, LocalDate from, LocalDate to, Long siteId, Long driverId,
                                            Long equipmentId, ShiftType type, ShiftStatus status,
                                            int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("تاريخ البداية يجب ألا يكون بعد تاريخ النهاية.");
        }
        Page<Shift> result = shifts.findAll(filter(tenantId, from, to, siteId, driverId, equipmentId, type, status),
                PageParams.of(page, size, Sort.by(Sort.Order.desc("shiftDate"), Sort.Order.desc("startedAt"),
                        Sort.Order.desc("id"))));
        List<ShiftResponse> items = toResponses(tenantId, result.getContent());
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ShiftResponse get(Long tenantId, Long id) {
        return toResponses(tenantId, Collections.singletonList(find(tenantId, id))).get(0);
    }

    @Transactional(readOnly = true)
    public ShiftDefaultsResponse defaults(Long tenantId, Long driverId, Long equipmentId, ShiftType type) {
        Driver driver = drivers.findByIdAndTenantId(driverId, tenantId)
                .orElseThrow(() -> ApiException.notFound("السائق أو العامل غير موجود."));
        Equipment item = equipment.findByIdAndTenantId(equipmentId, tenantId)
                .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));
        return new ShiftDefaultsResponse(Money.scale(rateFor(driver, type)),
                Money.scale(item.getDefaultShiftPrice()), Money.scale(item.getOperatingCostPerShift()));
    }

    @Transactional(readOnly = true)
    public ShiftStartOptionsResponse startOptions(Long tenantId) {
        List<ShiftStartOptionsResponse.DriverOption> driversList = drivers.findAvailableForShift(tenantId).stream()
                .sorted(Comparator.comparing(Driver::getName, String.CASE_INSENSITIVE_ORDER))
                .map(d -> new ShiftStartOptionsResponse.DriverOption(d.getId(), d.getName()))
                .collect(Collectors.toList());

        List<ShiftStartOptionsResponse.EquipmentOption> equipmentList = equipment
                .findAvailableForShift(tenantId, EquipmentStatus.AVAILABLE).stream()
                .sorted(Comparator.comparing(Equipment::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new ShiftStartOptionsResponse.EquipmentOption(e.getId(), e.getCode(), e.getName()))
                .collect(Collectors.toList());

        return new ShiftStartOptionsResponse(driversList, equipmentList);
    }

    @Transactional(readOnly = true)
    public List<ShiftHistoryResponse> history(Long tenantId, Long shiftId) {
        find(tenantId, shiftId);
        Map<Long, String> userNames = new HashMap<>();
        return audit.findByShiftIdAndTenantIdOrderByCreatedAtDescIdDesc(shiftId, tenantId).stream()
                .map(log -> new ShiftHistoryResponse(log, userNames.computeIfAbsent(log.getUserId(),
                        uid -> users.findByIdAndTenantId(uid, tenantId).map(User::getFullName).orElse("-"))))
                .collect(Collectors.toList());
    }

    @Transactional
    public ShiftResponse start(AuthenticatedUser me, StartShiftRequest request) {
        Long tenantId = me.getTenantId();
        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate shiftDate = startedAt.atZone(ZoneOffset.UTC).withZoneSameInstant(businessZone).toLocalDate();

        requireSite(tenantId, request.getSiteId());
        Driver driver = requireDriverForStart(tenantId, request.getDriverId());
        Equipment item = requireEquipmentForStart(tenantId, request.getEquipmentId());
        assertResourcesAvailable(tenantId, request.getDriverId(), request.getEquipmentId());

        Shift shift = new Shift(tenantId, shiftDate, request.getShiftType(), request.getSiteId(),
                request.getDriverId(), request.getEquipmentId());
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        shift.setStartedAt(startedAt);
        shift.setEndedAt(null);
        shift.setCancelledAt(null);
        shift.setCancelReason(null);
        shift.setNotes(Texts.blankToNull(request.getNotes()));
        shift.setDriverWage(request.getDriverWage() != null
                ? Money.scale(request.getDriverWage())
                : Money.scale(rateFor(driver, request.getShiftType())));
        shift.setPrice(request.getPrice() != null
                ? Money.scale(request.getPrice())
                : Money.scale(item.getDefaultShiftPrice()));
        shift.setEquipmentOperatingCost(Money.scale(item.getOperatingCostPerShift()));

        try {
            shifts.saveAndFlush(shift);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("السائق أو المعدة لم يعد متاحًا، تم بدء وردية أخرى بنفس الوقت.");
        }

        log(me, shift.getId(), ShiftAuditAction.STARTED, "status", null, ShiftStatus.IN_PROGRESS.name());
        return get(tenantId, shift.getId());
    }

    @Transactional
    public ShiftResponse end(AuthenticatedUser me, Long id, EndShiftRequest request) {
        Long tenantId = me.getTenantId();
        Shift shift = find(tenantId, id);
        ensureInProgress(shift, "لا يمكن إنهاء وردية ليست قيد التنفيذ.");
        shift.requireVersion(request.getVersion());
        shift.setStatus(ShiftStatus.COMPLETED);
        shift.setEndedAt(LocalDateTime.now(ZoneOffset.UTC));
        shifts.saveAndFlush(shift);
        log(me, shift.getId(), ShiftAuditAction.COMPLETED, "status", ShiftStatus.IN_PROGRESS.name(),
                ShiftStatus.COMPLETED.name());
        return get(tenantId, shift.getId());
    }

    @Transactional
    public ShiftResponse cancel(AuthenticatedUser me, Long id, ShiftCancelRequest request) {
        Long tenantId = me.getTenantId();
        Shift shift = find(tenantId, id);
        ensureInProgress(shift, "لا يمكن إلغاء وردية ليست قيد التنفيذ.");
        shift.requireVersion(request.getVersion());
        shift.setStatus(ShiftStatus.CANCELLED);
        shift.setCancelReason(request.getReason().trim());
        shift.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC));
        shifts.saveAndFlush(shift);
        log(me, shift.getId(), ShiftAuditAction.CANCELLED, "status", ShiftStatus.IN_PROGRESS.name(),
                ShiftStatus.CANCELLED.name());
        return get(tenantId, shift.getId());
    }

    @Transactional
    public ShiftResponse reinstate(AuthenticatedUser me, Long id, Long version) {
        Long tenantId = me.getTenantId();
        Shift shift = find(tenantId, id);
        if (shift.getStatus() != ShiftStatus.CANCELLED) {
            throw ApiException.conflict("الوردية ليست ملغاة.");
        }
        shift.requireVersion(version);
        shift.setStatus(ShiftStatus.COMPLETED);
        shift.setCancelReason(null);
        shift.setCancelledAt(null);
        if (shift.getEndedAt() == null) {
            shift.setEndedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        shifts.saveAndFlush(shift);
        log(me, shift.getId(), ShiftAuditAction.REINSTATED, "status", ShiftStatus.CANCELLED.name(),
                ShiftStatus.COMPLETED.name());
        return get(tenantId, shift.getId());
    }

    private void assertResourcesAvailable(Long tenantId, Long driverId, Long equipmentId) {
        if (shifts.hasInProgressForDriver(tenantId, driverId)) {
            throw ApiException.conflict("هذا السائق أو العامل لديه وردية قيد التنفيذ.");
        }
        if (shifts.hasInProgressForEquipment(tenantId, equipmentId)) {
            throw ApiException.conflict("هذه المعدة لديها وردية قيد التنفيذ.");
        }
    }

    private Shift find(Long tenantId, Long id) {
        return shifts.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private void ensureInProgress(Shift shift, String message) {
        if (shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw ApiException.conflict(message);
        }
    }

    private Site requireSite(Long tenantId, Long id) {
        Site site = sites.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("الموقع غير موجود."));
        if (!site.isActive()) {
            throw ApiException.badRequest("الموقع معطّل. اختر موقعًا فعّالًا.");
        }
        return site;
    }

    private Driver requireDriverForStart(Long tenantId, Long id) {
        Driver driver = drivers.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("السائق أو العامل غير موجود."));
        if (!driver.isActive()) {
            throw ApiException.badRequest("السائق أو العامل معطّل. اختر شخصًا فعّالًا.");
        }
        return driver;
    }

    private Equipment requireEquipmentForStart(Long tenantId, Long id) {
        Equipment item = equipment.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));
        if (!item.isActive()) {
            throw ApiException.badRequest("المعدة معطّلة. اختر معدة فعّالة.");
        }
        if (item.getStatus() != EquipmentStatus.AVAILABLE) {
            throw ApiException.badRequest("لا يمكن بدء وردية لمعدة غير متاحة.");
        }
        return item;
    }

    private static BigDecimal rateFor(Driver driver, ShiftType type) {
        switch (type) {
            case MORNING:
                return driver.getMorningRate();
            case EVENING:
                return driver.getEveningRate();
            default:
                return driver.getNightRate();
        }
    }

    private void log(AuthenticatedUser me, Long shiftId, ShiftAuditAction action, String field, String oldValue,
                     String newValue) {
        audit.save(new ShiftAuditLog(me.getTenantId(), shiftId, me.getUserId(), action, field, oldValue, newValue));
    }

    private List<ShiftResponse> toResponses(Long tenantId, List<Shift> list) {
        Set<Long> siteIds = new HashSet<>();
        Set<Long> driverIds = new HashSet<>();
        Set<Long> equipmentIds = new HashSet<>();
        for (Shift s : list) {
            siteIds.add(s.getSiteId());
            driverIds.add(s.getDriverId());
            equipmentIds.add(s.getEquipmentId());
        }
        Map<Long, String> siteNames = new HashMap<>();
        sites.findAllById(siteIds).stream().filter(s -> tenantId.equals(s.getTenantId()))
                .forEach(s -> siteNames.put(s.getId(), s.getName()));
        Map<Long, String> driverNames = new HashMap<>();
        drivers.findAllById(driverIds).stream().filter(d -> tenantId.equals(d.getTenantId()))
                .forEach(d -> driverNames.put(d.getId(), d.getName()));
        Map<Long, Equipment> equipmentById = new HashMap<>();
        equipment.findAllById(equipmentIds).stream().filter(e -> tenantId.equals(e.getTenantId()))
                .forEach(e -> equipmentById.put(e.getId(), e));

        return list.stream().map(s -> {
            Equipment e = equipmentById.get(s.getEquipmentId());
            return new ShiftResponse(s, siteNames.get(s.getSiteId()), driverNames.get(s.getDriverId()),
                    e == null ? null : e.getCode(), e == null ? null : e.getName());
        }).collect(Collectors.toList());
    }

    private static Specification<Shift> filter(Long tenantId, LocalDate from, LocalDate to, Long siteId,
                                               Long driverId, Long equipmentId, ShiftType type, ShiftStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("shiftDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("shiftDate"), to));
            }
            if (siteId != null) {
                predicates.add(cb.equal(root.get("siteId"), siteId));
            }
            if (driverId != null) {
                predicates.add(cb.equal(root.get("driverId"), driverId));
            }
            if (equipmentId != null) {
                predicates.add(cb.equal(root.get("equipmentId"), equipmentId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("shiftType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
