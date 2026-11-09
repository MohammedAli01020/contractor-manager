package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.workforce.api.dto.CompensationAgreementRequest;
import com.contractor.app.workforce.api.dto.CompensationAgreementResponse;
import com.contractor.app.workforce.api.dto.CompensationComponentRequest;
import com.contractor.app.workforce.infra.CompensationAgreementRepository;
import com.contractor.app.workforce.infra.CompensationComponentRepository;
import com.contractor.app.workforce.infra.WorkforceAssignmentRepository;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import com.contractor.app.workforce.infra.WorkforceFinancialItemDefinitionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import com.contractor.app.workforce.domain.WorkforceFinancialItemDefinition;
import com.contractor.app.workforce.domain.WorkforceFinancialItemFamily;
import com.contractor.app.workforce.domain.WorkforceFinancialItemCalculationMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompensationAgreementService {

    private final WorkforceMemberRepository members;
    private final WorkforceAssignmentRepository assignments;
    private final CompensationAgreementRepository agreements;
    private final CompensationComponentRepository components;
    private final WorkforceFinancialItemDefinitionRepository financialItems;

    public CompensationAgreementService(WorkforceMemberRepository members,
                                        WorkforceAssignmentRepository assignments,
                                        CompensationAgreementRepository agreements,
                                        CompensationComponentRepository components,
                                        WorkforceFinancialItemDefinitionRepository financialItems) {
        this.members = members;
        this.assignments = assignments;
        this.agreements = agreements;
        this.components = components;
        this.financialItems = financialItems;
    }

    @Transactional(readOnly = true)
    public List<CompensationAgreementResponse> list(Long tenantId, Long memberId) {
        requireMember(tenantId, memberId);
        return agreements.findAllByTenantIdAndWorkforceMemberIdOrderByEffectiveFromDescIdDesc(
                        tenantId, memberId)
                .stream().map(a -> response(a, components.findAllByTenantIdAndCompensationAgreementIdOrderBySortOrderAscIdAsc(
                        tenantId, a.getId()))).collect(Collectors.toList());
    }

    @Transactional
    public CompensationAgreementResponse createDraft(Long tenantId, Long memberId, Long actorId,
                                                       CompensationAgreementRequest request) {
        lockMember(tenantId, memberId);
        validateRequestDates(request);
        validateAssignmentScope(tenantId, memberId, request.getWorkforceAssignmentId(),
                request.getEffectiveFrom(), request.getEffectiveTo());
        validateReplacementDraft(tenantId, memberId, request);

        CompensationAgreement agreement = new CompensationAgreement(tenantId, memberId,
                request.getWorkforceAssignmentId(), request.getAgreementReference(),
                request.getEffectiveFrom(), request.getEffectiveTo(), request.getCurrencyCode(),
                request.getSource() == null ? CompensationSource.MANUAL : request.getSource(),
                actorId, request.getSupersedesAgreementId(), request.getChangeReason());
        agreement = agreements.saveAndFlush(agreement);
        saveComponents(tenantId, agreement.getId(), request.getComponents());
        return response(agreement, components.findAllByTenantIdAndCompensationAgreementIdOrderBySortOrderAscIdAsc(
                tenantId, agreement.getId()));
    }

    @Transactional
    public CompensationAgreementResponse approve(Long tenantId, Long memberId, Long agreementId,
                                                  Long actorId) {
        lockMember(tenantId, memberId);
        CompensationAgreement agreement = lockAgreement(tenantId, memberId, agreementId);
        if (agreement.getStatus() != CompensationAgreementStatus.DRAFT) {
            throw ApiException.conflict("لا يمكن اعتماد اتفاقية ليست مسودة.");
        }
        if (agreement.getSupersedesAgreementId() != null) {
            throw ApiException.conflict("يجب اعتماد مسودة الاستبدال من خلال إجراء الاستبدال المخصص.");
        }
        List<CompensationComponent> agreementComponents =
                components.findAllByTenantIdAndCompensationAgreementIdOrderBySortOrderAscIdAsc(
                        tenantId, agreementId);
        if (agreementComponents.isEmpty()) {
            throw ApiException.badRequest("يجب إضافة مكون أجر واحد على الأقل قبل الاعتماد.");
        }
        validateNoOverlap(tenantId, agreement, null);
        agreement.approve(actorId, LocalDateTime.now(ZoneOffset.UTC));
        agreements.saveAndFlush(agreement);
        return response(agreement, agreementComponents);
    }

    @Transactional
    public CompensationAgreementResponse approveReplacement(Long tenantId, Long memberId,
            Long newAgreementId, Long actorId) {
        lockMember(tenantId, memberId);
        CompensationAgreement replacement = lockAgreement(tenantId, memberId, newAgreementId);
        if (replacement.getStatus() != CompensationAgreementStatus.DRAFT
                || replacement.getSupersedesAgreementId() == null) {
            throw ApiException.conflict("المسودة ليست مهيأة لاستبدال اتفاقية معتمدة.");
        }
        List<CompensationComponent> newComponents =
                components.findAllByTenantIdAndCompensationAgreementIdOrderBySortOrderAscIdAsc(
                        tenantId, newAgreementId);
        if (newComponents.isEmpty()) {
            throw ApiException.badRequest("يجب إضافة مكون أجر واحد على الأقل قبل الاعتماد.");
        }

        CompensationAgreement oldAgreement = agreements.findByIdAndTenantIdForUpdate(
                        replacement.getSupersedesAgreementId(), tenantId)
                .filter(a -> memberId.equals(a.getWorkforceMemberId()))
                .orElseThrow(() -> ApiException.notFound("الاتفاقية المطلوب استبدالها غير موجودة."));
        if (oldAgreement.getStatus() != CompensationAgreementStatus.APPROVED) {
            throw ApiException.conflict("لا يمكن استبدال اتفاقية غير معتمدة أو سبق إنهاؤها.");
        }
        if (!sameScope(oldAgreement, replacement)) {
            throw ApiException.conflict("يجب أن يكون نطاق الاتفاقية الجديدة مطابقًا للاتفاقية المستبدلة.");
        }
        LocalDate newStart = replacement.getEffectiveFrom();
        if (!newStart.isAfter(oldAgreement.getEffectiveFrom())) {
            throw ApiException.conflict("يجب أن يبدأ الاستبدال بعد تاريخ بداية الاتفاقية القديمة.");
        }
        if (oldAgreement.getEffectiveTo() != null
                && oldAgreement.getEffectiveTo().isBefore(newStart.minusDays(1))) {
            throw ApiException.conflict("توجد فجوة بين نهاية الاتفاقية القديمة وبداية الاتفاقية الجديدة.");
        }
        LocalDate oldLastDay = newStart.minusDays(1);
        if (oldLastDay.isBefore(oldAgreement.getEffectiveFrom())) {
            throw ApiException.conflict("تاريخ الاستبدال ينتج فترة غير صحيحة للاتفاقية القديمة.");
        }
        validateNoOverlap(tenantId, replacement, oldAgreement.getId());

        // Both state transitions are committed or rolled back together.
        oldAgreement.supersedeOn(oldLastDay, actorId);
        replacement.approve(actorId, LocalDateTime.now(ZoneOffset.UTC));
        agreements.saveAndFlush(oldAgreement);
        agreements.saveAndFlush(replacement);
        return response(replacement, newComponents);
    }

    @Transactional
    public void cancelDraft(Long tenantId, Long memberId, Long agreementId, Long actorId) {
        lockMember(tenantId, memberId);
        CompensationAgreement agreement = lockAgreement(tenantId, memberId, agreementId);
        try {
            agreement.cancelDraft(actorId);
        } catch (IllegalStateException ex) {
            throw ApiException.conflict("لا يمكن إلغاء اتفاقية ليست مسودة.");
        }
        agreements.saveAndFlush(agreement);
    }

    private void validateReplacementDraft(Long tenantId, Long memberId,
                                          CompensationAgreementRequest request) {
        if (request.getSupersedesAgreementId() == null) {
            if (request.getChangeReason() != null && !request.getChangeReason().trim().isEmpty()) {
                throw ApiException.badRequest("سبب التغيير يستخدم فقط عند استبدال اتفاقية.");
            }
            return;
        }
        if (request.getChangeReason() == null || request.getChangeReason().trim().isEmpty()) {
            throw ApiException.badRequest("سبب تغيير الاتفاقية مطلوب.");
        }
        CompensationAgreement old = agreements.findByIdAndTenantIdForUpdate(
                        request.getSupersedesAgreementId(), tenantId)
                .filter(a -> memberId.equals(a.getWorkforceMemberId()))
                .orElseThrow(() -> ApiException.notFound("الاتفاقية المطلوب استبدالها غير موجودة."));
        if (old.getStatus() != CompensationAgreementStatus.APPROVED) {
            throw ApiException.conflict("يمكن استبدال الاتفاقيات المعتمدة فقط.");
        }
        if (!java.util.Objects.equals(old.getWorkforceAssignmentId(),
                request.getWorkforceAssignmentId())) {
            throw ApiException.conflict("يجب أن يكون نطاق الاتفاقية الجديدة مطابقًا للاتفاقية المستبدلة.");
        }
        if (!request.getEffectiveFrom().isAfter(old.getEffectiveFrom())) {
            throw ApiException.conflict("يجب أن يبدأ الاستبدال بعد تاريخ بداية الاتفاقية القديمة.");
        }
        if (old.getEffectiveTo() != null
                && old.getEffectiveTo().isBefore(request.getEffectiveFrom().minusDays(1))) {
            throw ApiException.conflict("توجد فجوة بين نهاية الاتفاقية القديمة وبداية الاتفاقية الجديدة.");
        }
    }

    private void validateNoOverlap(Long tenantId, CompensationAgreement candidate, Long excludedId) {
        for (CompensationAgreement existing :
                agreements.findAllByTenantIdAndWorkforceMemberIdOrderByEffectiveFromDescIdDesc(
                        tenantId, candidate.getWorkforceMemberId())) {
            if (existing.getStatus() != CompensationAgreementStatus.APPROVED
                    || (excludedId != null && excludedId.equals(existing.getId()))) {
                continue;
            }
            if (!overlaps(candidate.getEffectiveFrom(), candidate.getEffectiveTo(),
                    existing.getEffectiveFrom(), existing.getEffectiveTo())) {
                continue;
            }
            Long candidateAssignment = candidate.getWorkforceAssignmentId();
            Long existingAssignment = existing.getWorkforceAssignmentId();
            if (candidateAssignment == null || existingAssignment == null
                    || candidateAssignment.equals(existingAssignment)) {
                throw ApiException.conflict("توجد اتفاقية أجر معتمدة متداخلة خلال الفترة المحددة.");
            }
        }
    }

    private boolean overlaps(LocalDate fromA, LocalDate toA, LocalDate fromB, LocalDate toB) {
        return (toA == null || !toA.isBefore(fromB))
                && (toB == null || !toB.isBefore(fromA));
    }

    private boolean sameScope(CompensationAgreement a, CompensationAgreement b) {
        return java.util.Objects.equals(a.getWorkforceAssignmentId(), b.getWorkforceAssignmentId());
    }

    private void validateAssignmentScope(Long tenantId, Long memberId, Long assignmentId,
                                         LocalDate from, LocalDate to) {
        if (assignmentId == null) return;
        WorkforceAssignment assignment = assignments.findByIdAndTenantIdForUpdate(assignmentId, tenantId)
                .filter(a -> memberId.equals(a.getWorkforceMemberId()))
                .orElseThrow(() -> ApiException.notFound("التكليف الوظيفي غير موجود."));
        if (assignment.getStatus() != WorkforceAssignmentStatus.ACTIVE
                || from.isBefore(assignment.getEffectiveFrom())
                || (assignment.getEffectiveTo() != null
                    && (to == null || to.isAfter(assignment.getEffectiveTo())))) {
            throw ApiException.conflict("فترة الاتفاقية يجب أن تقع بالكامل داخل فترة التكليف النشط.");
        }
    }

    private void validateRequestDates(CompensationAgreementRequest request) {
        if (request.getEffectiveTo() != null && request.getEffectiveTo().isBefore(request.getEffectiveFrom())) {
            throw ApiException.badRequest("تاريخ نهاية الاتفاقية يجب ألا يسبق تاريخ بدايتها.");
        }
        if (request.getComponents() == null || request.getComponents().isEmpty()) {
            throw ApiException.badRequest("يجب إضافة مكون أجر واحد على الأقل.");
        }
    }

    private void saveComponents(Long tenantId, Long agreementId,
                                List<CompensationComponentRequest> requests) {
        for (CompensationComponentRequest request : requests) {
            try {
                Long definitionId = request.getFinancialItemDefinitionId();
                String code = request.getComponentCode();
                String name = request.getDisplayName();
                CompensationBasis basis = request.getBasis();

                if (definitionId != null) {
                    WorkforceFinancialItemDefinition definition = financialItems
                            .findByIdAndTenantIdForUpdate(definitionId, tenantId)
                            .orElseThrow(() -> ApiException.notFound("تعريف البند المالي غير موجود."));
                    if (!definition.isActive()) {
                        throw ApiException.conflict("لا يمكن استخدام بند مالي معطل في اتفاقية جديدة.");
                    }
                    if (definition.getFamily() != WorkforceFinancialItemFamily.EARNING) {
                        throw ApiException.badRequest("اتفاقيات الأجور تقبل بنود الاستحقاق فقط.");
                    }
                    basis = toCompensationBasis(definition.getCalculationMethod());
                    code = definition.getItemCode();
                    name = definition.getDisplayName();
                } else if (code == null || code.trim().isEmpty()
                        || name == null || name.trim().isEmpty() || basis == null) {
                    throw ApiException.badRequest("كود واسم وطريقة حساب مكون الأجر مطلوبة عند عدم اختيار بند من الكتالوج.");
                }

                components.save(new CompensationComponent(tenantId, agreementId, definitionId,
                        code, name, basis, request.getRate(), request.getUnitCode(),
                        request.getShiftType(), request.getSortOrder() == null ? 0 : request.getSortOrder()));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest(ex.getMessage());
            }
        }
        components.flush();
    }

    private CompensationBasis toCompensationBasis(WorkforceFinancialItemCalculationMethod method) {
        switch (method) {
            case FIXED: return CompensationBasis.FIXED;
            case PER_HOUR: return CompensationBasis.HOUR;
            case PER_DAY: return CompensationBasis.DAY;
            case PER_SHIFT: return CompensationBasis.SHIFT;
            case PER_QUANTITY: return CompensationBasis.QUANTITY;
            case MANUAL:
            default:
                throw ApiException.badRequest("البند اليدوي لا يمكن ربطه بمكون اتفاقية أجر محسوب.");
        }
    }

    private CompensationAgreement lockAgreement(Long tenantId, Long memberId, Long agreementId) {
        return agreements.findByIdAndTenantIdForUpdate(agreementId, tenantId)
                .filter(a -> memberId.equals(a.getWorkforceMemberId()))
                .orElseThrow(() -> ApiException.notFound("اتفاقية الأجر غير موجودة."));
    }

    private WorkforceMember lockMember(Long tenantId, Long memberId) {
        return members.findByIdAndTenantIdForUpdate(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
    }

    private WorkforceMember requireMember(Long tenantId, Long memberId) {
        return members.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
    }

    private CompensationAgreementResponse response(CompensationAgreement agreement,
                                                    List<CompensationComponent> agreementComponents) {
        return CompensationAgreementResponse.from(agreement, agreementComponents);
    }
}
