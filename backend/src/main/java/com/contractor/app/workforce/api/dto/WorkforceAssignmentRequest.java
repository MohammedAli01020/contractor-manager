package com.contractor.app.workforce.api.dto;

import java.time.LocalDate;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class WorkforceAssignmentRequest {

    @NotNull
    private Long roleId;

    private Long siteId;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Size(max = 500)
    private String notes;

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
