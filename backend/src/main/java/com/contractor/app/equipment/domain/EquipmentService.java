package com.contractor.app.equipment.domain;

import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.money.Money;
import com.contractor.app.common.util.Texts;
import com.contractor.app.equipment.api.dto.EquipmentRequest;
import com.contractor.app.equipment.api.dto.EquipmentResponse;
import com.contractor.app.equipment.infra.EquipmentRepository;
import com.contractor.app.equipment.infra.EquipmentTypeRepository;
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

    public EquipmentService(EquipmentRepository equipment, EquipmentTypeRepository types) {
        this.equipment = equipment;
        this.types = types;
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
        return toResponse(tenantId, equipment.saveAndFlush(item));
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
        item.setCode(code);
        item.setEquipmentTypeId(request.getEquipmentTypeId());
        apply(item, request);
        if (request.getStatus() != null) {
            item.setStatus(request.getStatus());
        }
        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }
        return toResponse(tenantId, equipment.saveAndFlush(item));
    }

    @Transactional
    public void deactivate(Long tenantId, Long id) {
        Equipment item = find(tenantId, id);
        item.setActive(false);
        equipment.saveAndFlush(item);
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
