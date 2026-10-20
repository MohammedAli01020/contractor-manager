package com.contractor.app.shift.infra;

import com.contractor.app.shift.domain.Shift;
import com.contractor.app.shift.domain.ShiftStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShiftRepository extends JpaRepository<Shift, Long>, JpaSpecificationExecutor<Shift> {

    Optional<Shift> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndDriverIdAndStatus(Long tenantId, Long driverId, ShiftStatus status);

    boolean existsByTenantIdAndEquipmentIdAndStatus(Long tenantId, Long equipmentId, ShiftStatus status);

    List<Shift> findByTenantIdAndStatusOrderByShiftDateDescIdDesc(Long tenantId, ShiftStatus status);
}
