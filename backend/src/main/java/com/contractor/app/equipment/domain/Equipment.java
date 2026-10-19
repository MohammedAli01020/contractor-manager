package com.contractor.app.equipment.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

@Entity
@Table(name = "equipment")
public class Equipment extends BaseTenantEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 150)
    private String name;

    @Column(name = "equipment_type_id", nullable = false)
    private Long equipmentTypeId;

    @Column(name = "operating_cost_per_shift", nullable = false, precision = 15, scale = 2)
    private BigDecimal operatingCostPerShift;

    @Column(name = "default_shift_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal defaultShiftPrice = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    protected Equipment() {
    }

    public Equipment(Long tenantId, String code, Long equipmentTypeId) {
        super(tenantId);
        this.code = code;
        this.equipmentTypeId = equipmentTypeId;
    }

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
