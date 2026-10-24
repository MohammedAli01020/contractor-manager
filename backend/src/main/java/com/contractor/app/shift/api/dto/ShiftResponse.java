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
        // الإيراد ناقص أجر السائق (قبل المصروفات الفعلية). تكلفة تشغيل المعدة مؤشر فقط ولا تدخل هنا.
        this.priceAfterWage = s.getPrice().subtract(s.getDriverWage());
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
