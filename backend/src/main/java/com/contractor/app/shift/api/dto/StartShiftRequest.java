package com.contractor.app.shift.api.dto;

import com.contractor.app.shift.domain.ShiftType;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class StartShiftRequest {

    @NotNull(message = "نوع الوردية مطلوب (MORNING أو EVENING أو NIGHT)")
    private ShiftType shiftType;

    @NotNull(message = "الموقع مطلوب")
    private Long siteId;

    @NotNull(message = "السائق أو العامل مطلوب")
    private Long driverId;

    @NotNull(message = "المعدة مطلوبة")
    private Long equipmentId;

    @DecimalMin(value = "0", message = "السعر لا يمكن أن يكون سالبًا")
    @Digits(integer = 13, fraction = 2, message = "السعر: حتى خانتين عشريتين")
    private BigDecimal price;

    @DecimalMin(value = "0", message = "الأجر لا يمكن أن يكون سالبًا")
    @Digits(integer = 13, fraction = 2, message = "الأجر: حتى خانتين عشريتين")
    private BigDecimal driverWage;

    @Size(max = 500, message = "الملاحظات يجب ألا تتجاوز 500 حرف")
    private String notes;

    public ShiftType getShiftType() {
        return shiftType;
    }

    public void setShiftType(ShiftType shiftType) {
        this.shiftType = shiftType;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
