package com.contractor.app.shift.api.dto;

import com.contractor.app.shift.domain.ShiftAuditLog;
import java.time.LocalDateTime;

public class ShiftHistoryResponse {

    private final Long id;
    private final String action;
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final Long userId;
    private final String userName;
    private final LocalDateTime createdAt;

    public ShiftHistoryResponse(ShiftAuditLog log, String userName) {
        this.id = log.getId();
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
