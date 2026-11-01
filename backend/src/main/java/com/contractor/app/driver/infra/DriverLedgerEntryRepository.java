package com.contractor.app.driver.infra;

import com.contractor.app.driver.domain.DriverLedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverLedgerEntryRepository extends JpaRepository<DriverLedgerEntry, Long> {
    Optional<DriverLedgerEntry> findByTenantIdAndClientRequestKey(Long tenantId, String key);
    List<DriverLedgerEntry> findByTenantIdAndDriverIdOrderByTransactionDateAscIdAsc(Long tenantId, Long driverId);
    /**
     * Locking/current read for balance decisions made after the driver row is locked.
     * A plain aggregate SELECT can reuse an older REPEATABLE READ snapshot created before
     * the driver lock, allowing two serialized payments to both observe the same old balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DriverLedgerEntry e where e.tenantId=:tenantId and e.driverId=:driverId order by e.transactionDate asc, e.id asc")
    List<DriverLedgerEntry> findByTenantIdAndDriverIdOrderByTransactionDateAscIdAscForUpdate(
            @Param("tenantId") Long tenantId, @Param("driverId") Long driverId);

    default BigDecimal sumBalanceForUpdate(Long tenantId, Long driverId) {
        return findByTenantIdAndDriverIdOrderByTransactionDateAscIdAscForUpdate(tenantId, driverId).stream()
                .map(DriverLedgerEntry::getBalanceEffect)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    default BigDecimal sumRecoverableAdvanceBalanceForUpdate(Long tenantId, Long driverId) {
        return findByTenantIdAndDriverIdOrderByTransactionDateAscIdAscForUpdate(tenantId, driverId).stream()
                .map(entry -> {
                    if ("RECOVERABLE_ADVANCE".equals(entry.getTransactionType())) return entry.getAmount();
                    if ("ADVANCE_RECOVERY".equals(entry.getTransactionType())) return entry.getAmount().negate();
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    @Query("select coalesce(sum(e.balanceEffect), 0) from DriverLedgerEntry e where e.tenantId=:tenantId and e.driverId=:driverId")
    BigDecimal sumBalance(@Param("tenantId") Long tenantId, @Param("driverId") Long driverId);
    @Query("select coalesce(sum(case when e.transactionType='RECOVERABLE_ADVANCE' then e.amount when e.transactionType='ADVANCE_RECOVERY' then -e.amount else 0 end), 0) from DriverLedgerEntry e where e.tenantId=:tenantId and e.driverId=:driverId")
    BigDecimal sumRecoverableAdvanceBalance(@Param("tenantId") Long tenantId, @Param("driverId") Long driverId);
}