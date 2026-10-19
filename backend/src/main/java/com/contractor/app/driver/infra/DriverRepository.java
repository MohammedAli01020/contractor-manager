package com.contractor.app.driver.infra;

import com.contractor.app.driver.domain.Driver;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverRepository extends JpaRepository<Driver, Long>, JpaSpecificationExecutor<Driver> {

    Optional<Driver> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.id = :id and d.tenantId = :tenantId")
    Optional<Driver> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Query("select d from Driver d where d.tenantId = :tenantId and d.active = true "
            + "and not exists (select s.id from Shift s where s.tenantId = :tenantId and s.driverId = d.id "
            + "and s.status = com.contractor.app.shift.domain.ShiftStatus.IN_PROGRESS)")
    List<Driver> findAvailableForShift(@Param("tenantId") Long tenantId);
}
