package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.WorkforceFinancialItemCalculationMethod;
import com.contractor.app.workforce.domain.WorkforceFinancialItemFamily;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class WorkforceFinancialItemDefinitionRequest {

    @NotBlank
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,59}")
    private String itemCode;

    @NotBlank
    @Size(max = 120)
    private String displayName;

    @Size(max = 500)
    private String description;

    @NotNull
    private WorkforceFinancialItemFamily family;

    @NotNull
    private WorkforceFinancialItemCalculationMethod calculationMethod;

    private boolean allowRecurring;
    private boolean allowOneOff = true;
    private boolean requiresApproval = true;

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public WorkforceFinancialItemFamily getFamily() { return family; }
    public void setFamily(WorkforceFinancialItemFamily family) { this.family = family; }
    public WorkforceFinancialItemCalculationMethod getCalculationMethod() { return calculationMethod; }
    public void setCalculationMethod(WorkforceFinancialItemCalculationMethod calculationMethod) {
        this.calculationMethod = calculationMethod;
    }
    public boolean isAllowRecurring() { return allowRecurring; }
    public void setAllowRecurring(boolean allowRecurring) { this.allowRecurring = allowRecurring; }
    public boolean isAllowOneOff() { return allowOneOff; }
    public void setAllowOneOff(boolean allowOneOff) { this.allowOneOff = allowOneOff; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
}
