package com.contractor.app.user.domain;

/** Tenant-scoped application roles. Platform owner is intentionally not a tenant user. */
public enum Role {
    ADMIN,
    OPERATOR,
    VIEWER
}
