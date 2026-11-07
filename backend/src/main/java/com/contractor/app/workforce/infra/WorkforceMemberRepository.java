package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.WorkforceMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = "select m.id as workforceMemberId, d.id as driverId, "
            + "m.fullName as fullName, m.phone as phone, m.active as active, "
            + "d.workerType as workerType, d.active as driverActive "
            + "from WorkforceMember m left join Driver d "
            + "on d.workforceMemberId = m.id and d.tenantId = m.tenantId "
            + "where m.tenantId = :tenantId "
            + "and (:includeInactive = true or m.active = true) "
            + "and (:search is null or lower(m.fullName) like lower(concat('%', :search, '%')) "
            + "or lower(coalesce(m.phone, '')) like lower(concat('%', :search, '%'))) "
            + "order by m.fullName asc",
            countQuery = "select count(m) from WorkforceMember m "
                    + "where m.tenantId = :tenantId "
                    + "and (:includeInactive = true or m.active = true) "
                    + "and (:search is null or lower(m.fullName) like lower(concat('%', :search, '%')) "
                    + "or lower(coalesce(m.phone, '')) like lower(concat('%', :search, '%')))")
    Page<WorkforceMemberDirectoryRow> searchDirectory(
            @Param("tenantId") Long tenantId,
            @Param("includeInactive") boolean includeInactive,
            @Param("search") String search,
            Pageable pageable);
}
