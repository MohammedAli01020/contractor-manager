package com.contractor.app.shift.api.dto;

import javax.validation.constraints.NotNull;

public class ShiftVersionRequest {

    @NotNull(message = "رقم النسخة (version) مطلوب")
    private Long version;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
