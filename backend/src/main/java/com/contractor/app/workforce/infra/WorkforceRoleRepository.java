package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.WorkforceRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkforceRoleRepository extends JpaRepository<WorkforceRole, Long> {
    List<WorkforceRole> findAllByTenantIdOrderByNameAsc(Long tenantId);
    Optional<WorkforceRole> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndCode(Long tenantId, String code);
}
