package com.contractor.app.workforce.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "workforce_financial_item_definitions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workforce_financial_items_tenant_code",
                columnNames = {"tenant_id", "item_code"}))
public class WorkforceFinancialItemDefinition extends BaseTenantEntity {

    @Column(name = "item_code", nullable = false, length = 60, updatable = false)
    private String itemCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_family", nullable = false, length = 20, updatable = false)
    private WorkforceFinancialItemFamily family;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method", nullable = false, length = 24, updatable = false)
    private WorkforceFinancialItemCalculationMethod calculationMethod;

    @Column(name = "allow_recurring", nullable = false)
    private boolean allowRecurring;

    @Column(name = "allow_one_off", nullable = false)
    private boolean allowOneOff;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(nullable = false)
    private boolean active = true;

    protected WorkforceFinancialItemDefinition() {
    }

    public WorkforceFinancialItemDefinition(Long tenantId, String itemCode, String displayName,
            String description, WorkforceFinancialItemFamily family,
            WorkforceFinancialItemCalculationMethod calculationMethod,
            boolean allowRecurring, boolean allowOneOff, boolean requiresApproval) {
        super(tenantId);
        this.itemCode = itemCode;
        this.displayName = displayName;
        this.description = description;
        this.family = family;
        this.calculationMethod = calculationMethod;
        this.allowRecurring = allowRecurring;
        this.allowOneOff = allowOneOff;
        this.requiresApproval = requiresApproval;
    }

    public void updateMetadata(Long expectedVersion, String displayName, String description,
            boolean allowRecurring, boolean allowOneOff, boolean requiresApproval) {
        requireVersion(expectedVersion);
        this.displayName = displayName;
        this.description = description;
        this.allowRecurring = allowRecurring;
        this.allowOneOff = allowOneOff;
        this.requiresApproval = requiresApproval;
    }

    public void deactivate(Long expectedVersion) {
        requireVersion(expectedVersion);
        this.active = false;
    }

    public String getItemCode() { return itemCode; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public WorkforceFinancialItemFamily getFamily() { return family; }
    public WorkforceFinancialItemCalculationMethod getCalculationMethod() { return calculationMethod; }
    public boolean isAllowRecurring() { return allowRecurring; }
    public boolean isAllowOneOff() { return allowOneOff; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public boolean isActive() { return active; }
}
