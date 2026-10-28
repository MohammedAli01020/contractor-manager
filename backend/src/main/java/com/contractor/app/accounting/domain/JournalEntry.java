package com.contractor.app.accounting.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

@Entity
@Table(name = "journal_entries")
public class JournalEntry extends BaseTenantEntity {

    @Column(name = "posting_key", nullable = false, length = 120, updatable = false)
    private String postingKey;

    @Column(name = "source_type", nullable = false, length = 40, updatable = false)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(name = "accounting_date", nullable = false, updatable = false)
    private LocalDate accountingDate;

    @Column(nullable = false, length = 300, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15, updatable = false)
    private JournalEntryStatus status = JournalEntryStatus.POSTED;

    @Column(name = "reversal_of_entry_id", updatable = false)
    private Long reversalOfEntryId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    @Column(name = "posted_by_user_id", nullable = false, updatable = false)
    private Long postedByUserId;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private LocalDateTime postedAt;

    protected JournalEntry() {
    }

    public JournalEntry(Long tenantId, String postingKey, String sourceType, Long sourceId,
                        LocalDate accountingDate, String description, Long actorUserId,
                        Long reversalOfEntryId, LocalDateTime postedAt) {
        super(tenantId);
        this.postingKey = postingKey;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.accountingDate = accountingDate;
        this.description = description;
        this.createdByUserId = actorUserId;
        this.postedByUserId = actorUserId;
        this.reversalOfEntryId = reversalOfEntryId;
        this.postedAt = postedAt;
        this.status = JournalEntryStatus.POSTED;
    }

    public String getPostingKey() {
        return postingKey;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public LocalDate getAccountingDate() {
        return accountingDate;
    }

    public String getDescription() {
        return description;
    }

    public JournalEntryStatus getStatus() {
        return status;
    }

    public Long getReversalOfEntryId() {
        return reversalOfEntryId;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public Long getPostedByUserId() {
        return postedByUserId;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }
}
