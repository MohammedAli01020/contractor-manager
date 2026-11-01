package com.contractor.app.shift.infra;

import com.contractor.app.shift.domain.ShiftCorrection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftCorrectionRepository extends JpaRepository<ShiftCorrection, Long> {

    Optional<ShiftCorrection> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    Optional<ShiftCorrection> findByTenantIdAndShiftIdAndReplacementWageJournalEntryId(
            Long tenantId, Long shiftId, Long replacementWageJournalEntryId);

    List<ShiftCorrection> findByTenantIdAndShiftIdOrderByCreatedAtDescIdDesc(Long tenantId, Long shiftId);
}
