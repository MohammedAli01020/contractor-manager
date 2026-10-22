package com.contractor.app.shift.domain;

import com.contractor.app.audit.domain.AuditAction;
import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditService;
import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.money.Money;
import com.contractor.app.common.util.Texts;
import com.contractor.app.driver.domain.Driver;
import com.contractor.app.driver.domain.DriverAvailability;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.equipment.domain.Equipment;
import com.contractor.app.equipment.domain.EquipmentStatus;
import com.contractor.app.equipment.infra.EquipmentRepository;
import com.contractor.app.shift.api.dto.ShiftCancelRequest;
import com.contractor.app.shift.api.dto.ShiftDefaultsResponse;
import com.contractor.app.shift.api.dto.ShiftRequest;
import com.contractor.app.shift.api.dto.ShiftResponse;
import com.contractor.app.shift.infra.ShiftRepository;
import com.contractor.app.site.domain.Site;
import com.contractor.app.site.infra.SiteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.TreeSet;
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
    private final SiteRepository sites;
    private final DriverRepository drivers;
    private final EquipmentRepository equipment;
    private final AuditService audit;
    private final ZoneId businessZone;

    public ShiftService(ShiftRepository shifts, SiteRepository sites, DriverRepository drivers,
                        EquipmentRepository equipment, AuditService audit,
                        @Value("${app.business-timezone:Africa/Cairo}") String businessTimezone) {
        this.shifts = shifts;
        this.sites = sites;
        this.drivers = drivers;
        this.equipment = equipment;
        this.audit = audit;
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
                PageParams.of(page, size, Sort.by(Sort.Order.desc("shiftDate"), Sort.Order.desc("id"))));
        return new PageResponse<>(toResponses(tenantId, result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** الورديات الشغالة الآن (الأقدم أولًا ليظهر المنسي في الأعلى). */
    @Transactional(readOnly = true)
    public List<ShiftResponse> running(Long tenantId) {
        List<Shift> list = new ArrayList<>(
                shifts.findByTenantIdAndStatusOrderByShiftDateDescIdDesc(tenantId, ShiftStatus.IN_PROGRESS));
        Collections.reverse(list);
        return toResponses(tenantId, list);
    }

    @Transactional(readOnly = true)
    public ShiftResponse get(Long tenantId, Long id) {
        return toResponses(tenantId, Collections.singletonList(find(tenantId, id))).get(0);
    }

    /** القيم التي سيقترحها النموذج قبل الحفظ. */
    @Transactional(readOnly = true)
    public ShiftDefaultsResponse defaults(Long tenantId, Long driverId, Long equipmentId, ShiftType type) {
        if (driverId == null && equipmentId == null) {
            throw ApiException.badRequest("اختار سائقًا أو معدة لحساب القيم الافتراضية.");
        }
        Driver driver = driverId == null ? null : drivers.findByIdAndTenantId(driverId, tenantId)
                .orElseThrow(() -> ApiException.notFound("السائق أو العامل غير موجود."));
        Equipment item = equipmentId == null ? null : equipment.findByIdAndTenantId(equipmentId, tenantId)
                .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));
        return new ShiftDefaultsResponse(
                driver == null ? null : Money.scale(rateFor(driver, type)),
                item == null ? null : Money.scale(item.getDefaultShiftPrice()),
                item == null ? null : Money.scale(item.getOperatingCostPerShift()));
    }

    // ------------------------------------------------------------------ commands

    /** بدء وردية: تُسجَّل "شغالة". */
    @Transactional
    public ShiftResponse start(Long tenantId, ShiftRequest request) {
        LocalDate date = request.getShiftDate() == null ? today() : request.getShiftDate();
        assertNotFuture(date);
        // Serialize starts for the same driver/equipment across concurrent requests and instances.
        drivers.findByIdAndTenantIdForUpdate(request.getDriverId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("السائق أو العامل غير موجود."));
        equipment.findByIdAndTenantIdForUpdate(request.getEquipmentId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));

        Driver driver = requireDriver(tenantId, request.getDriverId(), null);
        Equipment item = requireEquipment(tenantId, request.getEquipmentId(), null);
        // Do non-locking validation reads only after the resource locks are held. This avoids
        // establishing a MySQL REPEATABLE READ snapshot before waiting for a competing start.
        requireSite(tenantId, request.getSiteId(), null);
        assertDriverNotRunning(tenantId, request.getDriverId());
        assertEquipmentNotRunning(tenantId, request.getEquipmentId());

        Shift shift = new Shift(tenantId, date, request.getShiftType(), request.getSiteId(), request.getDriverId(),
                request.getEquipmentId());
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

        audit.record(tenantId, AuditEntityType.SHIFT, shift.getId(), AuditAction.CREATED, null, null,
                "date=" + shift.getShiftDate() + ", type=" + shift.getShiftType()
                        + ", price=" + shift.getPrice().toPlainString()
                        + ", wage=" + shift.getDriverWage().toPlainString());
        return get(tenantId, shift.getId());
    }

    @Transactional
    public ShiftResponse update(Long tenantId, Long id, ShiftRequest request) {
        Shift shift = findForUpdate(tenantId, id);
        if (shift.getStatus() == ShiftStatus.CANCELLED) {
            throw ApiException.conflict("لا يمكن تعديل وردية ملغاة. أعد تفعيلها أولًا.");
        }
        shift.requireVersion(request.getVersion());
        if (request.getShiftDate() == null) {
            throw ApiException.badRequest("تاريخ الوردية مطلوب عند التعديل.");
        }
        assertNotFuture(request.getShiftDate());
        lockAndValidateActiveShiftUpdate(tenantId, shift, request);
        requireSite(tenantId, request.getSiteId(), shift.getSiteId());
        Driver driver = requireDriver(tenantId, request.getDriverId(), shift.getDriverId());
        Equipment item = requireEquipment(tenantId, request.getEquipmentId(), shift.getEquipmentId());

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

        AuditService.ChangeSet changes = new AuditService.ChangeSet()
                .track("shiftDate", shift.getShiftDate(), request.getShiftDate())
                .track("shiftType", shift.getShiftType(), request.getShiftType())
                .track("siteId", shift.getSiteId(), request.getSiteId())
                .track("driverId", shift.getDriverId(), request.getDriverId())
                .track("equipmentId", shift.getEquipmentId(), request.getEquipmentId())
                .track("price", shift.getPrice(), price)
                .track("driverWage", shift.getDriverWage(), wage)
                .track("equipmentOperatingCost", shift.getEquipmentOperatingCost(), operatingCost)
                .track("notes", shift.getNotes(), Texts.blankToNull(request.getNotes()));

        shift.setShiftDate(request.getShiftDate());
        shift.setShiftType(request.getShiftType());
        shift.setSiteId(request.getSiteId());
        shift.setDriverId(request.getDriverId());
        shift.setEquipmentId(request.getEquipmentId());
        shift.setPrice(price);
        shift.setDriverWage(wage);
        shift.setEquipmentOperatingCost(operatingCost);
        shift.setNotes(Texts.blankToNull(request.getNotes()));
        shifts.saveAndFlush(shift);

        audit.recordChanges(tenantId, AuditEntityType.SHIFT, shift.getId(), changes);
        return get(tenantId, shift.getId());
    }

    /** إنهاء: يغيّر المؤشر فقط، ووقت الضغط ومن ضغط يُسجَّلان. */
    @Transactional
    public ShiftResponse finish(Long tenantId, Long id, Long version) {
        Shift shift = findForUpdate(tenantId, id);
        if (shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw ApiException.conflict("الوردية ليست شغالة.");
        }
        shift.requireVersion(version);
        shift.setStatus(ShiftStatus.COMPLETED);
        shift.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        shifts.saveAndFlush(shift);
        audit.record(tenantId, AuditEntityType.SHIFT, shift.getId(), AuditAction.FINISHED, "status",
                ShiftStatus.IN_PROGRESS.name(), ShiftStatus.COMPLETED.name());
        return get(tenantId, shift.getId());
    }

    @Transactional
    public ShiftResponse cancel(Long tenantId, Long id, ShiftCancelRequest request) {
        Shift shift = findForUpdate(tenantId, id);
        if (shift.getStatus() == ShiftStatus.CANCELLED) {
            throw ApiException.conflict("الوردية ملغاة بالفعل.");
        }
        shift.requireVersion(request.getVersion());
        ShiftStatus previous = shift.getStatus();
        shift.setStatus(ShiftStatus.CANCELLED);
        shift.setCancelReason(request.getReason().trim());
        shifts.saveAndFlush(shift);
        audit.record(tenantId, AuditEntityType.SHIFT, shift.getId(), AuditAction.CANCELLED, "status",
                previous.name(), "CANCELLED: " + shift.getCancelReason());
        return get(tenantId, shift.getId());
    }

    @Transactional
    public ShiftResponse reinstate(Long tenantId, Long id, Long version) {
        Shift shift = findForUpdate(tenantId, id);
        if (shift.getStatus() != ShiftStatus.CANCELLED) {
            throw ApiException.conflict("الوردية ليست ملغاة.");
        }
        shift.requireVersion(version);

        ShiftStatus restoredStatus;
        if (shift.getFinishedAt() != null) {
            // وردية كانت منتهية قبل إلغائها: أعدها منتهية مع الاحتفاظ بوقت الإنهاء والقيم المحفوظة.
            restoredStatus = ShiftStatus.COMPLETED;
        } else {
            // وردية أُلغيت أثناء التشغيل: أعد حجز مواردها بنفس ترتيب الأقفال المستخدم عند البدء.
            drivers.findByIdAndTenantIdForUpdate(shift.getDriverId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("السائق أو العامل غير موجود."));
            equipment.findByIdAndTenantIdForUpdate(shift.getEquipmentId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));

            Driver driver = requireDriver(tenantId, shift.getDriverId(), null);
            Equipment item = requireEquipment(tenantId, shift.getEquipmentId(), null);

            Shift driverConflict = shifts
                    .findFirstByTenantIdAndDriverIdAndStatusOrderByIdAsc(
                            tenantId, shift.getDriverId(), ShiftStatus.IN_PROGRESS)
                    .orElse(null);
            if (driverConflict != null) {
                throw ApiException.activeShiftConflict(activeResourceConflict(
                        "السائق " + driver.getName(), driverConflict, tenantId));
            }

            Shift equipmentConflict = shifts
                    .findFirstByTenantIdAndEquipmentIdAndStatusOrderByIdAsc(
                            tenantId, shift.getEquipmentId(), ShiftStatus.IN_PROGRESS)
                    .orElse(null);
            if (equipmentConflict != null) {
                String equipmentName = item.getCode() == null ? "المعدة" : "المعدة " + item.getCode();
                throw ApiException.activeShiftConflict(activeResourceConflict(
                        equipmentName, equipmentConflict, tenantId));
            }
            restoredStatus = ShiftStatus.IN_PROGRESS;
        }

        shift.setStatus(restoredStatus);
        shift.setCancelReason(null);
        shifts.saveAndFlush(shift);
        audit.record(tenantId, AuditEntityType.SHIFT, shift.getId(), AuditAction.REINSTATED, "status",
                ShiftStatus.CANCELLED.name(), restoredStatus.name());
        return get(tenantId, shift.getId());
    }

    private String activeResourceConflict(String resource, Shift conflictingShift, Long tenantId) {
        String siteName = sites.findByIdAndTenantId(conflictingShift.getSiteId(), tenantId)
                .map(Site::getName).orElse("موقع غير معروف");
        return resource + " مشغول في الوردية رقم " + conflictingShift.getId()
                + " بموقع " + siteName + ". أنهِ الوردية الأخرى أو اختر إجراءً آخر.";
    }

    // ------------------------------------------------------------------ helpers

    private Shift find(Long tenantId, Long id) {
        return shifts.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    /** Lock the shift row before a state-changing command to serialize finish/cancel/reinstate/update. */
    private Shift findForUpdate(Long tenantId, Long id) {
        return shifts.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private void assertDriverNotRunning(Long tenantId, Long driverId) {
        if (shifts.existsByTenantIdAndDriverIdAndStatus(tenantId, driverId, ShiftStatus.IN_PROGRESS)) {
            throw ApiException.activeShiftConflict("السائق أو العامل لديه وردية نشطة بالفعل.");
        }
    }

    private void assertEquipmentNotRunning(Long tenantId, Long equipmentId) {
        if (shifts.existsByTenantIdAndEquipmentIdAndStatus(tenantId, equipmentId, ShiftStatus.IN_PROGRESS)) {
            throw ApiException.activeShiftConflict("المعدة مستخدمة في وردية نشطة بالفعل.");
        }
    }

    /**
     * If an active shift changes its assigned resources, reserve affected rows in a stable order.
     * The generated-column unique indexes remain the final protection against races.
     */
    private void lockAndValidateActiveShiftUpdate(Long tenantId, Shift shift, ShiftRequest request) {
        if (shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            return;
        }

        Set<Long> driverIds = new TreeSet<>(Arrays.asList(shift.getDriverId(), request.getDriverId()));
        Set<Long> equipmentIds = new TreeSet<>(Arrays.asList(shift.getEquipmentId(), request.getEquipmentId()));

        for (Long driverId : driverIds) {
            drivers.findByIdAndTenantIdForUpdate(driverId, tenantId)
                    .orElseThrow(() -> ApiException.notFound("السائق أو العامل غير موجود."));
        }
        for (Long equipmentId : equipmentIds) {
            equipment.findByIdAndTenantIdForUpdate(equipmentId, tenantId)
                    .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));
        }

        if (shifts.existsByTenantIdAndDriverIdAndStatusAndIdNot(
                tenantId, request.getDriverId(), ShiftStatus.IN_PROGRESS, shift.getId())) {
            throw ApiException.activeShiftConflict("السائق أو العامل لديه وردية نشطة بالفعل.");
        }
        if (shifts.existsByTenantIdAndEquipmentIdAndStatusAndIdNot(
                tenantId, request.getEquipmentId(), ShiftStatus.IN_PROGRESS, shift.getId())) {
            throw ApiException.activeShiftConflict("المعدة مستخدمة في وردية نشطة بالفعل.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(businessZone);
    }

    private void assertNotFuture(LocalDate date) {
        if (date.isAfter(today())) {
            throw ApiException.badRequest("لا يمكن تسجيل وردية بتاريخ مستقبلي.");
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

    /** المنع يسري على كل التواريخ، لكن لا يُعاد فحص سائق لم يتغيّر في وردية قائمة. */
    private Driver requireDriver(Long tenantId, Long id, Long currentId) {
        Driver driver = drivers.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("السائق أو العامل غير موجود."));
        if (!id.equals(currentId)) {
            if (!driver.isActive()) {
                throw ApiException.badRequest("السائق أو العامل معطّل. اختر شخصًا فعّالًا.");
            }
            if (driver.getAvailability() != DriverAvailability.AVAILABLE) {
                String reason = driver.getUnavailableReason();
                throw ApiException.badRequest("السائق أو العامل غير متاح" + (reason == null ? "." : ": " + reason));
            }
        }
        return driver;
    }

    private Equipment requireEquipment(Long tenantId, Long id, Long currentId) {
        Equipment item = equipment.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));
        if (!id.equals(currentId)) {
            if (!item.isActive()) {
                throw ApiException.badRequest("المعدة معطّلة. اختر معدة فعّالة.");
            }
            if (item.getStatus() == EquipmentStatus.MAINTENANCE) {
                throw ApiException.badRequest("المعدة في الصيانة ولا يمكن تسجيل وردية لها.");
            }
            if (item.getStatus() == EquipmentStatus.OUT_OF_SERVICE) {
                throw ApiException.badRequest("المعدة خارج الخدمة ولا يمكن تسجيل وردية لها.");
            }
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
