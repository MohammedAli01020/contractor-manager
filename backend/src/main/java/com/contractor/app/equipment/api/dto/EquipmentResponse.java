package com.contractor.app.equipment.api.dto;

import com.contractor.app.equipment.domain.Equipment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class EquipmentResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final Long equipmentTypeId;
    private final String equipmentTypeName;
    private final BigDecimal operatingCostPerShift;
    private final BigDecimal defaultShiftPrice;
    private final String status;
    private final String notes;
    private final boolean active;
    private final Long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private EquipmentResponse(Equipment e, String typeName) {
        this.id = e.getId();
        this.code = e.getCode();
        this.name = e.getName();
        this.equipmentTypeId = e.getEquipmentTypeId();
        this.equipmentTypeName = typeName;
        this.operatingCostPerShift = e.getOperatingCostPerShift();
        this.defaultShiftPrice = e.getDefaultShiftPrice();
        this.status = e.getStatus().name();
        this.notes = e.getNotes();
        this.active = e.isActive();
        this.version = e.getVersion();
        this.createdAt = e.getCreatedAt();
        this.updatedAt = e.getUpdatedAt();
    }

    public static EquipmentResponse from(Equipment equipment, String typeName) {
        return new EquipmentResponse(equipment, typeName);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Long getEquipmentTypeId() {
        return equipmentTypeId;
    }

    public String getEquipmentTypeName() {
        return equipmentTypeName;
    }

    public BigDecimal getOperatingCostPerShift() {
        return operatingCostPerShift;
    }

    public BigDecimal getDefaultShiftPrice() {
        return defaultShiftPrice;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
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
