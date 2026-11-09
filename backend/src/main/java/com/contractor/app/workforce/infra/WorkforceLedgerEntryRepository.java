package com.contractor.app.workforce.infra;

import com.contractor.app.workforce.domain.WorkforceLedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Workforce-native ledger queries.
 * Do not add Driver-keyed operational lookups here; legacy IDs are provenance only.
 */
public interface WorkforceLedgerEntryRepository extends JpaRepository<WorkforceLedgerEntry, Long> {

    Optional<WorkforceLedgerEntry> findByTenantIdAndLegacySourceEntryId(Long tenantId, Long legacySourceEntryId);

    Optional<WorkforceLedgerEntry> findByTenantIdAndClientRequestKey(Long tenantId, String clientRequestKey);

    Optional<WorkforceLedgerEntry> findByTenantIdAndJournalEntryId(Long tenantId, Long journalEntryId);

    boolean existsByTenantIdAndShiftIdAndTransactionType(Long tenantId, Long shiftId, String transactionType);

    List<WorkforceLedgerEntry> findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(
            Long tenantId, Long workforceMemberId);

    @Query("select coalesce(sum(e.balanceEffect), 0) from WorkforceLedgerEntry e "
            + "where e.tenantId = :tenantId and e.workforceMemberId = :workforceMemberId")
    BigDecimal sumBalance(@Param("tenantId") Long tenantId,
                          @Param("workforceMemberId") Long workforceMemberId);

    @Query("select coalesce(sum(case when e.transactionType = 'RECOVERABLE_ADVANCE' then e.amount "
            + "when e.transactionType = 'ADVANCE_RECOVERY' then -e.amount else 0 end), 0) "
            + "from WorkforceLedgerEntry e where e.tenantId = :tenantId "
            + "and e.workforceMemberId = :workforceMemberId")
    BigDecimal sumRecoverableAdvanceBalance(@Param("tenantId") Long tenantId,
                                            @Param("workforceMemberId") Long workforceMemberId);
}
