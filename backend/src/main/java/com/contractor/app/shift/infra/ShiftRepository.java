package com.contractor.app.shift.infra;

import com.contractor.app.shift.domain.Shift;
import com.contractor.app.shift.domain.ShiftType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository extends JpaRepository<Shift, Long>, JpaSpecificationExecutor<Shift> {

    Optional<Shift> findByIdAndTenantId(Long id, Long tenantId);

    /** excludeId = -1 يعني "لا استثناء" (المعرّفات دائمًا موجبة). */
    @Query("select count(s) > 0 from Shift s where s.tenantId = :tenantId and s.equipmentId = :equipmentId "
            + "and s.shiftDate = :date and s.shiftType = :type "
            + "and s.status = com.contractor.app.shift.domain.ShiftStatus.COMPLETED and s.id <> :excludeId")
    boolean equipmentBusy(@Param("tenantId") Long tenantId, @Param("equipmentId") Long equipmentId,
                          @Param("date") LocalDate date, @Param("type") ShiftType type,
                          @Param("excludeId") Long excludeId);

    @Query("select count(s) > 0 from Shift s where s.tenantId = :tenantId and s.driverId = :driverId "
            + "and s.shiftDate = :date and s.shiftType = :type "
            + "and s.status = com.contractor.app.shift.domain.ShiftStatus.COMPLETED and s.id <> :excludeId")
    boolean driverBusy(@Param("tenantId") Long tenantId, @Param("driverId") Long driverId,
                       @Param("date") LocalDate date, @Param("type") ShiftType type,
                       @Param("excludeId") Long excludeId);
}
