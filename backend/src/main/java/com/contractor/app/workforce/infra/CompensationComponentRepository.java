package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.CompensationComponent;
import java.util.List;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompensationComponentRepository extends JpaRepository<CompensationComponent, Long> {

    List<CompensationComponent> findAllByTenantIdAndCompensationAgreementIdOrderBySortOrderAscIdAsc(
            Long tenantId, Long compensationAgreementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompensationComponent c where c.id = :id and c.tenantId = :tenantId")
    java.util.Optional<CompensationComponent> findByIdAndTenantIdForUpdate(
            @Param("id") Long id, @Param("tenantId") Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from CompensationComponent c where c.tenantId = :tenantId and c.compensationAgreementId = :agreementId order by c.sortOrder asc, c.id asc")
    List<CompensationComponent> findAllForWageResolution(
            @Param("tenantId") Long tenantId, @Param("agreementId") Long agreementId);
}
