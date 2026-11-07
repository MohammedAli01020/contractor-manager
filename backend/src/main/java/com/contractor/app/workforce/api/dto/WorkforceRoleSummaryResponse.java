package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.WorkforceAssignment;
import com.contractor.app.workforce.domain.WorkforceRole;
import java.time.LocalDate;

/** Safe directory summary for an active role assignment; contains no identity secrets. */
public class WorkforceRoleSummaryResponse {
    private final Long assignmentId;
    private final Long roleId;
    private final String roleCode;
    private final String roleName;
    private final Long siteId;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;

    public WorkforceRoleSummaryResponse(WorkforceAssignment assignment, WorkforceRole role) {
        this.assignmentId = assignment.getId();
        this.roleId = role.getId();
        this.roleCode = role.getCode();
        this.roleName = role.getName();
        this.siteId = assignment.getSiteId();
        this.effectiveFrom = assignment.getEffectiveFrom();
        this.effectiveTo = assignment.getEffectiveTo();
    }

    public Long getAssignmentId() { return assignmentId; }
    public Long getRoleId() { return roleId; }
    public String getRoleCode() { return roleCode; }
    public String getRoleName() { return roleName; }
    public Long getSiteId() { return siteId; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
}
