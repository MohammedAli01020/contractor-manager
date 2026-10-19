package com.contractor.app.shift.api.dto;

import com.contractor.app.shift.domain.ShiftType;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ShiftRequest {

    @NotNull(message = "تاريخ الوردية مطلوب")
    private LocalDate shiftDate;

    @NotNull(message = "نوع الوردية مطلوب (MORNING أو EVENING أو NIGHT)")
    private ShiftType shiftType;

    @NotNull(message = "الموقع مطلوب")
    private Long siteId;

    @NotNull(message = "السائق أو العامل مطلوب")
    private Long driverId;

    @NotNull(message = "المعدة مطلوبة")
    private Long equipmentId;

    @DecimalMin(value = "0", message = "عدد الساعات لا يمكن أن يكون سالبًا")
    @DecimalMax(value = "24", message = "عدد الساعات لا يتجاوز 24")
    @Digits(integer = 2, fraction = 2, message = "عدد الساعات: حتى خانتين عشريتين")
    private BigDecimal hours;

    @DecimalMin(value = "0", message = "الكمية لا يمكن أن تكون سالبة")
    @Digits(integer = 10, fraction = 2, message = "الكمية: حتى خانتين عشريتين")
    private BigDecimal quantity;

    @Size(max = 30, message = "وحدة الكمية يجب ألا تتجاوز 30 حرفًا")
    private String quantityUnit;

    /** اختياري: إن لم يُرسل يؤخذ السعر الافتراضي للمعدة (عند الإنشاء)، أو يبقى كما هو (عند التعديل). */
    @DecimalMin(value = "0", message = "السعر لا يمكن أن يكون سالبًا")
    @Digits(integer = 13, fraction = 2, message = "السعر: حتى خانتين عشريتين")
    private BigDecimal price;

    /** اختياري: إن لم يُرسل يؤخذ من أجر السائق حسب نوع الوردية. */
    @DecimalMin(value = "0", message = "الأجر لا يمكن أن يكون سالبًا")
    @Digits(integer = 13, fraction = 2, message = "الأجر: حتى خانتين عشريتين")
    private BigDecimal driverWage;

    @Size(max = 500, message = "الملاحظات يجب ألا تتجاوز 500 حرف")
    private String notes;

    /** مطلوب عند التعديل فقط. */
    private Long version;

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

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getQuantityUnit() {
        return quantityUnit;
    }

    public void setQuantityUnit(String quantityUnit) {
        this.quantityUnit = quantityUnit;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
