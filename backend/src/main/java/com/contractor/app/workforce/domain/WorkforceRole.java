package com.contractor.app.workforce.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "workforce_roles")
public class WorkforceRole extends BaseTenantEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "shift_eligible", nullable = false)
    private boolean shiftEligible;

    protected WorkforceRole() {
    }

    public WorkforceRole(Long tenantId, String code, String name) {
        super(tenantId);
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isSystemDefined() { return systemDefined; }
    public boolean isActive() { return active; }
    public boolean isShiftEligible() { return shiftEligible; }
    public void setSystemDefined(boolean systemDefined) { this.systemDefined = systemDefined; }
    public void setActive(boolean active) { this.active = active; }
    public void setShiftEligible(boolean shiftEligible) { this.shiftEligible = shiftEligible; }
}
