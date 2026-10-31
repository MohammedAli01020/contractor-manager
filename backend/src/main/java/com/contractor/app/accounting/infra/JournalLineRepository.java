package com.contractor.app.accounting.infra;

import com.contractor.app.accounting.domain.JournalLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(Long tenantId, Long journalEntryId);

    @Query(value = "select coalesce(sum(debit - credit), 0) from journal_lines where tenant_id = :tenantId and account_id = :accountId for update", nativeQuery = true)
    BigDecimal calculateDebitNormalBalance(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId);
}
