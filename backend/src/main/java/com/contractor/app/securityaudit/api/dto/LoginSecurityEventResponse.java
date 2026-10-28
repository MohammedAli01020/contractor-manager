package com.contractor.app.securityaudit.api.dto;

import com.contractor.app.securityaudit.domain.LoginSecurityEvent;
import java.time.LocalDateTime;

public class LoginSecurityEventResponse {
    private final Long id;
    private final String principalType;
    private final Long principalId;
    private final Long tenantId;
    private final String username;
    private final String outcome;
    private final String reason;
    private final String clientIp;
    private final String requestId;
    private final LocalDateTime createdAt;

    public LoginSecurityEventResponse(LoginSecurityEvent event) {
        this.id = event.getId();
        this.principalType = event.getPrincipalType().name();
        this.principalId = event.getPrincipalId();
        this.tenantId = event.getTenantId();
        this.username = event.getUsername();
        this.outcome = event.getOutcome().name();
        this.reason = event.getReason();
        this.clientIp = event.getClientIp();
        this.requestId = event.getRequestId();
        this.createdAt = event.getCreatedAt();
    }

    public Long getId() { return id; }
    public String getPrincipalType() { return principalType; }
    public Long getPrincipalId() { return principalId; }
    public Long getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public String getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public String getClientIp() { return clientIp; }
    public String getRequestId() { return requestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
