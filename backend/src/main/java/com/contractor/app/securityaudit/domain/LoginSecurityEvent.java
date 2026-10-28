package com.contractor.app.securityaudit.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Index;

/**
 * Append-only security record for authentication attempts. Never store credentials or tokens here.
 */
@Entity
@Table(name = "login_security_events", indexes = {
        @Index(name = "idx_login_event_type_created", columnList = "principal_type, created_at, id"),
        @Index(name = "idx_login_event_tenant_created", columnList = "tenant_id, created_at, id"),
        @Index(name = "idx_login_event_username_created", columnList = "username, created_at")
})
public class LoginSecurityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 30, updatable = false)
    private LoginPrincipalType principalType;

    @Column(name = "principal_id", updatable = false)
    private Long principalId;

    @Column(name = "tenant_id", updatable = false)
    private Long tenantId;

    @Column(nullable = false, length = 60, updatable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private LoginEventOutcome outcome;

    @Column(length = 50, updatable = false)
    private String reason;

    @Column(name = "client_ip", length = 45, updatable = false)
    private String clientIp;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LoginSecurityEvent() { }

    public LoginSecurityEvent(LoginPrincipalType principalType, Long principalId, Long tenantId,
                              String username, LoginEventOutcome outcome, String reason,
                              String clientIp, String requestId) {
        this.principalType = principalType;
        this.principalId = principalId;
        this.tenantId = tenantId;
        this.username = safeUsername(username);
        this.outcome = outcome;
        this.reason = safeReason(reason);
        this.clientIp = clip(clientIp, 45);
        this.requestId = clip(requestId, 64);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String safeUsername(String value) {
        if (value == null || value.trim().isEmpty()) return "(empty)";
        return clip(value.trim().toLowerCase(java.util.Locale.ROOT), 60);
    }

    private static String safeReason(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim().replaceAll("[^A-Za-z0-9_:-]", "_");
        return clip(normalized, 50);
    }

    private static String clip(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() { return id; }
    public LoginPrincipalType getPrincipalType() { return principalType; }
    public Long getPrincipalId() { return principalId; }
    public Long getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public LoginEventOutcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public String getClientIp() { return clientIp; }
    public String getRequestId() { return requestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
