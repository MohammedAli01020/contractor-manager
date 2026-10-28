package com.contractor.app.accounting.infra;

import com.contractor.app.accounting.domain.JournalEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByIdAndTenantId(Long id, Long tenantId);

    Optional<JournalEntry> findByTenantIdAndPostingKey(Long tenantId, String postingKey);
}
