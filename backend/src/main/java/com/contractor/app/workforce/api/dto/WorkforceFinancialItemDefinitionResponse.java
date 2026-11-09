package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.WorkforceFinancialItemDefinition;
import com.contractor.app.workforce.domain.WorkforceFinancialItemCalculationMethod;
import com.contractor.app.workforce.domain.WorkforceFinancialItemFamily;
import java.time.LocalDateTime;

public class WorkforceFinancialItemDefinitionResponse {
    private final Long id;
    private final String itemCode;
    private final String displayName;
    private final String description;
    private final WorkforceFinancialItemFamily family;
    private final WorkforceFinancialItemCalculationMethod calculationMethod;
    private final boolean allowRecurring;
    private final boolean allowOneOff;
    private final boolean requiresApproval;
    private final boolean active;
    private final Long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private WorkforceFinancialItemDefinitionResponse(WorkforceFinancialItemDefinition item) {
        id = item.getId();
        itemCode = item.getItemCode();
        displayName = item.getDisplayName();
        description = item.getDescription();
        family = item.getFamily();
        calculationMethod = item.getCalculationMethod();
        allowRecurring = item.isAllowRecurring();
        allowOneOff = item.isAllowOneOff();
        requiresApproval = item.isRequiresApproval();
        active = item.isActive();
        version = item.getVersion();
        createdAt = item.getCreatedAt();
        updatedAt = item.getUpdatedAt();
    }

    public static WorkforceFinancialItemDefinitionResponse from(WorkforceFinancialItemDefinition item) {
        return new WorkforceFinancialItemDefinitionResponse(item);
    }

    public Long getId() { return id; }
    public String getItemCode() { return itemCode; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public WorkforceFinancialItemFamily getFamily() { return family; }
    public WorkforceFinancialItemCalculationMethod getCalculationMethod() { return calculationMethod; }
    public boolean isAllowRecurring() { return allowRecurring; }
    public boolean isAllowOneOff() { return allowOneOff; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
