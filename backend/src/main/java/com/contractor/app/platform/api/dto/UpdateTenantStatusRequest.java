package com.contractor.app.platform.api.dto;

import javax.validation.constraints.NotNull;

public class UpdateTenantStatusRequest {
    @NotNull
    private TenantStatus status;

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public enum TenantStatus {
        ACTIVE,
        SUSPENDED
    }
}
