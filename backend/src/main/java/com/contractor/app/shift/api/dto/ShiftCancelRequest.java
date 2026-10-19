package com.contractor.app.shift.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ShiftCancelRequest {

    @NotBlank(message = "سبب الإلغاء مطلوب")
    @Size(max = 300, message = "سبب الإلغاء يجب ألا يتجاوز 300 حرف")
    private String reason;

    @NotNull(message = "رقم النسخة (version) مطلوب")
    private Long version;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
