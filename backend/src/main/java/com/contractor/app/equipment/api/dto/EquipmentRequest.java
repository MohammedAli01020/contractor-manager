package com.contractor.app.equipment.api.dto;

import com.contractor.app.equipment.domain.EquipmentStatus;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class EquipmentRequest {

    @NotBlank(message = "كود المعدة مطلوب")
    @Size(max = 50, message = "كود المعدة يجب ألا يتجاوز 50 حرفًا")
    private String code;

    @Size(max = 150, message = "اسم المعدة يجب ألا يتجاوز 150 حرفًا")
    private String name;

    @NotNull(message = "نوع المعدة مطلوب")
    private Long equipmentTypeId;

    @NotNull(message = "تكلفة التشغيل لكل وردية مطلوبة")
    @DecimalMin(value = "0", message = "التكلفة لا يمكن أن تكون سالبة")
    @Digits(integer = 13, fraction = 2, message = "التكلفة: حتى خانتين عشريتين")
    private BigDecimal operatingCostPerShift;

    /** اختياري: سعر الوردية الافتراضي (الإيراد). إن لم يُرسل يبقى 0 عند الإنشاء ويُترك كما هو عند التعديل. */
    @DecimalMin(value = "0", message = "السعر لا يمكن أن يكون سالبًا")
    @Digits(integer = 13, fraction = 2, message = "السعر: حتى خانتين عشريتين")
    private BigDecimal defaultShiftPrice;

    /** اختياري: الافتراضي AVAILABLE عند الإنشاء، ويُترك كما هو عند التعديل إن لم يُرسل. */
    private EquipmentStatus status;

    @Size(max = 500, message = "الملاحظات يجب ألا تتجاوز 500 حرف")
    private String notes;

    private Boolean active;

    private Long version;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getEquipmentTypeId() {
        return equipmentTypeId;
    }

    public void setEquipmentTypeId(Long equipmentTypeId) {
        this.equipmentTypeId = equipmentTypeId;
    }

    public BigDecimal getOperatingCostPerShift() {
        return operatingCostPerShift;
    }

    public void setOperatingCostPerShift(BigDecimal operatingCostPerShift) {
        this.operatingCostPerShift = operatingCostPerShift;
    }

    public BigDecimal getDefaultShiftPrice() {
        return defaultShiftPrice;
    }

    public void setDefaultShiftPrice(BigDecimal defaultShiftPrice) {
        this.defaultShiftPrice = defaultShiftPrice;
    }

    public EquipmentStatus getStatus() {
        return status;
    }

    public void setStatus(EquipmentStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
