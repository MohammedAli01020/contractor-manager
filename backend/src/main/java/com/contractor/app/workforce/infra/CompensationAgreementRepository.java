package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.CompensationAgreement;
import com.contractor.app.workforce.domain.CompensationAgreementStatus;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompensationAgreementRepository extends JpaRepository<CompensationAgreement, Long> {

    List<CompensationAgreement> findAllByTenantIdAndWorkforceMemberIdOrderByEffectiveFromDescIdDesc(
            Long tenantId, Long workforceMemberId);

    Optional<CompensationAgreement> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CompensationAgreement a where a.id = :id and a.tenantId = :tenantId")
    Optional<CompensationAgreement> findByIdAndTenantIdForUpdate(
            @Param("id") Long id, @Param("tenantId") Long tenantId);

    boolean existsByTenantIdAndWorkforceAssignmentIdAndStatus(
            Long tenantId, Long workforceAssignmentId, CompensationAgreementStatus status);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from CompensationAgreement a where a.tenantId = :tenantId and a.workforceMemberId = :memberId")
    List<CompensationAgreement> findAllForWageResolution(
            @Param("tenantId") Long tenantId, @Param("memberId") Long memberId);
}
