package com.contractor.app.shift.infra;

import com.contractor.app.shift.domain.Shift;
import com.contractor.app.shift.domain.ShiftStatus;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHint;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShiftRepository extends JpaRepository<Shift, Long>, JpaSpecificationExecutor<Shift> {

    Optional<Shift> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shift s where s.id = :id and s.tenantId = :tenantId")
    Optional<Shift> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    boolean existsByTenantIdAndDriverIdAndStatus(Long tenantId, Long driverId, ShiftStatus status);

    boolean existsByTenantIdAndEquipmentIdAndStatus(Long tenantId, Long equipmentId, ShiftStatus status);

    boolean existsByTenantIdAndDriverIdAndStatusAndIdNot(
            Long tenantId, Long driverId, ShiftStatus status, Long excludedShiftId);

    boolean existsByTenantIdAndEquipmentIdAndStatusAndIdNot(
            Long tenantId, Long equipmentId, ShiftStatus status, Long excludedShiftId);

    List<Shift> findByTenantIdAndStatusOrderByShiftDateDescIdDesc(Long tenantId, ShiftStatus status);
}
