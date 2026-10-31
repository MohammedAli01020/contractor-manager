package com.contractor.app.driver.infra;

import com.contractor.app.driver.domain.DriverLedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverLedgerEntryRepository extends JpaRepository<DriverLedgerEntry, Long> {
    Optional<DriverLedgerEntry> findByTenantIdAndClientRequestKey(Long tenantId, String key);
    List<DriverLedgerEntry> findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(Long tenantId, Long driverId);
    @Query("select coalesce(sum(e.balanceEffect), 0) from DriverLedgerEntry e where e.tenantId=:tenantId and e.driverId=:driverId")
    BigDecimal sumBalance(@Param("tenantId") Long tenantId, @Param("driverId") Long driverId);
}