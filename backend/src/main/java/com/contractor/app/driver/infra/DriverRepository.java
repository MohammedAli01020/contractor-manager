package com.contractor.app.driver.infra;

import com.contractor.app.driver.domain.Driver;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import javax.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface DriverRepository extends JpaRepository<Driver, Long>, JpaSpecificationExecutor<Driver> {

    Optional<Driver> findByIdAndTenantId(Long id, Long tenantId);

    /** Keep the global lock order: driver rows first, then equipment rows. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
    @Query("select d from Driver d where d.id = :id and d.tenantId = :tenantId")
    Optional<Driver> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<Driver> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
}
