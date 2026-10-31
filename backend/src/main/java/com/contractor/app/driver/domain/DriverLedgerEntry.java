package com.contractor.app.driver.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.persistence.*;

@Entity
@Table(name = "driver_ledger_entries")
public class DriverLedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="tenant_id", nullable=false, updatable=false) private Long tenantId;
    @Column(name="driver_id", nullable=false, updatable=false) private Long driverId;
    @Column(name="transaction_type", nullable=false, length=20, updatable=false) private String transactionType;
    @Column(name="transaction_date", nullable=false, updatable=false) private LocalDate transactionDate;
    @Column(nullable=false, precision=15, scale=2, updatable=false) private BigDecimal amount;
    @Column(name="balance_effect", nullable=false, precision=15, scale=2, updatable=false) private BigDecimal balanceEffect;
    @Column(nullable=false, length=300, updatable=false) private String description;
    @Column(name="client_request_key", nullable=false, length=120, updatable=false) private String clientRequestKey;
    @Column(name="shift_id", updatable=false) private Long shiftId;
    @Column(name="journal_entry_id", nullable=false, updatable=false) private Long journalEntryId;
    @Column(name="created_by_user_id", nullable=false, updatable=false) private Long createdByUserId;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    protected DriverLedgerEntry() {}
    public DriverLedgerEntry(Long tenantId, Long driverId, String type, LocalDate date, BigDecimal amount,
            BigDecimal effect, String description, String key, Long shiftId, Long journalId, Long actorId) {
        this.tenantId=tenantId; this.driverId=driverId; this.transactionType=type; this.transactionDate=date;
        this.amount=amount; this.balanceEffect=effect; this.description=description; this.clientRequestKey=key;
        this.shiftId=shiftId; this.journalEntryId=journalId; this.createdByUserId=actorId;
        this.createdAt=LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
    public Long getId(){return id;} public Long getTenantId(){return tenantId;} public Long getDriverId(){return driverId;}
    public String getTransactionType(){return transactionType;} public LocalDate getTransactionDate(){return transactionDate;}
    public BigDecimal getAmount(){return amount;} public BigDecimal getBalanceEffect(){return balanceEffect;}
    public String getDescription(){return description;} public String getClientRequestKey(){return clientRequestKey;}
    public Long getShiftId(){return shiftId;} public Long getJournalEntryId(){return journalEntryId;}
    public Long getCreatedByUserId(){return createdByUserId;} public LocalDateTime getCreatedAt(){return createdAt;}
}