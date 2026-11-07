package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.WorkforceMember;
import java.time.LocalDateTime;

/** Detail response; national ID is exposed only to tenant administrators. */
public class WorkforceMemberResponse {
    private final Long id;
    private final String fullName;
    private final String phone;
    private final String nationalId;
    private final String notes;
    private final boolean active;
    private final Long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private WorkforceMemberResponse(WorkforceMember member, boolean revealNationalId) {
        this.id = member.getId();
        this.fullName = member.getFullName();
        this.phone = member.getPhone();
        this.nationalId = revealNationalId ? member.getNationalId() : null;
        this.notes = member.getNotes();
        this.active = member.isActive();
        this.version = member.getVersion();
        this.createdAt = member.getCreatedAt();
        this.updatedAt = member.getUpdatedAt();
    }

    public static WorkforceMemberResponse from(WorkforceMember member, boolean revealNationalId) {
        return new WorkforceMemberResponse(member, revealNationalId);
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getNationalId() { return nationalId; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
