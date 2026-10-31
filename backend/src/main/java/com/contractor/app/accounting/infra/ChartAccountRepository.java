package com.contractor.app.accounting.infra;

import com.contractor.app.accounting.domain.ChartAccount;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartAccountRepository extends JpaRepository<ChartAccount, Long> {
    Optional<ChartAccount> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ChartAccount a where a.id = :id and a.tenantId = :tenantId and a.accountType = com.contractor.app.accounting.domain.AccountType.ASSET and a.accountCode in ('1000', '1010')")
    Optional<ChartAccount> findCashOrBankByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    List<ChartAccount> findByTenantIdOrderByAccountCodeAsc(Long tenantId);
    boolean existsByTenantIdAndAccountCodeIgnoreCase(Long tenantId, String accountCode);
    Optional<ChartAccount> findByTenantIdAndAccountCodeIgnoreCase(Long tenantId, String accountCode);
}
