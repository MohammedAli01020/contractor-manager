package com.contractor.app.platform.api.dto;

import java.time.LocalDateTime;

public class PlatformTenantSummary {
    private final Long tenantId;
    private final String tenantName;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final int totalUsers;
    private final int activeUsers;
    private final PlatformAdminSummary admin;

    public PlatformTenantSummary(Long tenantId, String tenantName, String status,
                                 LocalDateTime createdAt, PlatformAdminSummary admin) {
        this(tenantId, tenantName, status, createdAt, null, 0, 0, admin);
    }

    public PlatformTenantSummary(Long tenantId, String tenantName, String status,
                                 LocalDateTime createdAt, LocalDateTime updatedAt,
                                 PlatformAdminSummary admin) {
        this(tenantId, tenantName, status, createdAt, updatedAt, 0, 0, admin);
    }

    public PlatformTenantSummary(Long tenantId, String tenantName, String status,
                                 LocalDateTime createdAt, LocalDateTime updatedAt,
                                 int totalUsers, int activeUsers, PlatformAdminSummary admin) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.totalUsers = totalUsers;
        this.activeUsers = activeUsers;
        this.admin = admin;
    }

    public Long getTenantId() { return tenantId; }
    public String getTenantName() { return tenantName; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public int getTotalUsers() { return totalUsers; }
    public int getActiveUsers() { return activeUsers; }
    public PlatformAdminSummary getAdmin() { return admin; }
}
