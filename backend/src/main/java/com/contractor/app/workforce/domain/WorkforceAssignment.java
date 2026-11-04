package com.contractor.app.workforce.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

@Entity
@Table(name = "workforce_assignments")
public class WorkforceAssignment extends BaseTenantEntity {

    @Column(name = "workforce_member_id", nullable = false)
    private Long workforceMemberId;

    @Column(name = "workforce_role_id", nullable = false)
    private Long workforceRoleId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkforceAssignmentStatus status = WorkforceAssignmentStatus.ACTIVE;

    @Column(length = 500)
    private String notes;

    protected WorkforceAssignment() {
    }

    public WorkforceAssignment(Long tenantId, Long memberId, Long roleId, Long siteId,
                               LocalDate from, LocalDate to, String notes) {
        super(tenantId);
        this.workforceMemberId = memberId;
        this.workforceRoleId = roleId;
        this.siteId = siteId;
        this.effectiveFrom = from;
        this.effectiveTo = to;
        this.notes = notes;
    }

    public Long getWorkforceMemberId() { return workforceMemberId; }
    public Long getWorkforceRoleId() { return workforceRoleId; }
    public Long getSiteId() { return siteId; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public WorkforceAssignmentStatus getStatus() { return status; }
    public String getNotes() { return notes; }

    public void endOn(LocalDate date) {
        this.effectiveTo = date;
        this.status = WorkforceAssignmentStatus.ENDED;
    }
}
