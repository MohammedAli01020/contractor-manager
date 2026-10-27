package com.contractor.app.platform.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name = "platform_owners")
public class PlatformOwner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "totp_secret", length = 128)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Column(name = "totp_setup_expires_at")
    private LocalDateTime totpSetupExpiresAt;

    @Column(name = "totp_enabled_at")
    private LocalDateTime totpEnabledAt;

    @Column(name = "totp_last_used_step")
    private Long totpLastUsedStep;

    @Column(name = "totp_failed_attempts", nullable = false)
    private int totpFailedAttempts = 0;

    @Column(name = "totp_locked_until")
    private LocalDateTime totpLockedUntil;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PlatformOwner() { }

    public PlatformOwner(String username, String fullName, String passwordHash) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public String getTotpSecret() { return totpSecret; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public LocalDateTime getTotpSetupExpiresAt() { return totpSetupExpiresAt; }
    public LocalDateTime getTotpEnabledAt() { return totpEnabledAt; }
    public Long getTotpLastUsedStep() { return totpLastUsedStep; }
    public int getTotpFailedAttempts() { return totpFailedAttempts; }
    public LocalDateTime getTotpLockedUntil() { return totpLockedUntil; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
    public void setTotpSetupExpiresAt(LocalDateTime totpSetupExpiresAt) { this.totpSetupExpiresAt = totpSetupExpiresAt; }
    public void setTotpEnabledAt(LocalDateTime totpEnabledAt) { this.totpEnabledAt = totpEnabledAt; }
    public void setTotpLastUsedStep(Long totpLastUsedStep) { this.totpLastUsedStep = totpLastUsedStep; }
    public void setTotpFailedAttempts(int totpFailedAttempts) { this.totpFailedAttempts = totpFailedAttempts; }
    public void setTotpLockedUntil(LocalDateTime totpLockedUntil) { this.totpLockedUntil = totpLockedUntil; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setActive(boolean active) { this.active = active; }
}
