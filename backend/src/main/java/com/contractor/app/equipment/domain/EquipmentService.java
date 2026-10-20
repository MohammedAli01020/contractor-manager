package com.contractor.app.equipment.domain;

import com.contractor.app.audit.domain.AuditAction;
import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditService;
import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.money.Money;
import com.contractor.app.common.util.Texts;
import com.contractor.app.equipment.api.dto.EquipmentRequest;
import com.contractor.app.equipment.api.dto.EquipmentResponse;
import com.contractor.app.equipment.infra.EquipmentRepository;
import com.contractor.app.equipment.infra.EquipmentTypeRepository;
import com.contractor.app.shift.domain.ShiftStatus;
import com.contractor.app.shift.infra.ShiftRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentService {

    private static final String NOT_FOUND = "المعدة غير موجودة.";
    private static final String CODE_TAKEN = "كود المعدة مستخدم بالفعل.";

    private final EquipmentRepository equipment;
    private final EquipmentTypeRepository types;
    private final ShiftRepository shifts;
    private final AuditService audit;

    public EquipmentService(EquipmentRepository equipment, EquipmentTypeRepository types, ShiftRepository shifts,
                            AuditService audit) {
        this.equipment = equipment;
        this.types = types;
        this.shifts = shifts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<EquipmentResponse> list(Long tenantId, String q, Long typeId, EquipmentStatus status,
                                                boolean includeInactive, int page, int size) {
        Page<Equipment> result = equipment.findAll(filter(tenantId, q, typeId, status, includeInactive),
                PageParams.of(page, size, Sort.by("code").ascending()));
        Map<Long, String> names = typeNames(tenantId, result.getContent().stream()
                .map(Equipment::getEquipmentTypeId).collect(Collectors.toSet()));
        return PageResponse.of(result, e -> EquipmentResponse.from(e, names.get(e.getEquipmentTypeId())));
    }

    @Transactional(readOnly = true)
    public EquipmentResponse get(Long tenantId, Long id) {
        return toResponse(tenantId, find(tenantId, id));
    }

    @Transactional
    public EquipmentResponse create(Long tenantId, EquipmentRequest request) {
        String code = request.getCode().trim();
        if (equipment.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
            throw ApiException.conflict(CODE_TAKEN);
        }
        requireType(tenantId, request.getEquipmentTypeId(), null);
        Equipment item = new Equipment(tenantId, code, request.getEquipmentTypeId());
        apply(item, request);
        if (request.getStatus() != null) {
            item.setStatus(request.getStatus());
        }
        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }
        equipment.saveAndFlush(item);
        audit.record(tenantId, AuditEntityType.EQUIPMENT, item.getId(), AuditAction.CREATED, null, null,
                "code=" + item.getCode() + ", cost=" + item.getOperatingCostPerShift().toPlainString()
                        + ", price=" + item.getDefaultShiftPrice().toPlainString());
        return toResponse(tenantId, item);
    }

    @Transactional
    public EquipmentResponse update(Long tenantId, Long id, EquipmentRequest request) {
        Equipment item = find(tenantId, id);
        item.requireVersion(request.getVersion());
        String code = request.getCode().trim();
        if (equipment.existsByTenantIdAndCodeIgnoreCaseAndIdNot(tenantId, code, id)) {
            throw ApiException.conflict(CODE_TAKEN);
        }
        requireType(tenantId, request.getEquipmentTypeId(), item.getEquipmentTypeId());

        boolean leavingService = request.getStatus() != null && request.getStatus() != EquipmentStatus.AVAILABLE
                && item.getStatus() == EquipmentStatus.AVAILABLE;
        boolean deactivating = Boolean.FALSE.equals(request.getActive()) && item.isActive();
        if (leavingService || deactivating) {
            assertNoRunningShift(tenantId, id);
        }

        String oldCode = item.getCode();
        String oldName = item.getName();
        Long oldType = item.getEquipmentTypeId();
        BigDecimal oldCost = item.getOperatingCostPerShift();
        BigDecimal oldPrice = item.getDefaultShiftPrice();
        EquipmentStatus oldStatus = item.getStatus();
        boolean oldActive = item.isActive();

        item.setCode(code);
        item.setEquipmentTypeId(request.getEquipmentTypeId());
        apply(item, request);
        if (request.getStatus() != null) {
            item.setStatus(request.getStatus());
        }
        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }
        equipment.saveAndFlush(item);

        AuditService.ChangeSet changes = new AuditService.ChangeSet()
                .track("code", oldCode, item.getCode())
                .track("name", oldName, item.getName())
                .track("equipmentTypeId", oldType, item.getEquipmentTypeId())
                .track("operatingCostPerShift", oldCost, item.getOperatingCostPerShift())
                .track("defaultShiftPrice", oldPrice, item.getDefaultShiftPrice())
                .track("status", oldStatus, item.getStatus())
                .track("active", oldActive, item.isActive());
        audit.recordChanges(tenantId, AuditEntityType.EQUIPMENT, id, changes);
        return toResponse(tenantId, item);
    }

    @Transactional
    public void deactivate(Long tenantId, Long id) {
        Equipment item = find(tenantId, id);
        if (!item.isActive()) {
            return;
        }
        assertNoRunningShift(tenantId, id);
        item.setActive(false);
        equipment.saveAndFlush(item);
        audit.record(tenantId, AuditEntityType.EQUIPMENT, id, AuditAction.DEACTIVATED, "active", "true", "false");
    }

    private void assertNoRunningShift(Long tenantId, Long equipmentId) {
        if (shifts.existsByTenantIdAndEquipmentIdAndStatus(tenantId, equipmentId, ShiftStatus.IN_PROGRESS)) {
            throw ApiException.conflict("المعدة في وردية شغالة. أنهِ الوردية أولًا.");
        }
    }

    private void apply(Equipment item, EquipmentRequest request) {
        item.setName(Texts.blankToNull(request.getName()));
        item.setOperatingCostPerShift(Money.scale(request.getOperatingCostPerShift()));
        if (request.getDefaultShiftPrice() != null) {
            item.setDefaultShiftPrice(Money.scale(request.getDefaultShiftPrice()));
        }
        item.setNotes(Texts.blankToNull(request.getNotes()));
    }

    /** النوع لازم يكون من نفس المقاول، وفعّالًا إلا إذا لم يتغير النوع. */
    private void requireType(Long tenantId, Long typeId, Long currentTypeId) {
        EquipmentType type = types.findByIdAndTenantId(typeId, tenantId)
                .orElseThrow(() -> ApiException.notFound("نوع المعدة غير موجود."));
        if (!type.isActive() && !typeId.equals(currentTypeId)) {
            throw ApiException.badRequest("نوع المعدة معطّل. اختر نوعًا فعّالًا.");
        }
    }

    private Equipment find(Long tenantId, Long id) {
        return equipment.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private EquipmentResponse toResponse(Long tenantId, Equipment item) {
        Map<Long, String> names = typeNames(tenantId, Collections.singleton(item.getEquipmentTypeId()));
        return EquipmentResponse.from(item, names.get(item.getEquipmentTypeId()));
    }

    private Map<Long, String> typeNames(Long tenantId, Collection<Long> typeIds) {
        if (typeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> names = new HashMap<>();
        types.findByTenantIdAndIdIn(tenantId, typeIds).forEach(t -> names.put(t.getId(), t.getName()));
        return names;
    }

    private static Specification<Equipment> filter(Long tenantId, String q, Long typeId, EquipmentStatus status,
                                                   boolean includeInactive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!includeInactive) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (typeId != null) {
                predicates.add(cb.equal(root.get("equipmentTypeId"), typeId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.trim().isEmpty()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("code")), like),
                        cb.like(cb.lower(root.<String>get("name")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
