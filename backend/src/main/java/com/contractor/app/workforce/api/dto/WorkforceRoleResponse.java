package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.WorkforceRole;

public class WorkforceRoleResponse {
    private final Long id;
    private final String code;
    private final String name;
    private final boolean systemDefined;
    private final boolean active;
    private final Long version;

    private WorkforceRoleResponse(WorkforceRole role) {
        this.id = role.getId();
        this.code = role.getCode();
        this.name = role.getName();
        this.systemDefined = role.isSystemDefined();
        this.active = role.isActive();
        this.version = role.getVersion();
    }

    public static WorkforceRoleResponse from(WorkforceRole role) {
        return new WorkforceRoleResponse(role);
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isSystemDefined() { return systemDefined; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
}
