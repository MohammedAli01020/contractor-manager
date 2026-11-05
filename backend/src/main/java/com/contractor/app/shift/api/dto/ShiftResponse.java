package com.contractor.app.shift.api.dto;

import com.contractor.app.shift.domain.Shift;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ShiftResponse {

    private final Long id;
    private final LocalDate shiftDate;
    private final String shiftType;
    private final String status;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Long siteId;
    private final String siteName;
    private final Long driverId;
    private final String driverName;
    private final Long equipmentId;
    private final String equipmentCode;
    private final String equipmentName;
    private final BigDecimal price;
    private final BigDecimal driverWage;
    private final BigDecimal equipmentOperatingCost;
    private final Long driverWageJournalEntryId;
    private final String wageSource;
    private final Long compensationAgreementId;
    private final String compensationAgreementReference;
    private final Long compensationComponentId;
    private final String compensationComponentCode;
    private final String compensationComponentName;
    private final String compensationBasis;
    private final String compensationCurrencyCode;
    private final BigDecimal compensationRate;
    private final BigDecimal compensationCalculatedAmount;
    private final BigDecimal actualHours;
    private final String compensationUnitCode;
    private final BigDecimal actualQuantity;

    private final BigDecimal priceAfterWage;
    private final String notes;
    private final String cancelReason;
    private final Long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ShiftResponse(Shift s, String siteName, String driverName, String equipmentCode, String equipmentName) {
        this.id = s.getId();
        this.shiftDate = s.getShiftDate();
        this.shiftType = s.getShiftType().name();
        this.status = s.getStatus().name();
        this.startedAt = s.getStartedAt();
        this.finishedAt = s.getFinishedAt();
        this.siteId = s.getSiteId();
        this.siteName = siteName;
        this.driverId = s.getDriverId();
        this.driverName = driverName;
        this.equipmentId = s.getEquipmentId();
        this.equipmentCode = equipmentCode;
        this.equipmentName = equipmentName;
        this.price = s.getPrice();
        this.driverWage = s.getDriverWage();
        this.equipmentOperatingCost = s.getEquipmentOperatingCost();
        this.driverWageJournalEntryId = s.getDriverWageJournalEntryId();
        this.wageSource = s.getWageSource();
        this.compensationAgreementId = s.getCompensationAgreementId();
        this.compensationAgreementReference = s.getCompensationAgreementReference();
        this.compensationComponentId = s.getCompensationComponentId();
        this.compensationComponentCode = s.getCompensationComponentCode();
        this.compensationComponentName = s.getCompensationComponentName();
        this.compensationBasis = s.getCompensationBasis();
        this.compensationCurrencyCode = s.getCompensationCurrencyCode();
        this.compensationRate = s.getCompensationRate();
        this.compensationCalculatedAmount = s.getCompensationCalculatedAmount();
        this.actualHours = s.getActualHours();
        this.compensationUnitCode = s.getCompensationUnitCode();
        this.actualQuantity = s.getActualQuantity();
        // الإيراد ناقص أجر السائق (قبل المصروفات الفعلية). تكلفة تشغيل المعدة مؤشر فقط ولا تدخل هنا.
        this.priceAfterWage = "HOUR".equals(s.getCompensationBasis())
                && s.getStatus() == com.contractor.app.shift.domain.ShiftStatus.IN_PROGRESS
                ? null : s.getPrice().subtract(s.getDriverWage());
        this.notes = s.getNotes();
        this.cancelReason = s.getCancelReason();
        this.version = s.getVersion();
        this.createdAt = s.getCreatedAt();
        this.updatedAt = s.getUpdatedAt();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public String getShiftType() {
        return shiftType;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Long getSiteId() {
        return siteId;
    }

    public String getSiteName() {
        return siteName;
    }

    public Long getDriverId() {
        return driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getDriverWage() {
        return driverWage;
    }

    public Long getDriverWageJournalEntryId() { return driverWageJournalEntryId; }
    public String getWageSource() { return wageSource; }
    public Long getCompensationAgreementId() { return compensationAgreementId; }
    public String getCompensationAgreementReference() { return compensationAgreementReference; }
    public Long getCompensationComponentId() { return compensationComponentId; }
    public String getCompensationComponentCode() { return compensationComponentCode; }
    public String getCompensationComponentName() { return compensationComponentName; }
    public String getCompensationBasis() { return compensationBasis; }
    public String getCompensationCurrencyCode() { return compensationCurrencyCode; }
    public BigDecimal getCompensationRate() { return compensationRate; }
    public BigDecimal getCompensationCalculatedAmount() { return compensationCalculatedAmount; }
    public BigDecimal getActualHours() { return actualHours; }
    public String getCompensationUnitCode() { return compensationUnitCode; }
    public BigDecimal getActualQuantity() { return actualQuantity; }

    public BigDecimal getEquipmentOperatingCost() {
        return equipmentOperatingCost;
    }

    public BigDecimal getPriceAfterWage() {
        return priceAfterWage;
    }

    public String getNotes() {
        return notes;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
