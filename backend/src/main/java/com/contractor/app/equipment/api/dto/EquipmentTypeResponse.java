package com.contractor.app.equipment.api.dto;

import com.contractor.app.equipment.domain.EquipmentType;

public class EquipmentTypeResponse {

    private final Long id;
    private final String name;
    private final boolean active;
    private final Long version;

    private EquipmentTypeResponse(EquipmentType t) {
        this.id = t.getId();
        this.name = t.getName();
        this.active = t.isActive();
        this.version = t.getVersion();
    }

    public static EquipmentTypeResponse from(EquipmentType type) {
        return new EquipmentTypeResponse(type);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }
}
