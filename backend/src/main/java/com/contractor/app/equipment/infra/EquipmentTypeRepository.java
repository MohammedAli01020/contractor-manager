package com.contractor.app.equipment.infra;

import com.contractor.app.equipment.domain.EquipmentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Long> {

    Optional<EquipmentType> findByIdAndTenantId(Long id, Long tenantId);

    List<EquipmentType> findByTenantIdOrderByNameAsc(Long tenantId);

    List<EquipmentType> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);

    List<EquipmentType> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(Long tenantId, String name, Long id);
}
