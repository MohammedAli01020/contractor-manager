package com.contractor.app.equipment.infra;

import com.contractor.app.equipment.domain.Equipment;
import com.contractor.app.equipment.domain.EquipmentStatus;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {

    Optional<Equipment> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(Long tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(Long tenantId, String code, Long id);

    boolean existsByTenantIdAndEquipmentTypeIdAndActiveTrue(Long tenantId, Long equipmentTypeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id and e.tenantId = :tenantId")
    Optional<Equipment> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Query("select e from Equipment e where e.tenantId = :tenantId and e.active = true and e.status = :status "
            + "and not exists (select s.id from Shift s where s.tenantId = :tenantId and s.equipmentId = e.id "
            + "and s.status = com.contractor.app.shift.domain.ShiftStatus.IN_PROGRESS)")
    List<Equipment> findAvailableForShift(@Param("tenantId") Long tenantId, @Param("status") EquipmentStatus status);
}
