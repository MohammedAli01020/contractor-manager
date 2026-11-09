package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.CompensationAgreement;
import com.contractor.app.workforce.domain.CompensationAgreementStatus;
import com.contractor.app.workforce.domain.CompensationComponent;
import com.contractor.app.workforce.domain.CompensationBasis;
import com.contractor.app.workforce.domain.CompensationShiftType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class CompensationAgreementResponse {
    private final Long id;
    private final Long workforceMemberId;
    private final Long workforceAssignmentId;
    private final String agreementReference;
    private final CompensationAgreementStatus status;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final String currencyCode;
    private final Long supersedesAgreementId;
    private final String changeReason;
    private final Long approvedByUserId;
    private final LocalDateTime approvedAt;
    private final Long version;
    private final List<Component> components;

    private CompensationAgreementResponse(CompensationAgreement agreement,
                                           List<CompensationComponent> components) {
        id = agreement.getId();
        workforceMemberId = agreement.getWorkforceMemberId();
        workforceAssignmentId = agreement.getWorkforceAssignmentId();
        agreementReference = agreement.getAgreementReference();
        status = agreement.getStatus();
        effectiveFrom = agreement.getEffectiveFrom();
        effectiveTo = agreement.getEffectiveTo();
        currencyCode = agreement.getCurrencyCode();
        supersedesAgreementId = agreement.getSupersedesAgreementId();
        changeReason = agreement.getChangeReason();
        approvedByUserId = agreement.getApprovedByUserId();
        approvedAt = agreement.getApprovedAt();
        version = agreement.getVersion();
        this.components = components.stream().map(Component::new).collect(Collectors.toList());
    }

    public static CompensationAgreementResponse from(CompensationAgreement agreement,
                                                       List<CompensationComponent> components) {
        return new CompensationAgreementResponse(agreement, components);
    }

    public Long getId() { return id; }
    public Long getWorkforceMemberId() { return workforceMemberId; }
    public Long getWorkforceAssignmentId() { return workforceAssignmentId; }
    public String getAgreementReference() { return agreementReference; }
    public CompensationAgreementStatus getStatus() { return status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getCurrencyCode() { return currencyCode; }
    public Long getSupersedesAgreementId() { return supersedesAgreementId; }
    public String getChangeReason() { return changeReason; }
    public Long getApprovedByUserId() { return approvedByUserId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public Long getVersion() { return version; }
    public List<Component> getComponents() { return components; }

    public static class Component {
        private final Long id;
        private final Long financialItemDefinitionId;
        private final String componentCode;
        private final String displayName;
        private final CompensationBasis basis;
        private final BigDecimal rate;
        private final String unitCode;
        private final CompensationShiftType shiftType;
        private final int sortOrder;

        Component(CompensationComponent component) {
            id = component.getId();
            financialItemDefinitionId = component.getFinancialItemDefinitionId();
            componentCode = component.getComponentCode();
            displayName = component.getDisplayName();
            basis = component.getBasis();
            rate = component.getRate();
            unitCode = component.getUnitCode();
            shiftType = component.getShiftType();
            sortOrder = component.getSortOrder();
        }
        public Long getId() { return id; }
        public Long getFinancialItemDefinitionId() { return financialItemDefinitionId; }
        public String getComponentCode() { return componentCode; }
        public String getDisplayName() { return displayName; }
        public CompensationBasis getBasis() { return basis; }
        public BigDecimal getRate() { return rate; }
        public String getUnitCode() { return unitCode; }
        public CompensationShiftType getShiftType() { return shiftType; }
        public int getSortOrder() { return sortOrder; }
    }
}
