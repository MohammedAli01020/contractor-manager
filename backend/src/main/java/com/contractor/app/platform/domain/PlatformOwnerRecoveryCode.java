package com.contractor.app.platform.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "platform_owner_recovery_codes",
    uniqueConstraints = @UniqueConstraint(name = "uq_platform_owner_recovery_code", columnNames = {"owner_id", "code_hash"}))
public class PlatformOwnerRecoveryCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "code_hash", nullable = false, length = 64, updatable = false)
    private String codeHash;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PlatformOwnerRecoveryCode() { }

    public PlatformOwnerRecoveryCode(Long ownerId, String codeHash) {
        this.ownerId = ownerId;
        this.codeHash = codeHash;
    }

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getCodeHash() { return codeHash; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void markUsed() { usedAt = LocalDateTime.now(ZoneOffset.UTC); }
}
