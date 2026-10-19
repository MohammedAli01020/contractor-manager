package com.contractor.app.common.security;

import com.contractor.app.user.domain.Role;

/** هوية المستخدم الحالي داخل الطلب. tenantId هنا هو المصدر الوحيد الموثوق لعزل البيانات. */
public class AuthenticatedUser {

    private final Long userId;
    private final Long tenantId;
    private final String username;
    private final Role role;

    public AuthenticatedUser(Long userId, Long tenantId, String username, Role role) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }
}
