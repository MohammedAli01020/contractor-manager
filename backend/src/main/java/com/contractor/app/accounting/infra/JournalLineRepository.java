package com.contractor.app.accounting.infra;

import com.contractor.app.accounting.domain.JournalLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(Long tenantId, Long journalEntryId);

    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from JournalLine l where l.tenantId = :tenantId and l.journalEntryId = :journalEntryId order by l.lineNumber asc")
    List<JournalLine> findByTenantIdAndJournalEntryIdOrderByLineNumberAscForUpdate(
            @org.springframework.data.repository.query.Param("tenantId") Long tenantId,
            @org.springframework.data.repository.query.Param("journalEntryId") Long journalEntryId);

    @Transactional
    @Query(value = "select coalesce(sum(debit - credit), 0) from journal_lines where tenant_id = :tenantId and account_id = :accountId for update", nativeQuery = true)
    BigDecimal calculateDebitNormalBalance(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId);
}
