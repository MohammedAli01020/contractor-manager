package com.contractor.app.shift.infra;

import com.contractor.app.shift.domain.ShiftAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftAuditLogRepository extends JpaRepository<ShiftAuditLog, Long> {

    List<ShiftAuditLog> findByShiftIdAndTenantIdOrderByCreatedAtDescIdDesc(Long shiftId, Long tenantId);
}
