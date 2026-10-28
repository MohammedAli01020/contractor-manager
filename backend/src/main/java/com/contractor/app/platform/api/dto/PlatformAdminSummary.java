package com.contractor.app.platform.api.dto;

/** Safe, read-only summary of a tenant administrator for the Platform Owner dashboard. */
public class PlatformAdminSummary {
    private final Long userId;
    private final String fullName;
    private final String username;
    private final boolean active;
    private final boolean passwordActivated;
    private final String phone;
    private final String email;
    private final boolean emailVerified;

    public PlatformAdminSummary(Long userId, String fullName, String username,
                                boolean active, boolean passwordActivated) {
        this(userId, fullName, username, active, passwordActivated, null, null, false);
    }

    public PlatformAdminSummary(Long userId, String fullName, String username,
                                boolean active, boolean passwordActivated,
                                String phone, String email, boolean emailVerified) {
        this.userId = userId;
        this.fullName = fullName;
        this.username = username;
        this.active = active;
        this.passwordActivated = passwordActivated;
        this.phone = phone;
        this.email = email;
        this.emailVerified = emailVerified;
    }

    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public boolean isActive() { return active; }
    public boolean isPasswordActivated() { return passwordActivated; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
}
