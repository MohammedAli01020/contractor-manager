package com.contractor.app.workforce.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Append-only Workforce-keyed financial movement.
 *
 * During the compatibility phase, rows may be mirrored from the legacy Driver ledger.
 * A future Workforce-native row has null legacy provenance. These fields must never
 * be used for operational identity, authorization, or balance grouping.
 */
@Entity
@Table(name = "workforce_ledger_entries")
public class WorkforceLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "workforce_member_id", nullable = false, updatable = false)
    private Long workforceMemberId;

    @Column(name = "transaction_type", nullable = false, length = 20, updatable = false)
    private String transactionType;

    @Column(name = "transaction_date", nullable = false, updatable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_effect", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal balanceEffect;

    @Column(nullable = false, length = 300, updatable = false)
    private String description;

    @Column(name = "client_request_key", nullable = false, length = 120, updatable = false)
    private String clientRequestKey;

    @Column(name = "shift_id", updatable = false)
    private Long shiftId;

    @Column(name = "journal_entry_id", nullable = false, updatable = false)
    private Long journalEntryId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "legacy_source_entry_id", updatable = false)
    private Long legacySourceEntryId;

    @Column(name = "legacy_driver_id", updatable = false)
    private Long legacyDriverId;

    protected WorkforceLedgerEntry() {
    }

    public WorkforceLedgerEntry(Long tenantId, Long workforceMemberId, String transactionType,
            LocalDate transactionDate, BigDecimal amount, BigDecimal balanceEffect, String description,
            String clientRequestKey, Long shiftId, Long journalEntryId, Long createdByUserId) {
        this.tenantId = tenantId;
        this.workforceMemberId = workforceMemberId;
        this.transactionType = transactionType;
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.balanceEffect = balanceEffect;
        this.description = description;
        this.clientRequestKey = clientRequestKey;
        this.shiftId = shiftId;
        this.journalEntryId = journalEntryId;
        this.createdByUserId = createdByUserId;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.legacySourceEntryId = null;
        this.legacyDriverId = null;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getWorkforceMemberId() { return workforceMemberId; }
    public String getTransactionType() { return transactionType; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceEffect() { return balanceEffect; }
    public String getDescription() { return description; }
    public String getClientRequestKey() { return clientRequestKey; }
    public Long getShiftId() { return shiftId; }
    public Long getJournalEntryId() { return journalEntryId; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getLegacySourceEntryId() { return legacySourceEntryId; }
    public Long getLegacyDriverId() { return legacyDriverId; }
}
