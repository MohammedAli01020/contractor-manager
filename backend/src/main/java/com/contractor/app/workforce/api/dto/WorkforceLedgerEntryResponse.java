package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.WorkforceLedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Workforce-keyed statement row; legacy Driver identifiers are intentionally not exposed. */
public class WorkforceLedgerEntryResponse {
    private final Long id;
    private final Long workforceMemberId;
    private final String transactionType;
    private final LocalDate transactionDate;
    private final BigDecimal amount;
    private final BigDecimal balanceEffect;
    private final String description;
    private final Long shiftId;
    private final Long journalEntryId;
    private final BigDecimal balanceAfter;

    public WorkforceLedgerEntryResponse(WorkforceLedgerEntry entry, BigDecimal balanceAfter) {
        this.id = entry.getId();
        this.workforceMemberId = entry.getWorkforceMemberId();
        this.transactionType = entry.getTransactionType();
        this.transactionDate = entry.getTransactionDate();
        this.amount = entry.getAmount();
        this.balanceEffect = entry.getBalanceEffect();
        this.description = entry.getDescription();
        this.shiftId = entry.getShiftId();
        this.journalEntryId = entry.getJournalEntryId();
        this.balanceAfter = balanceAfter;
    }

    public Long getId() { return id; }
    public Long getWorkforceMemberId() { return workforceMemberId; }
    public String getTransactionType() { return transactionType; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceEffect() { return balanceEffect; }
    public String getDescription() { return description; }
    public Long getShiftId() { return shiftId; }
    public Long getJournalEntryId() { return journalEntryId; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
}
