package com.contractor.app.audit.infra;

import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
            Long tenantId, AuditEntityType entityType, Long entityId);
}
