package com.contractor.app.workforce.api.dto;

import javax.validation.constraints.NotNull;

public class WorkforceRoleEligibilityRequest {

    @NotNull
    private Boolean shiftEligible;

    @NotNull
    private Long version;

    public Boolean getShiftEligible() { return shiftEligible; }
    public void setShiftEligible(Boolean shiftEligible) { this.shiftEligible = shiftEligible; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
