package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.workforce.api.dto.WorkforceFinancialItemDefinitionRequest;
import com.contractor.app.workforce.api.dto.WorkforceFinancialItemDefinitionResponse;
import com.contractor.app.workforce.api.dto.UpdateWorkforceFinancialItemDefinitionRequest;
import com.contractor.app.workforce.infra.WorkforceFinancialItemDefinitionRepository;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkforceFinancialItemDefinitionService {

    private final WorkforceFinancialItemDefinitionRepository items;

    public WorkforceFinancialItemDefinitionService(WorkforceFinancialItemDefinitionRepository items) {
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<WorkforceFinancialItemDefinitionResponse> list(Long tenantId, Boolean active) {
        List<WorkforceFinancialItemDefinition> result = active == null
                ? items.findAllByTenantIdOrderByDisplayNameAscIdAsc(tenantId)
                : items.findAllByTenantIdAndActiveOrderByDisplayNameAscIdAsc(tenantId, active);
        return result.stream().map(WorkforceFinancialItemDefinitionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WorkforceFinancialItemDefinitionResponse get(Long tenantId, Long itemId) {
        WorkforceFinancialItemDefinition item = items.findByIdAndTenantId(itemId, tenantId)
                .orElseThrow(() -> ApiException.notFound("البند المالي غير موجود."));
        return WorkforceFinancialItemDefinitionResponse.from(item);
    }

    @Transactional
    public WorkforceFinancialItemDefinitionResponse create(
            Long tenantId, WorkforceFinancialItemDefinitionRequest request) {
        String code = normalizeCode(request.getItemCode());
        if (!request.isAllowRecurring() && !request.isAllowOneOff()) {
            throw ApiException.badRequest("يجب إتاحة البند للاتفاقيات أو للاستخدام الفردي على الأقل.");
        }
        if (items.existsByTenantIdAndItemCode(tenantId, code)) {
            throw ApiException.conflict("كود البند مستخدم بالفعل داخل الشركة.");
        }
        WorkforceFinancialItemDefinition item = new WorkforceFinancialItemDefinition(
                tenantId, code, request.getDisplayName().trim(), trimToNull(request.getDescription()),
                request.getFamily(), request.getCalculationMethod(), request.isAllowRecurring(),
                request.isAllowOneOff(), request.isRequiresApproval());
        try {
            return WorkforceFinancialItemDefinitionResponse.from(items.saveAndFlush(item));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("كود البند مستخدم بالفعل داخل الشركة.");
        }
    }

    @Transactional
    public WorkforceFinancialItemDefinitionResponse update(
            Long tenantId, Long itemId, UpdateWorkforceFinancialItemDefinitionRequest request) {
        WorkforceFinancialItemDefinition item = lock(tenantId, itemId);
        if (!request.isAllowRecurring() && !request.isAllowOneOff()) {
            throw ApiException.badRequest("يجب إتاحة البند للاتفاقيات أو للاستخدام الفردي على الأقل.");
        }
        item.updateMetadata(request.getVersion(), request.getDisplayName().trim(),
                trimToNull(request.getDescription()), request.isAllowRecurring(),
                request.isAllowOneOff(), request.isRequiresApproval());
        return WorkforceFinancialItemDefinitionResponse.from(items.saveAndFlush(item));
    }

    @Transactional
    public WorkforceFinancialItemDefinitionResponse deactivate(
            Long tenantId, Long itemId, Long expectedVersion) {
        WorkforceFinancialItemDefinition item = lock(tenantId, itemId);
        item.deactivate(expectedVersion);
        return WorkforceFinancialItemDefinitionResponse.from(items.saveAndFlush(item));
    }

    private WorkforceFinancialItemDefinition lock(Long tenantId, Long itemId) {
        return items.findByIdAndTenantIdForUpdate(itemId, tenantId)
                .orElseThrow(() -> ApiException.notFound("البند المالي غير موجود."));
    }

    private String normalizeCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw ApiException.badRequest("كود البند مطلوب.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
