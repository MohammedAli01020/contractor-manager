package com.contractor.app.platform.api.dto;

import java.time.LocalDateTime;

public class PlatformTenantSummary {
    private final Long tenantId;
    private final String tenantName;
    private final String status;
    private final LocalDateTime createdAt;
    private final PlatformAdminSummary admin;

    public PlatformTenantSummary(Long tenantId, String tenantName, String status,
                                 LocalDateTime createdAt, PlatformAdminSummary admin) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.status = status;
        this.createdAt = createdAt;
        this.admin = admin;
    }

    public Long getTenantId() { return tenantId; }
    public String getTenantName() { return tenantName; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public PlatformAdminSummary getAdmin() { return admin; }
}
