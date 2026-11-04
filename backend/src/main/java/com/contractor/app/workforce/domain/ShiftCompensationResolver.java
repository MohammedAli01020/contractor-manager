package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.money.Money;
import com.contractor.app.driver.domain.Driver;
import com.contractor.app.shift.domain.Shift;
import com.contractor.app.shift.domain.ShiftType;
import com.contractor.app.workforce.infra.CompensationAgreementRepository;
import com.contractor.app.workforce.infra.CompensationComponentRepository;
import com.contractor.app.workforce.infra.WorkforceAssignmentRepository;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Resolves a shift's contractual wage by its business date and freezes the result on the shift.
 * The legacy driver-rate path is retained only for members who have never had an approved agreement.
 */
@Service
public class ShiftCompensationResolver {

    private final CompensationAgreementRepository agreements;
    private final CompensationComponentRepository components;
    private final WorkforceAssignmentRepository assignments;
    private final WorkforceMemberRepository members;

    public ShiftCompensationResolver(CompensationAgreementRepository agreements,
            CompensationComponentRepository components, WorkforceAssignmentRepository assignments,
            WorkforceMemberRepository members) {
        this.agreements = agreements;
        this.components = components;
        this.assignments = assignments;
        this.members = members;
    }

    public void resolveAndApply(Long tenantId, Driver driver, LocalDate shiftDate,
            Long siteId, ShiftType shiftType, BigDecimal requestedWage, Shift shift) {
        if (driver == null || driver.getWorkforceMemberId() == null || shiftDate == null
                || siteId == null || shiftType == null || shift == null) {
            throw ApiException.badRequest("تعذر تحديد بيانات العامل والتكليف لحساب الأجر.");
        }

        members.findByIdAndTenantIdForUpdate(driver.getWorkforceMemberId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل المرتبط بالسائق غير موجود."));

        List<CompensationAgreement> memberAgreements =
                agreements.findAllForWageResolution(tenantId, driver.getWorkforceMemberId());

        List<CompensationAgreement> approved = memberAgreements.stream()
                .filter(a -> a.getStatus() == CompensationAgreementStatus.APPROVED
                        || a.getStatus() == CompensationAgreementStatus.SUPERSEDED)
                .collect(Collectors.toList());

        List<CompensationAgreement> effective = approved.stream()
                .filter(a -> !a.getEffectiveFrom().isAfter(shiftDate)
                        && (a.getEffectiveTo() == null || !a.getEffectiveTo().isBefore(shiftDate)))
                .collect(Collectors.toList());

        if (effective.isEmpty()) {
            if (!approved.isEmpty()) {
                throw ApiException.conflict("لا توجد اتفاقية أجر معتمدة وسارية للعامل في تاريخ الوردية. راجع تواريخ الاتفاقيات.");
            }
            applyLegacyRate(driver, shiftDate, shiftType, requestedWage, shift);
            return;
        }

        List<WorkforceAssignment> effectiveAssignments =
                assignments.findAllForWageResolution(tenantId, driver.getWorkforceMemberId()).stream()
                .filter(a -> a.getStatus() != WorkforceAssignmentStatus.CANCELLED)
                .filter(a -> !a.getEffectiveFrom().isAfter(shiftDate)
                        && (a.getEffectiveTo() == null || !a.getEffectiveTo().isBefore(shiftDate)))
                .filter(a -> a.getSiteId() == null || a.getSiteId().equals(siteId))
                .collect(Collectors.toList());

        Set<Long> assignmentIds = new HashSet<Long>();
        for (WorkforceAssignment assignment : effectiveAssignments) {
            assignmentIds.add(assignment.getId());
        }

        List<CompensationAgreement> assignmentSpecific = effective.stream()
                .filter(a -> a.getWorkforceAssignmentId() != null
                        && assignmentIds.contains(a.getWorkforceAssignmentId()))
                .collect(Collectors.toList());

        List<CompensationAgreement> general = effective.stream()
                .filter(a -> a.getWorkforceAssignmentId() == null)
                .collect(Collectors.toList());
        if (assignmentSpecific.size() > 1) {
            throw ApiException.conflict("يوجد أكثر من اتفاقية أجر خاصة بالتكليف تنطبق على الوردية. راجع التكليفات والاتفاقيات.");
        }
        if (!assignmentSpecific.isEmpty() && effectiveAssignments.size() > 1) {
            throw ApiException.conflict("للعامل أكثر من تكليف سارٍ في الموقع نفسه، ولا تحدد الوردية التكليف المقصود. لا يمكن اختيار اتفاقية خاصة بأمان.");
        }
        if (general.size() > 1) {
            throw ApiException.conflict("يوجد أكثر من اتفاقية أجر عامة سارية للعامل في تاريخ الوردية.");
        }
        if (!assignmentSpecific.isEmpty() && !general.isEmpty()) {
            throw ApiException.conflict("تتداخل اتفاقية عامة مع اتفاقية خاصة بالتكليف. صحّح فترات الاتفاقيات قبل بدء الوردية.");
        }
        CompensationAgreement selected;
        if (!assignmentSpecific.isEmpty()) {
            selected = assignmentSpecific.get(0);
        } else if (!general.isEmpty()) {
            selected = general.get(0);
        } else {
            throw ApiException.conflict("لا توجد اتفاقية أجر سارية تنطبق على تكليف العامل في تاريخ الوردية.");
        }

        if (!"EGP".equalsIgnoreCase(selected.getCurrencyCode())) {
            throw ApiException.conflict("الدفتر المحاسبي الحالي يعمل بالجنيه المصري فقط؛ لا يمكن ترحيل اتفاقية بعملة مختلفة قبل دعم تعدد العملات.");
        }

        List<CompensationComponent> matching = components
                .findAllForWageResolution(tenantId, selected.getId()).stream()
                .filter(c -> c.getBasis() == CompensationBasis.SHIFT)
                .filter(c -> c.getShiftType() == CompensationShiftType.valueOf(shiftType.name()))
                .collect(Collectors.toList());

        if (matching.size() != 1) {
            throw ApiException.conflict("الاتفاقية المعتمدة لا تحتوي على مكوّن أجر وردية واحد مطابق لنوع الوردية.");
        }

        CompensationComponent component = matching.get(0);
        if (requestedWage != null && component.getRate() != null
                && requestedWage.compareTo(component.getRate()) != 0) {
            throw ApiException.conflict("أجر الاتفاقية معتمد ولا يمكن تغييره من نموذج الوردية. استخدم إجراء تصحيح معتمدًا إذا لزم الأمر.");
        }
        if (component.getRate() == null || component.getRate().signum() < 0) {
            throw ApiException.conflict("قيمة أجر الوردية في الاتفاقية غير صالحة.");
        }
        shift.applyCompensationSnapshot("AGREEMENT", selected.getId(), component.getId(),
                selected.getAgreementReference(), component.getComponentCode(), component.getDisplayName(),
                component.getBasis().name(), selected.getCurrencyCode(), component.getUnitCode(),
                Money.scale(component.getRate()), Money.scale(component.getRate()));
    }

    private static void applyLegacyRate(Driver driver, LocalDate shiftDate, ShiftType shiftType, BigDecimal requestedWage, Shift shift) {
        java.math.BigDecimal rate;
        switch (shiftType) {
            case MORNING: rate = driver.getMorningRate(); break;
            case EVENING: rate = driver.getEveningRate(); break;
            default: rate = driver.getNightRate(); break;
        }
        if (requestedWage != null) rate = requestedWage;
        if (rate == null || rate.signum() < 0) {
            throw ApiException.conflict("لا توجد اتفاقية أجر للعامل، كما أن الأجر القديم غير صالح.");
        }
        shift.applyCompensationSnapshot("LEGACY_DRIVER_RATE", null, null, null,
                "LEGACY_DRIVER_RATE", "Legacy driver rate", "SHIFT", null, null,
                Money.scale(rate), Money.scale(rate));
    }
}
