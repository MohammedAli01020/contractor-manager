package com.contractor.app.common.security;

public class TokenClaims {
    private final Long userId;
    private final Long tenantId;
    private final boolean platformOwner;

    public TokenClaims(Long userId, Long tenantId) { this(userId, tenantId, false); }
    public TokenClaims(Long userId, Long tenantId, boolean platformOwner) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.platformOwner = platformOwner;
    }
    public Long getUserId() { return userId; }
    public Long getTenantId() { return tenantId; }
    public boolean isPlatformOwner() { return platformOwner; }
}
