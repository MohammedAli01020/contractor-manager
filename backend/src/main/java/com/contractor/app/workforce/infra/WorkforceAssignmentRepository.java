package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.WorkforceAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkforceAssignmentRepository extends JpaRepository<WorkforceAssignment, Long> {

    List<WorkforceAssignment> findAllByTenantIdAndWorkforceMemberIdOrderByEffectiveFromDescIdDesc(
            Long tenantId, Long workforceMemberId);

    @Query("select case when count(a) > 0 then true else false end from WorkforceAssignment a " +
            "where a.tenantId = :tenantId and a.workforceMemberId = :memberId " +
            "and a.workforceRoleId = :roleId and a.status <> com.contractor.app.workforce.domain.WorkforceAssignmentStatus.CANCELLED " +
            "and (:toDate is null or a.effectiveFrom <= :toDate) " +
            "and (a.effectiveTo is null or a.effectiveTo >= :fromDate)")
    boolean existsOverlappingAssignment(@Param("tenantId") Long tenantId,
                                        @Param("memberId") Long memberId,
                                        @Param("roleId") Long roleId,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from WorkforceAssignment a where a.id = :id and a.tenantId = :tenantId")
    Optional<WorkforceAssignment> findByIdAndTenantIdForUpdate(
            @Param("id") Long id, @Param("tenantId") Long tenantId);
}
