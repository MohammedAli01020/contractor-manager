package com.contractor.app.equipment.infra;

import com.contractor.app.equipment.domain.Equipment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {

    Optional<Equipment> findByIdAndTenantId(Long id, Long tenantId);

    List<Equipment> findByTenantIdAndActiveTrueOrderByCodeAsc(Long tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(Long tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(Long tenantId, String code, Long id);

    boolean existsByTenantIdAndEquipmentTypeIdAndActiveTrue(Long tenantId, Long equipmentTypeId);
}
