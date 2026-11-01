package com.contractor.app.shift.api.dto;

import com.contractor.app.shift.domain.ShiftType;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** طلب تصحيح معتمد لوردية مكتملة؛ لا يسمح بتغيير هوية السائق في الإصدار الأول. */
public class ShiftCorrectionRequest {

    @NotBlank
    @Size(max = 120)
    private String idempotencyKey;

    @NotBlank
    @Size(min = 5, max = 500)
    private String reason;

    @NotNull
    private Long expectedVersion;

    @NotNull
    private LocalDate shiftDate;

    @NotNull
    private ShiftType shiftType;

    @NotNull
    @NotNull
    private Long siteId;

    @NotNull
    @NotNull
    private Long equipmentId;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal driverWage;

    @NotNull
    @DecimalMin(value = "0")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal price;

    @NotNull
    @DecimalMin(value = "0")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal equipmentOperatingCost;

    @Size(max = 500)
    private String notes;

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getReason() { return reason; }
    public Long getExpectedVersion() { return expectedVersion; }
    public LocalDate getShiftDate() { return shiftDate; }
    public ShiftType getShiftType() { return shiftType; }
    public Long getSiteId() { return siteId; }
    public Long getEquipmentId() { return equipmentId; }
    public BigDecimal getDriverWage() { return driverWage; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getEquipmentOperatingCost() { return equipmentOperatingCost; }
    public String getNotes() { return notes; }

    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setReason(String reason) { this.reason = reason; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }
    public void setShiftType(ShiftType shiftType) { this.shiftType = shiftType; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public void setDriverWage(BigDecimal driverWage) { this.driverWage = driverWage; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setEquipmentOperatingCost(BigDecimal equipmentOperatingCost) { this.equipmentOperatingCost = equipmentOperatingCost; }
    public void setNotes(String notes) { this.notes = notes; }
}
