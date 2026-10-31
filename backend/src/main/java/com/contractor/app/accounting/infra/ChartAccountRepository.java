package com.contractor.app.accounting.infra;

import com.contractor.app.accounting.domain.ChartAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartAccountRepository extends JpaRepository<ChartAccount, Long> {
    Optional<ChartAccount> findByIdAndTenantId(Long id, Long tenantId);
    List<ChartAccount> findByTenantIdOrderByAccountCodeAsc(Long tenantId);
    boolean existsByTenantIdAndAccountCodeIgnoreCase(Long tenantId, String accountCode);
    Optional<ChartAccount> findByTenantIdAndAccountCodeIgnoreCase(Long tenantId, String accountCode);
}
