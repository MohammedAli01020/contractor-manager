package com.contractor.app.equipment.infra;

import com.contractor.app.equipment.domain.Equipment;
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

    /** Acquire equipment locks only after driver locks have been acquired. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id and e.tenantId = :tenantId")
    Optional<Equipment> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<Equipment> findByTenantIdAndActiveTrueOrderByCodeAsc(Long tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(Long tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(Long tenantId, String code, Long id);

    boolean existsByTenantIdAndEquipmentTypeIdAndActiveTrue(Long tenantId, Long equipmentTypeId);
}
