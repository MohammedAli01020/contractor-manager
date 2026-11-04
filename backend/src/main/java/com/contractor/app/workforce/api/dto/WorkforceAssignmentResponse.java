package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.WorkforceAssignment;
import com.contractor.app.workforce.domain.WorkforceAssignmentStatus;
import java.time.LocalDate;

public class WorkforceAssignmentResponse {
    private final Long id;
    private final Long workforceMemberId;
    private final Long roleId;
    private final Long siteId;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final WorkforceAssignmentStatus status;
    private final String notes;
    private final Long version;

    private WorkforceAssignmentResponse(WorkforceAssignment assignment) {
        this.id = assignment.getId();
        this.workforceMemberId = assignment.getWorkforceMemberId();
        this.roleId = assignment.getWorkforceRoleId();
        this.siteId = assignment.getSiteId();
        this.effectiveFrom = assignment.getEffectiveFrom();
        this.effectiveTo = assignment.getEffectiveTo();
        this.status = assignment.getStatus();
        this.notes = assignment.getNotes();
        this.version = assignment.getVersion();
    }

    public static WorkforceAssignmentResponse from(WorkforceAssignment assignment) {
        return new WorkforceAssignmentResponse(assignment);
    }
    public Long getId() { return id; }
    public Long getWorkforceMemberId() { return workforceMemberId; }
    public Long getRoleId() { return roleId; }
    public Long getSiteId() { return siteId; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public WorkforceAssignmentStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Long getVersion() { return version; }
}
