package com.contractor.app.workforce.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * General person identity. During PR-WF-01 the legacy Driver service remains
 * the only write entry point and synchronizes this compatibility identity
 * within the same database transaction.
 */
@Entity
@Table(name = "workforce_members")
public class WorkforceMember extends BaseTenantEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "national_id", length = 30)
    private String nationalId;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    protected WorkforceMember() {
    }

    public WorkforceMember(Long tenantId, String fullName) {
        super(tenantId);
        this.fullName = fullName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
