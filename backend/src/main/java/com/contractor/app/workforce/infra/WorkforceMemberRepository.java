package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.WorkforceMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkforceMemberRepository extends JpaRepository<WorkforceMember, Long> {

    Optional<WorkforceMember> findByIdAndTenantId(Long id, Long tenantId);
}
