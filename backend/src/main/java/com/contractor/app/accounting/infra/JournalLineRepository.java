package com.contractor.app.accounting.infra;

import com.contractor.app.accounting.domain.JournalLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(Long tenantId, Long journalEntryId);
}
