package com.contractor.app.common.security;

public class TokenClaims {

    private final Long userId;
    private final Long tenantId;

    public TokenClaims(Long userId, Long tenantId) {
        this.userId = userId;
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }
}
