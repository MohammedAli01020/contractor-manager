package com.contractor.app.driver.infra;

import com.contractor.app.driver.domain.Driver;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DriverRepository extends JpaRepository<Driver, Long>, JpaSpecificationExecutor<Driver> {

    Optional<Driver> findByIdAndTenantId(Long id, Long tenantId);

    List<Driver> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
}
