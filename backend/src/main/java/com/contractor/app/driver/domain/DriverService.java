package com.contractor.app.driver.domain;

import com.contractor.app.audit.domain.AuditAction;
import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditService;
import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.money.Money;
import com.contractor.app.common.util.Texts;
import com.contractor.app.driver.api.dto.DriverRequest;
import com.contractor.app.driver.api.dto.DriverResponse;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.shift.domain.ShiftStatus;
import com.contractor.app.shift.infra.ShiftRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {

    private static final String NOT_FOUND = "السائق أو العامل غير موجود.";

    private final DriverRepository drivers;
    private final ShiftRepository shifts;
    private final AuditService audit;

    public DriverService(DriverRepository drivers, ShiftRepository shifts, AuditService audit) {
        this.drivers = drivers;
        this.shifts = shifts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<DriverResponse> list(Long tenantId, String q, WorkerType type, boolean includeInactive,
                                             int page, int size) {
        Page<Driver> result = drivers.findAll(filter(tenantId, q, type, includeInactive),
                PageParams.of(page, size, Sort.by("name").ascending()));
        return PageResponse.of(result, DriverResponse::from);
    }

    @Transactional(readOnly = true)
    public DriverResponse get(Long tenantId, Long id) {
        return DriverResponse.from(find(tenantId, id));
    }

    @Transactional
    public DriverResponse create(Long tenantId, DriverRequest request) {
        Driver driver = new Driver(tenantId, request.getName().trim(), request.getWorkerType());
        applyBasics(driver, request);
        applyAvailability(driver, request);
        if (request.getActive() != null) {
            driver.setActive(request.getActive());
        }
        drivers.saveAndFlush(driver);
        audit.record(tenantId, AuditEntityType.DRIVER, driver.getId(), AuditAction.CREATED, null, null,
                "name=" + driver.getName() + ", morning=" + driver.getMorningRate().toPlainString()
                        + ", evening=" + driver.getEveningRate().toPlainString()
                        + ", night=" + driver.getNightRate().toPlainString());
        return DriverResponse.from(driver);
    }

    @Transactional
    public DriverResponse update(Long tenantId, Long id, DriverRequest request) {
        Driver driver = find(tenantId, id);
        driver.requireVersion(request.getVersion());

        boolean becomingUnavailable = request.getAvailability() == DriverAvailability.UNAVAILABLE
                && driver.getAvailability() == DriverAvailability.AVAILABLE;
        boolean deactivating = Boolean.FALSE.equals(request.getActive()) && driver.isActive();
        if (becomingUnavailable || deactivating) {
            assertNoRunningShift(tenantId, id);
        }

        AuditService.ChangeSet changes = new AuditService.ChangeSet();
        String oldName = driver.getName();
        WorkerType oldType = driver.getWorkerType();
        java.math.BigDecimal oldMorning = driver.getMorningRate();
        java.math.BigDecimal oldEvening = driver.getEveningRate();
        java.math.BigDecimal oldNight = driver.getNightRate();
        DriverAvailability oldAvailability = driver.getAvailability();
        String oldReason = driver.getUnavailableReason();
        boolean oldActive = driver.isActive();

        driver.setName(request.getName().trim());
        driver.setWorkerType(request.getWorkerType());
        applyBasics(driver, request);
        applyAvailability(driver, request);
        if (request.getActive() != null) {
            driver.setActive(request.getActive());
        }
        drivers.saveAndFlush(driver);

        changes.track("name", oldName, driver.getName())
                .track("workerType", oldType, driver.getWorkerType())
                .track("morningRate", oldMorning, driver.getMorningRate())
                .track("eveningRate", oldEvening, driver.getEveningRate())
                .track("nightRate", oldNight, driver.getNightRate())
                .track("availability", oldAvailability, driver.getAvailability())
                .track("unavailableReason", oldReason, driver.getUnavailableReason())
                .track("active", oldActive, driver.isActive());
        audit.recordChanges(tenantId, AuditEntityType.DRIVER, id, changes);
        return DriverResponse.from(driver);
    }

    @Transactional
    public void deactivate(Long tenantId, Long id) {
        Driver driver = find(tenantId, id);
        if (!driver.isActive()) {
            return;
        }
        assertNoRunningShift(tenantId, id);
        driver.setActive(false);
        drivers.saveAndFlush(driver);
        audit.record(tenantId, AuditEntityType.DRIVER, id, AuditAction.DEACTIVATED, "active", "true", "false");
    }

    private void assertNoRunningShift(Long tenantId, Long driverId) {
        if (shifts.existsByTenantIdAndDriverIdAndStatus(tenantId, driverId, ShiftStatus.IN_PROGRESS)) {
            throw ApiException.conflict("السائق أو العامل في وردية شغالة. أنهِ الوردية أولًا.");
        }
    }

    private void applyBasics(Driver driver, DriverRequest request) {
        driver.setPhone(Texts.blankToNull(request.getPhone()));
        driver.setNationalId(Texts.blankToNull(request.getNationalId()));
        driver.setMorningRate(Money.scale(request.getMorningRate()));
        driver.setEveningRate(Money.scale(request.getEveningRate()));
        driver.setNightRate(Money.scale(request.getNightRate()));
        driver.setNotes(Texts.blankToNull(request.getNotes()));
    }

    /** إن لم تُرسل الحالة تبقى كما هي. "غير متاح" يتطلب سببًا نصيًا. */
    private void applyAvailability(Driver driver, DriverRequest request) {
        if (request.getAvailability() == null) {
            return;
        }
        if (request.getAvailability() == DriverAvailability.UNAVAILABLE) {
            String reason = Texts.blankToNull(request.getUnavailableReason());
            if (reason == null) {
                throw ApiException.badRequest("سبب عدم التوفر مطلوب.");
            }
            driver.setAvailability(DriverAvailability.UNAVAILABLE);
            driver.setUnavailableReason(reason);
        } else {
            driver.setAvailability(DriverAvailability.AVAILABLE);
            driver.setUnavailableReason(null);
        }
    }

    private Driver find(Long tenantId, Long id) {
        return drivers.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private static Specification<Driver> filter(Long tenantId, String q, WorkerType type, boolean includeInactive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!includeInactive) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("workerType"), type));
            }
            if (q != null && !q.trim().isEmpty()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), like),
                        cb.like(cb.lower(root.<String>get("phone")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
