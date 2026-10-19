package com.contractor.app.shift.infra;

import com.contractor.app.shift.domain.Shift;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository extends JpaRepository<Shift, Long>, JpaSpecificationExecutor<Shift> {

    Optional<Shift> findByIdAndTenantId(Long id, Long tenantId);

    @Query("select count(s) > 0 from Shift s where s.tenantId = :tenantId and s.equipmentId = :equipmentId "
            + "and s.status = com.contractor.app.shift.domain.ShiftStatus.IN_PROGRESS")
    boolean hasInProgressForEquipment(@Param("tenantId") Long tenantId, @Param("equipmentId") Long equipmentId);

    @Query("select count(s) > 0 from Shift s where s.tenantId = :tenantId and s.driverId = :driverId "
            + "and s.status = com.contractor.app.shift.domain.ShiftStatus.IN_PROGRESS")
    boolean hasInProgressForDriver(@Param("tenantId") Long tenantId, @Param("driverId") Long driverId);
}
