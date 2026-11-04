package com.contractor.app.workforce.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import java.util.Locale;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

@Entity
@Table(name = "compensation_components")
public class CompensationComponent extends BaseTenantEntity {

    @Column(name = "compensation_agreement_id", nullable = false, updatable = false)
    private Long compensationAgreementId;

    @Column(name = "component_code", nullable = false, length = 60)
    private String componentCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationBasis basis;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal rate;

    @Column(name = "unit_code", length = 30)
    private String unitCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", length = 20)
    private CompensationShiftType shiftType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CompensationComponent() {
    }

    public CompensationComponent(Long tenantId, Long agreementId, String code, String displayName,
                                 CompensationBasis basis, BigDecimal rate, String unitCode,
                                 CompensationShiftType shiftType, int sortOrder) {
        super(tenantId);
        if (agreementId == null || code == null || code.trim().isEmpty()
                || displayName == null || displayName.trim().isEmpty()
                || basis == null || rate == null || rate.signum() < 0) {
            throw new IllegalArgumentException("Invalid compensation component attributes");
        }
        String normalizedUnit = unitCode == null || unitCode.trim().isEmpty()
                ? null : unitCode.trim().toUpperCase(Locale.ROOT);
        if (basis == CompensationBasis.SHIFT) {
            if (shiftType == null) throw new IllegalArgumentException("SHIFT requires shift type");
        } else if (shiftType != null) {
            throw new IllegalArgumentException("Only SHIFT components may have a shift type");
        }
        if ((basis == CompensationBasis.HOUR || basis == CompensationBasis.DAY
                || basis == CompensationBasis.QUANTITY) && normalizedUnit == null) {
            throw new IllegalArgumentException("This compensation basis requires a unit");
        }
        if (basis == CompensationBasis.FIXED
                && !("MONTH".equals(normalizedUnit) || "WEEK".equals(normalizedUnit)
                || "DAY".equals(normalizedUnit))) {
            throw new IllegalArgumentException("FIXED requires MONTH, WEEK or DAY period unit");
        }
        this.compensationAgreementId = agreementId;
        this.componentCode = code.trim().toUpperCase(Locale.ROOT);
        this.displayName = displayName.trim();
        this.basis = basis;
        this.rate = rate;
        this.unitCode = normalizedUnit;
        this.shiftType = shiftType;
        this.sortOrder = sortOrder;
    }

    public Long getCompensationAgreementId() { return compensationAgreementId; }
    public String getComponentCode() { return componentCode; }
    public String getDisplayName() { return displayName; }
    public CompensationBasis getBasis() { return basis; }
    public BigDecimal getRate() { return rate; }
    public String getUnitCode() { return unitCode; }
    public CompensationShiftType getShiftType() { return shiftType; }
    public int getSortOrder() { return sortOrder; }
}
