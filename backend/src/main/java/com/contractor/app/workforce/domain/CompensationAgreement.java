package com.contractor.app.workforce.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CompensationSource source = CompensationSource.MANUAL;

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
                                  String currencyCode, CompensationSource source, Long actorUserId) {
        super(tenantId);
        if (memberId == null || effectiveFrom == null
                || currencyCode == null || currencyCode.trim().length() != 3
                || source == null || actorUserId == null) {
            throw new IllegalArgumentException("Invalid compensation agreement attributes");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("Agreement end date precedes start date");
        }
        this.workforceMemberId = memberId;
        this.workforceAssignmentId = assignmentId;
        this.agreementReference = normalizeReference(reference);
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.currencyCode = currencyCode.trim().toUpperCase(java.util.Locale.ROOT);
        this.source = source;
        this.createdByUserId = actorUserId;
        this.updatedByUserId = actorUserId;
    }

    private static String normalizeReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) return null;
        return reference.trim();
    }

    public Long getWorkforceMemberId() { return workforceMemberId; }
    public Long getWorkforceAssignmentId() { return workforceAssignmentId; }
    public String getAgreementReference() { return agreementReference; }
    public CompensationAgreementStatus getStatus() { return status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getCurrencyCode() { return currencyCode; }
    public CompensationSource getSource() { return source; }
    public Long getApprovedByUserId() { return approvedByUserId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getUpdatedByUserId() { return updatedByUserId; }

    public boolean isApprovedOrClosed() {
        return status != CompensationAgreementStatus.DRAFT;
    }
}
