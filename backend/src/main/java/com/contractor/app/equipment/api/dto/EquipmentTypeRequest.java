package com.contractor.app.equipment.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class EquipmentTypeRequest {

    @NotBlank(message = "اسم النوع مطلوب")
    @Size(max = 100, message = "اسم النوع يجب ألا يتجاوز 100 حرف")
    private String name;

    private Boolean active;

    private Long version;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
