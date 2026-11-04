package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.WorkforceMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkforceMemberRepository extends JpaRepository<WorkforceMember, Long> {

    Optional<WorkforceMember> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from WorkforceMember m where m.id = :id and m.tenantId = :tenantId")
    Optional<WorkforceMember> findByIdAndTenantIdForUpdate(
            @Param("id") Long id, @Param("tenantId") Long tenantId);

    List<WorkforceMember> findAllByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);
}
