package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.workforce.infra.WorkforceFinancialLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides the dedicated mutex primitive for future coordinated financial
 * command adoption. This service does not change any writer until every
 * balance-affecting entry point is migrated in one reviewed release.
 */
@Service
public class WorkforceFinancialLockService {

    private final WorkforceFinancialLockRepository locks;

    public WorkforceFinancialLockService(WorkforceFinancialLockRepository locks) {
        this.locks = locks;
    }

    @Transactional
    public void createForMember(WorkforceMember member) {
        locks.saveAndFlush(new WorkforceFinancialLock(member.getTenantId(), member.getId()));
    }

    /**
     * Must be called inside the financial command transaction before acquiring
     * Driver, WorkforceMember, Shift, or Equipment row locks.
     */
    @Transactional
    public WorkforceFinancialLock lockForFinancialUpdate(Long tenantId, Long memberId) {
        return locks.findByTenantAndMemberForUpdate(tenantId, memberId)
                .orElseThrow(() -> ApiException.conflict(
                        "تعذر العثور على قفل المعاملات المالية للشخص. أوقف العملية وراجع بيانات القوى العاملة."));
    }
}
