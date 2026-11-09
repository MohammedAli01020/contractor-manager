package com.contractor.app.workforce.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Dedicated financial serialization mutex. It is deliberately separate from
 * Driver and WorkforceMember rows so legacy identity synchronization can keep
 * its established Driver -> WorkforceMember lock order.
 */
@Entity
@Table(name = "workforce_financial_locks")
public class WorkforceFinancialLock extends BaseTenantEntity {

    @Column(name = "workforce_member_id", nullable = false, updatable = false)
    private Long workforceMemberId;

    protected WorkforceFinancialLock() {
    }

    public WorkforceFinancialLock(Long tenantId, Long workforceMemberId) {
        super(tenantId);
        this.workforceMemberId = workforceMemberId;
    }

    public Long getWorkforceMemberId() {
        return workforceMemberId;
    }
}
