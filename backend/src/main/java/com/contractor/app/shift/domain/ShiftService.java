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
import com.contractor.app.equipment.infra.EquipmentRepository;
import com.contractor.app.shift.api.dto.ShiftCancelRequest;
import com.contractor.app.shift.api.dto.ShiftDefaultsResponse;
import com.contractor.app.shift.api.dto.ShiftHistoryResponse;
import com.contractor.app.shift.api.dto.ShiftRequest;
import com.contractor.app.shift.api.dto.ShiftResponse;
import com.contractor.app.shift.infra.ShiftAuditLogRepository;
import com.contractor.app.shift.infra.ShiftRepository;
import com.contractor.app.site.domain.Site;
import com.contractor.app.site.infra.SiteRepository;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
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

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<ShiftResponse> list(Long tenantId, LocalDate from, LocalDate to, Long siteId, Long driverId,
                                            Long equipmentId, ShiftType type, ShiftStatus status,
                                            int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("تاريخ البداية يجب ألا يكون بعد تاريخ النهاية.");
        }
        Page<Shift> result = shifts.findAll(filter(tenantId, from, to, siteId, driverId, equipmentId, type, status),
                PageParams.of(page, size, Sort.by(Sort.Direction.DESC, "shiftDate")));
        List<ShiftResponse> items = toResponses(tenantId, result.getContent());
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ShiftResponse get(Long tenantId, Long id) {
        return toResponses(tenantId, Collections.singletonList(find(tenantId, id))).get(0);
    }

    /** القيم التي سيقترحها النموذج قبل الحفظ. */
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
    public List<ShiftHistoryResponse> history(Long tenantId, Long shiftId) {
        find(tenantId, shiftId);
        Map<Long, String> userNames = new HashMap<>();
        return audit.findByShiftIdAndTenantIdOrderByCreatedAtDescIdDesc(shiftId, tenantId).stream()
                .map(log -> new ShiftHistoryResponse(log, userNames.computeIfAbsent(log.getUserId(),
                        uid -> users.findByIdAndTenantId(uid, tenantId).map(User::getFullName).orElse("-"))))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public ShiftResponse create(AuthenticatedUser me, ShiftRequest request) {
        Long tenantId = me.getTenantId();
        assertNotFuture(request.getShiftDate());
        requireSite(tenantId, request.getSiteId(), null);
        Driver driver = requireDriver(tenantId, request.getDriverId(), null);
        Equipment item = requireEquipment(tenantId, request.getEquipmentId(), null);
        assertSlotFree(tenantId, request.getEquipmentId(), request.getDriverId(), request.getShiftDate(),
                request.getShiftType(), null);

        Shift shift = new Shift(tenantId, request.getShiftDate(), request.getShiftType(), request.getSiteId(),
                request.getDriverId(), request.getEquipmentId());
        shift.setHours(scaleOrNull(request.getHours()));
        shift.setQuantity(scaleOrNull(request.getQuantity()));
        shift.setQuantityUnit(Texts.blankToNull(request.getQuantityUnit()));
        shift.setNotes(Texts.blankToNull(request.getNotes()));
        // نسخ ثابتة وقت التسجيل: تغيير الأسعار لاحقًا لا يمس هذه الوردية
        shift.setDriverWage(request.getDriverWage() != null
                ? Money.scale(request.getDriverWage())
                : Money.scale(rateFor(driver, request.getShiftType())));
        shift.setPrice(request.getPrice() != null
                ? Money.scale(request.getPrice())
                : Money.scale(item.getDefaultShiftPrice()));
        shift.setEquipmentOperatingCost(Money.scale(item.getOperatingCostPerShift()));
        shifts.saveAndFlush(shift);

        log(me, shift.getId(), ShiftAuditAction.CREATED, null, null,
                "date=" + shift.getShiftDate() + ", type=" + shift.getShiftType()
                        + ", price=" + shift.getPrice().toPlainString()
                        + ", wage=" + shift.getDriverWage().toPlainString());
        return get(tenantId, shift.getId());
    }

    @Transactional
    public ShiftResponse update(AuthenticatedUser me, Long id, ShiftRequest request) {
        Long tenantId = me.getTenantId();
        Shift shift = find(tenantId, id);
        if (shift.getStatus() == ShiftStatus.CANCELLED) {
            throw ApiException.conflict("لا يمكن تعديل وردية ملغاة. أعد تفعيلها أولًا.");
        }
        shift.requireVersion(request.getVersion());
        assertNotFuture(request.getShiftDate());
        requireSite(tenantId, request.getSiteId(), shift.getSiteId());
        Driver driver = requireDriver(tenantId, request.getDriverId(), shift.getDriverId());
        Equipment item = requireEquipment(tenantId, request.getEquipmentId(), shift.getEquipmentId());
        assertSlotFree(tenantId, request.getEquipmentId(), request.getDriverId(), request.getShiftDate(),
                request.getShiftType(), shift.getId());

        boolean driverChanged = !request.getDriverId().equals(shift.getDriverId());
        boolean typeChanged = request.getShiftType() != shift.getShiftType();
        boolean equipmentChanged = !request.getEquipmentId().equals(shift.getEquipmentId());

        // الأجر: قيمة صريحة، أو إعادة احتساب إن تغيّر السائق/نوع الوردية، وإلا تبقى النسخة القديمة
        BigDecimal wage = request.getDriverWage() != null
                ? Money.scale(request.getDriverWage())
                : (driverChanged || typeChanged ? Money.scale(rateFor(driver, request.getShiftType()))
                        : shift.getDriverWage());
        BigDecimal price = request.getPrice() != null
                ? Money.scale(request.getPrice())
                : (equipmentChanged ? Money.scale(item.getDefaultShiftPrice()) : shift.getPrice());
        BigDecimal operatingCost = equipmentChanged
                ? Money.scale(item.getOperatingCostPerShift())
                : shift.getEquipmentOperatingCost();

        List<Change> changes = new ArrayList<>();
        track(changes, "shiftDate", shift.getShiftDate(), request.getShiftDate());
        track(changes, "shiftType", shift.getShiftType(), request.getShiftType());
        track(changes, "siteId", shift.getSiteId(), request.getSiteId());
        track(changes, "driverId", shift.getDriverId(), request.getDriverId());
        track(changes, "equipmentId", shift.getEquipmentId(), request.getEquipmentId());
        track(changes, "hours", shift.getHours(), scaleOrNull(request.getHours()));
        track(changes, "quantity", shift.getQuantity(), scaleOrNull(request.getQuantity()));
        track(changes, "quantityUnit", shift.getQuantityUnit(), Texts.blankToNull(request.getQuantityUnit()));
        track(changes, "price", shift.getPrice(), price);
        track(changes, "driverWage", shift.getDriverWage(), wage);
        track(changes, "equipmentOperatingCost", shift.getEquipmentOperatingCost(), operatingCost);
        track(changes, "notes", shift.getNotes(), Texts.blankToNull(request.getNotes()));

        shift.setShiftDate(request.getShiftDate());
        shift.setShiftType(request.getShiftType());
        shift.setSiteId(request.getSiteId());
        shift.setDriverId(request.getDriverId());
        shift.setEquipmentId(request.getEquipmentId());
        shift.setHours(scaleOrNull(request.getHours()));
        shift.setQuantity(scaleOrNull(request.getQuantity()));
        shift.setQuantityUnit(Texts.blankToNull(request.getQuantityUnit()));
        shift.setPrice(price);
        shift.setDriverWage(wage);
        shift.setEquipmentOperatingCost(operatingCost);
        shift.setNotes(Texts.blankToNull(request.getNotes()));
        shifts.saveAndFlush(shift);

        for (Change change : changes) {
            log(me, shift.getId(), ShiftAuditAction.UPDATED, change.field, change.oldValue, change.newValue);
        }
        return get(tenantId, shift.getId());
    }

    @Transactional
    public ShiftResponse cancel(AuthenticatedUser me, Long id, ShiftCancelRequest request) {
        Long tenantId = me.getTenantId();
        Shift shift = find(tenantId, id);
        if (shift.getStatus() == ShiftStatus.CANCELLED) {
            throw ApiException.conflict("الوردية ملغاة بالفعل.");
        }
        shift.requireVersion(request.getVersion());
        shift.setStatus(ShiftStatus.CANCELLED);
        shift.setCancelReason(request.getReason().trim());
        shifts.saveAndFlush(shift);
        log(me, shift.getId(), ShiftAuditAction.CANCELLED, "status", ShiftStatus.COMPLETED.name(),
                shift.getCancelReason());
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
        // قد تكون المعدة أو السائق سُجّلا في نفس الوردية بعد الإلغاء
        assertSlotFree(tenantId, shift.getEquipmentId(), shift.getDriverId(), shift.getShiftDate(),
                shift.getShiftType(), shift.getId());
        shift.setStatus(ShiftStatus.COMPLETED);
        shift.setCancelReason(null);
        shifts.saveAndFlush(shift);
        log(me, shift.getId(), ShiftAuditAction.REINSTATED, "status", ShiftStatus.CANCELLED.name(),
                ShiftStatus.COMPLETED.name());
        return get(tenantId, shift.getId());
    }

    // ------------------------------------------------------------------ helpers

    private Shift find(Long tenantId, Long id) {
        return shifts.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private void assertNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(businessZone))) {
            throw ApiException.badRequest("لا يمكن تسجيل وردية بتاريخ مستقبلي. تُسجَّل الوردية بعد حدوثها.");
        }
    }

    private void assertSlotFree(Long tenantId, Long equipmentId, Long driverId, LocalDate date, ShiftType type,
                                Long excludeId) {
        long exclude = excludeId == null ? -1L : excludeId;
        if (shifts.equipmentBusy(tenantId, equipmentId, date, type, exclude)) {
            throw ApiException.conflict("هذه المعدة مسجّلة بالفعل في نفس الوردية في هذا التاريخ.");
        }
        if (shifts.driverBusy(tenantId, driverId, date, type, exclude)) {
            throw ApiException.conflict("هذا السائق أو العامل مسجّل بالفعل في نفس الوردية في هذا التاريخ.");
        }
    }

    private Site requireSite(Long tenantId, Long id, Long currentId) {
        Site site = sites.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("الموقع غير موجود."));
        if (!site.isActive() && !id.equals(currentId)) {
            throw ApiException.badRequest("الموقع معطّل. اختر موقعًا فعّالًا.");
        }
        return site;
    }

    private Driver requireDriver(Long tenantId, Long id, Long currentId) {
        Driver driver = drivers.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("السائق أو العامل غير موجود."));
        if (!driver.isActive() && !id.equals(currentId)) {
            throw ApiException.badRequest("السائق أو العامل معطّل. اختر شخصًا فعّالًا.");
        }
        return driver;
    }

    private Equipment requireEquipment(Long tenantId, Long id, Long currentId) {
        Equipment item = equipment.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));
        if (!item.isActive() && !id.equals(currentId)) {
            throw ApiException.badRequest("المعدة معطّلة. اختر معدة فعّالة.");
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

    private static BigDecimal scaleOrNull(BigDecimal value) {
        return value == null ? null : Money.scale(value);
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

    private static final class Change {
        private final String field;
        private final String oldValue;
        private final String newValue;

        private Change(String field, String oldValue, String newValue) {
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    private static void track(List<Change> changes, String field, Object oldValue, Object newValue) {
        boolean same;
        if (oldValue instanceof BigDecimal && newValue instanceof BigDecimal) {
            same = ((BigDecimal) oldValue).compareTo((BigDecimal) newValue) == 0;
        } else {
            same = Objects.equals(oldValue, newValue);
        }
        if (!same) {
            changes.add(new Change(field, text(oldValue), text(newValue)));
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : value.toString();
        return s.length() > 480 ? s.substring(0, 480) : s;
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
