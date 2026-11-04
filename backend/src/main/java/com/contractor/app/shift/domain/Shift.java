package com.contractor.app.shift.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

@Entity
@Table(name = "shifts")
public class Shift extends BaseTenantEntity {

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 20)
    private ShiftType shiftType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status = ShiftStatus.IN_PROGRESS;

    /** وقت آخر بدء فعلي للوردية (UTC)، ويُحدّث عند إعادة تفعيل وردية كانت جارية ثم أُلغيت. */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** وقت الضغط على "إنهاء" (UTC). يبقى بعد الإلغاء، وهو ما يسمح بإعادة تفعيل الوردية المنتهية. */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "driver_wage", nullable = false, precision = 15, scale = 2)
    private BigDecimal driverWage;

    @Column(name = "equipment_operating_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal equipmentOperatingCost;

    /** One-time journal that recognizes this shift driver wage. */
    @Column(name = "driver_wage_journal_entry_id", unique = true)
    private Long driverWageJournalEntryId;

    @Column(name = "wage_source", nullable = false, length = 30)
    private String wageSource = "LEGACY_DRIVER_RATE";

    @Column(name = "compensation_agreement_id")
    private Long compensationAgreementId;

    @Column(name = "compensation_component_id")
    private Long compensationComponentId;

    @Column(name = "compensation_agreement_reference", length = 80)
    private String compensationAgreementReference;

    @Column(name = "compensation_component_code", nullable = false, length = 60)
    private String compensationComponentCode = "LEGACY_DRIVER_RATE";

    @Column(name = "compensation_component_name", nullable = false, length = 120)
    private String compensationComponentName = "Legacy driver rate";

    @Column(name = "compensation_basis", nullable = false, length = 20)
    private String compensationBasis = "SHIFT";

    @Column(name = "compensation_currency_code", length = 3)
    private String compensationCurrencyCode;

    @Column(name = "compensation_unit_code", length = 30)
    private String compensationUnitCode;

    @Column(name = "compensation_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal compensationRate;

    @Column(name = "compensation_calculated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal compensationCalculatedAmount;

    @Column(length = 500)
    private String notes;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    protected Shift() {
    }

    public Shift(Long tenantId, LocalDate shiftDate, ShiftType shiftType, Long siteId, Long driverId,
                 Long equipmentId) {
        super(tenantId);
        this.shiftDate = shiftDate;
        this.shiftType = shiftType;
        this.startedAt = LocalDateTime.now(ZoneOffset.UTC);
        this.siteId = siteId;
        this.driverId = driverId;
        this.equipmentId = equipmentId;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public ShiftType getShiftType() {
        return shiftType;
    }

    public void setShiftType(ShiftType shiftType) {
        this.shiftType = shiftType;
    }

    public ShiftStatus getStatus() {
        return status;
    }

    public void setStatus(ShiftStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getDriverWage() {
        return driverWage;
    }

    public void setDriverWage(BigDecimal driverWage) {
        this.driverWage = driverWage;
    }

    public Long getDriverWageJournalEntryId() { return driverWageJournalEntryId; }

    public void setDriverWageJournalEntryId(Long driverWageJournalEntryId) {
        this.driverWageJournalEntryId = driverWageJournalEntryId;
    }

    public BigDecimal getEquipmentOperatingCost() {
        return equipmentOperatingCost;
    }

    public void setEquipmentOperatingCost(BigDecimal equipmentOperatingCost) {
        this.equipmentOperatingCost = equipmentOperatingCost;
    }

    public String getWageSource() { return wageSource; }
    public Long getCompensationAgreementId() { return compensationAgreementId; }
    public Long getCompensationComponentId() { return compensationComponentId; }
    public String getCompensationAgreementReference() { return compensationAgreementReference; }
    public String getCompensationComponentCode() { return compensationComponentCode; }
    public String getCompensationComponentName() { return compensationComponentName; }
    public String getCompensationBasis() { return compensationBasis; }
    public String getCompensationCurrencyCode() { return compensationCurrencyCode; }
    public String getCompensationUnitCode() { return compensationUnitCode; }
    public BigDecimal getCompensationRate() { return compensationRate; }
    public BigDecimal getCompensationCalculatedAmount() { return compensationCalculatedAmount; }

    public void applyCompensationSnapshot(String source, Long agreementId, Long componentId,
            String agreementReference, String componentCode, String componentName,
            String basis, String currencyCode, String unitCode, BigDecimal rate, BigDecimal amount) {
        if (source == null || componentCode == null || componentName == null || basis == null
                || rate == null || amount == null || rate.signum() < 0 || amount.signum() < 0) {
            throw new IllegalArgumentException("Invalid shift compensation snapshot");
        }
        if ("AGREEMENT".equals(source) && (agreementId == null || componentId == null || currencyCode == null)) {
            throw new IllegalArgumentException("Agreement snapshot requires agreement, component and currency");
        }
        if (!"AGREEMENT".equals(source) && !"LEGACY_DRIVER_RATE".equals(source)) {
            throw new IllegalArgumentException("Unsupported shift wage source");
        }
        this.wageSource = source;
        this.compensationAgreementId = agreementId;
        this.compensationComponentId = componentId;
        this.compensationAgreementReference = agreementReference;
        this.compensationComponentCode = componentCode;
        this.compensationComponentName = componentName;
        this.compensationBasis = basis;
        this.compensationCurrencyCode = currencyCode;
        this.compensationUnitCode = unitCode;
        this.compensationRate = rate;
        this.compensationCalculatedAmount = amount;
        this.driverWage = amount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
