package com.contractor.app.shift.domain;

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

/** سطر في سجل التعديلات. لا يوجد أي setter عمدًا: السجل لا يُعدَّل بعد كتابته. */
@Entity
@Table(name = "shift_audit_log")
public class ShiftAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "shift_id", nullable = false, updatable = false)
    private Long shiftId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ShiftAuditAction action;

    @Column(name = "field_name", updatable = false, length = 40)
    private String fieldName;

    @Column(name = "old_value", updatable = false, length = 500)
    private String oldValue;

    @Column(name = "new_value", updatable = false, length = 500)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ShiftAuditLog() {
    }

    public ShiftAuditLog(Long tenantId, Long shiftId, Long userId, ShiftAuditAction action, String fieldName,
                         String oldValue, String newValue) {
        this.tenantId = tenantId;
        this.shiftId = shiftId;
        this.userId = userId;
        this.action = action;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public ShiftAuditAction getAction() {
        return action;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
