package com.contractor.app.accounting.infra;

import com.contractor.app.accounting.domain.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Page<JournalEntry> findByTenantIdOrderByAccountingDateDescPostedAtDescIdDesc(Long tenantId, Pageable pageable);

    Optional<JournalEntry> findByIdAndTenantId(Long id, Long tenantId);

    Optional<JournalEntry> findByTenantIdAndPostingKey(Long tenantId, String postingKey);

    @org.springframework.data.jpa.repository.Lock(org.springframework.data.jpa.repository.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from JournalEntry e where e.tenantId = :tenantId and e.postingKey = :postingKey")
    Optional<JournalEntry> findByTenantIdAndPostingKeyForUpdate(
            @org.springframework.data.repository.query.Param("tenantId") Long tenantId,
            @org.springframework.data.repository.query.Param("postingKey") String postingKey);

    boolean existsByTenantIdAndReversalOfEntryId(Long tenantId, Long reversalOfEntryId);
}
