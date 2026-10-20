package com.contractor.app.audit.api.dto;

import com.contractor.app.audit.domain.AuditLog;
import java.time.LocalDateTime;

public class AuditResponse {

    private final Long id;
    private final String entityType;
    private final Long entityId;
    private final String action;
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final Long userId;
    private final String userName;
    private final LocalDateTime createdAt;

    public AuditResponse(AuditLog log, String userName) {
        this.id = log.getId();
        this.entityType = log.getEntityType().name();
        this.entityId = log.getEntityId();
        this.action = log.getAction().name();
        this.fieldName = log.getFieldName();
        this.oldValue = log.getOldValue();
        this.newValue = log.getNewValue();
        this.userId = log.getUserId();
        this.userName = userName;
        this.createdAt = log.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
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

    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
