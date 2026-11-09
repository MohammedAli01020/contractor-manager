package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.WorkforceFinancialItemDefinition;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkforceFinancialItemDefinitionRepository
        extends JpaRepository<WorkforceFinancialItemDefinition, Long> {

    List<WorkforceFinancialItemDefinition> findAllByTenantIdOrderByDisplayNameAscIdAsc(Long tenantId);

    List<WorkforceFinancialItemDefinition> findAllByTenantIdAndActiveOrderByDisplayNameAscIdAsc(
            Long tenantId, boolean active);

    Optional<WorkforceFinancialItemDefinition> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndItemCode(Long tenantId, String itemCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from WorkforceFinancialItemDefinition i where i.id = :id and i.tenantId = :tenantId")
    Optional<WorkforceFinancialItemDefinition> findByIdAndTenantIdForUpdate(
            @Param("id") Long id, @Param("tenantId") Long tenantId);
}
