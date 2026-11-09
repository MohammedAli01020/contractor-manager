package com.contractor.app.workforce.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class UpdateWorkforceFinancialItemDefinitionRequest {

    @NotNull
    private Long version;

    @NotBlank
    @Size(max = 120)
    private String displayName;

    @Size(max = 500)
    private String description;

    private boolean allowRecurring;
    private boolean allowOneOff;
    private boolean requiresApproval;

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isAllowRecurring() { return allowRecurring; }
    public void setAllowRecurring(boolean allowRecurring) { this.allowRecurring = allowRecurring; }
    public boolean isAllowOneOff() { return allowOneOff; }
    public void setAllowOneOff(boolean allowOneOff) { this.allowOneOff = allowOneOff; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
}
