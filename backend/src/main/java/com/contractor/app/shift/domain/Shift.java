package com.contractor.app.shift.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "driver_wage", nullable = false, precision = 15, scale = 2)
    private BigDecimal driverWage;

    @Column(name = "equipment_operating_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal equipmentOperatingCost;

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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
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

    public BigDecimal getEquipmentOperatingCost() {
        return equipmentOperatingCost;
    }

    public void setEquipmentOperatingCost(BigDecimal equipmentOperatingCost) {
        this.equipmentOperatingCost = equipmentOperatingCost;
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
