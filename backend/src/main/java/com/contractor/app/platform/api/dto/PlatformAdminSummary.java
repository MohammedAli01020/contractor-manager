package com.contractor.app.platform.api.dto;

/** Safe, read-only summary of a tenant administrator for the Platform Owner dashboard. */
public class PlatformAdminSummary {
    private final Long userId;
    private final String fullName;
    private final String username;
    private final boolean active;
    private final boolean passwordActivated;

    public PlatformAdminSummary(Long userId, String fullName, String username,
                                boolean active, boolean passwordActivated) {
        this.userId = userId;
        this.fullName = fullName;
        this.username = username;
        this.active = active;
        this.passwordActivated = passwordActivated;
    }

    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public boolean isActive() { return active; }
    public boolean isPasswordActivated() { return passwordActivated; }
}
