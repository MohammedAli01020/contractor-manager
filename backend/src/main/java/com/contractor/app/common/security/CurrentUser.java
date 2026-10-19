package com.contractor.app.common.security;

import com.contractor.app.common.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser)) {
            throw ApiException.unauthorized("يجب تسجيل الدخول أولًا.");
        }
        return (AuthenticatedUser) auth.getPrincipal();
    }

    public static Long tenantId() {
        return get().getTenantId();
    }
}
