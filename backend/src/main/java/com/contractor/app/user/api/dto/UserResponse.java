package com.contractor.app.user.api.dto;

import com.contractor.app.user.domain.User;
import java.time.LocalDateTime;

public class UserResponse {

    private final Long id;
    private final Long tenantId;
    private final String username;
    private final String fullName;
    private final String phone;
    private final String role;
    private final boolean active;
    private final LocalDateTime createdAt;

    public UserResponse(Long id, Long tenantId, String username, String fullName, String phone, String role,
                        boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getTenantId(), user.getUsername(), user.getFullName(),
                user.getPhone(), user.getRole().name(), user.isActive(), user.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
