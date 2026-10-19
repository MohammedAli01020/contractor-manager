package com.contractor.app.driver.domain;

import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.money.Money;
import com.contractor.app.common.util.Texts;
import com.contractor.app.driver.api.dto.DriverRequest;
import com.contractor.app.driver.api.dto.DriverResponse;
import com.contractor.app.driver.infra.DriverRepository;
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

    public DriverService(DriverRepository drivers) {
        this.drivers = drivers;
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
        apply(driver, request);
        if (request.getActive() != null) {
            driver.setActive(request.getActive());
        }
        return DriverResponse.from(drivers.saveAndFlush(driver));
    }

    @Transactional
    public DriverResponse update(Long tenantId, Long id, DriverRequest request) {
        Driver driver = find(tenantId, id);
        driver.requireVersion(request.getVersion());
        driver.setName(request.getName().trim());
        driver.setWorkerType(request.getWorkerType());
        apply(driver, request);
        if (request.getActive() != null) {
            driver.setActive(request.getActive());
        }
        return DriverResponse.from(drivers.saveAndFlush(driver));
    }

    @Transactional
    public void deactivate(Long tenantId, Long id) {
        Driver driver = find(tenantId, id);
        driver.setActive(false);
        drivers.saveAndFlush(driver);
    }

    private void apply(Driver driver, DriverRequest request) {
        driver.setPhone(Texts.blankToNull(request.getPhone()));
        driver.setNationalId(Texts.blankToNull(request.getNationalId()));
        driver.setMorningRate(Money.scale(request.getMorningRate()));
        driver.setEveningRate(Money.scale(request.getEveningRate()));
        driver.setNightRate(Money.scale(request.getNightRate()));
        driver.setNotes(Texts.blankToNull(request.getNotes()));
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
