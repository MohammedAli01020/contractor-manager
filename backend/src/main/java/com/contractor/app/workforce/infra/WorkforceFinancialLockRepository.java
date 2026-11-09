package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.WorkforceFinancialLock;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkforceFinancialLockRepository extends JpaRepository<WorkforceFinancialLock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from WorkforceFinancialLock l "
            + "where l.tenantId = :tenantId and l.workforceMemberId = :memberId")
    Optional<WorkforceFinancialLock> findByTenantAndMemberForUpdate(
            @Param("tenantId") Long tenantId, @Param("memberId") Long memberId);

    Optional<WorkforceFinancialLock> findByTenantIdAndWorkforceMemberId(
            Long tenantId, Long workforceMemberId);
}
