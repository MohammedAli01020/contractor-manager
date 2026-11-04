package com.contractor.app.workforce.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

@Entity
@Table(name = "compensation_agreements")
public class CompensationAgreement extends BaseTenantEntity {

    @Column(name = "workforce_member_id", nullable = false, updatable = false)
    private Long workforceMemberId;

    @Column(name = "workforce_assignment_id", updatable = false)
    private Long workforceAssignmentId;

    @Column(name = "agreement_reference", length = 80)
    private String agreementReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationAgreementStatus status = CompensationAgreementStatus.DRAFT;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private CompensationSource source = CompensationSource.MANUAL;

    @Column(name = "supersedes_agreement_id", updatable = false)
    private Long supersedesAgreementId;

    @Column(name = "change_reason", length = 500, updatable = false)
    private String changeReason;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;

    protected CompensationAgreement() {
    }

    public CompensationAgreement(Long tenantId, Long memberId, Long assignmentId,
                                 String reference, LocalDate effectiveFrom, LocalDate effectiveTo,
                                 String currencyCode, CompensationSource source, Long actorUserId,
                                 Long supersedesAgreementId, String changeReason) {
        super(tenantId);
        if (memberId == null || effectiveFrom == null || currencyCode == null
                || currencyCode.trim().length() != 3 || source == null || actorUserId == null) {
            throw new IllegalArgumentException("Invalid compensation agreement attributes");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("Agreement end date precedes start date");
        }
        String normalizedReason = changeReason == null || changeReason.trim().isEmpty()
                ? null : changeReason.trim();
        if (supersedesAgreementId != null && normalizedReason == null) {
            throw new IllegalArgumentException("Replacement reason is required");
        }
        this.workforceMemberId = memberId;
        this.workforceAssignmentId = assignmentId;
        this.agreementReference = reference == null || reference.trim().isEmpty()
                ? null : reference.trim();
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.currencyCode = currencyCode.trim().toUpperCase(Locale.ROOT);
        this.source = source;
        this.supersedesAgreementId = supersedesAgreementId;
        this.changeReason = normalizedReason;
        this.createdByUserId = actorUserId;
        this.updatedByUserId = actorUserId;
    }

    public void approve(Long actorUserId, LocalDateTime approvedAt) {
        if (status != CompensationAgreementStatus.DRAFT) {
            throw new IllegalStateException("Only draft agreements can be approved");
        }
        if (actorUserId == null || approvedAt == null) {
            throw new IllegalArgumentException("Approval audit is required");
        }
        status = CompensationAgreementStatus.APPROVED;
        approvedByUserId = actorUserId;
        this.approvedAt = approvedAt;
        updatedByUserId = actorUserId;
    }

    public void supersedeOn(LocalDate lastValidDay, Long actorUserId) {
        if (status != CompensationAgreementStatus.APPROVED || lastValidDay == null
                || lastValidDay.isBefore(effectiveFrom) || actorUserId == null) {
            throw new IllegalStateException("Agreement cannot be superseded on the supplied date");
        }
        effectiveTo = lastValidDay;
        status = CompensationAgreementStatus.SUPERSEDED;
        updatedByUserId = actorUserId;
    }

    public void cancelDraft(Long actorUserId) {
        if (status != CompensationAgreementStatus.DRAFT || actorUserId == null) {
            throw new IllegalStateException("Only draft agreements can be cancelled");
        }
        status = CompensationAgreementStatus.CANCELLED;
        updatedByUserId = actorUserId;
    }

    public Long getWorkforceMemberId() { return workforceMemberId; }
    public Long getWorkforceAssignmentId() { return workforceAssignmentId; }
    public String getAgreementReference() { return agreementReference; }
    public CompensationAgreementStatus getStatus() { return status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getCurrencyCode() { return currencyCode; }
    public CompensationSource getSource() { return source; }
    public Long getSupersedesAgreementId() { return supersedesAgreementId; }
    public String getChangeReason() { return changeReason; }
    public Long getApprovedByUserId() { return approvedByUserId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
}
