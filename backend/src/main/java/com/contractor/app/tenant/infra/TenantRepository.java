package com.contractor.app.tenant.infra;

import com.contractor.app.tenant.domain.Tenant;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :tenantId")
    Optional<Tenant> findByIdForUpdate(@Param("tenantId") Long tenantId);
}
