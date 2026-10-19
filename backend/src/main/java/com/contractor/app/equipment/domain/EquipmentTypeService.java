package com.contractor.app.equipment.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.equipment.api.dto.EquipmentTypeRequest;
import com.contractor.app.equipment.api.dto.EquipmentTypeResponse;
import com.contractor.app.equipment.infra.EquipmentRepository;
import com.contractor.app.equipment.infra.EquipmentTypeRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentTypeService {

    private static final String NOT_FOUND = "نوع المعدة غير موجود.";
    private static final String NAME_TAKEN = "اسم النوع مستخدم بالفعل.";

    private final EquipmentTypeRepository types;
    private final EquipmentRepository equipment;

    public EquipmentTypeService(EquipmentTypeRepository types, EquipmentRepository equipment) {
        this.types = types;
        this.equipment = equipment;
    }

    @Transactional(readOnly = true)
    public List<EquipmentTypeResponse> list(Long tenantId, boolean includeInactive) {
        List<EquipmentType> found = includeInactive
                ? types.findByTenantIdOrderByNameAsc(tenantId)
                : types.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
        return found.stream().map(EquipmentTypeResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public EquipmentTypeResponse create(Long tenantId, EquipmentTypeRequest request) {
        String name = request.getName().trim();
        if (types.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw ApiException.conflict(NAME_TAKEN);
        }
        EquipmentType type = new EquipmentType(tenantId, name);
        if (request.getActive() != null) {
            type.setActive(request.getActive());
        }
        return EquipmentTypeResponse.from(types.saveAndFlush(type));
    }

    @Transactional
    public EquipmentTypeResponse update(Long tenantId, Long id, EquipmentTypeRequest request) {
        EquipmentType type = find(tenantId, id);
        type.requireVersion(request.getVersion());
        String name = request.getName().trim();
        if (types.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
            throw ApiException.conflict(NAME_TAKEN);
        }
        type.setName(name);
        if (request.getActive() != null) {
            if (!request.getActive()) {
                assertNotInUse(tenantId, id);
            }
            type.setActive(request.getActive());
        }
        return EquipmentTypeResponse.from(types.saveAndFlush(type));
    }

    @Transactional
    public void deactivate(Long tenantId, Long id) {
        EquipmentType type = find(tenantId, id);
        assertNotInUse(tenantId, id);
        type.setActive(false);
        types.saveAndFlush(type);
    }

    private void assertNotInUse(Long tenantId, Long typeId) {
        if (equipment.existsByTenantIdAndEquipmentTypeIdAndActiveTrue(tenantId, typeId)) {
            throw ApiException.conflict("لا يمكن تعطيل نوع مرتبط بمعدات فعّالة. عطّل المعدات أو غيّر نوعها أولًا.");
        }
    }

    private EquipmentType find(Long tenantId, Long id) {
        return types.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }
}
