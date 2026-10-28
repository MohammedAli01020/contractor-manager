package com.contractor.app.accounting.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "journal_lines")
public class JournalLine extends BaseTenantEntity {

    @Column(name = "journal_entry_id", nullable = false, updatable = false)
    private Long journalEntryId;

    @Column(name = "line_number", nullable = false, updatable = false)
    private Integer lineNumber;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(length = 300, updatable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal debit;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal credit;

    @Column(name = "driver_id", updatable = false)
    private Long driverId;

    @Column(name = "equipment_id", updatable = false)
    private Long equipmentId;

    protected JournalLine() {
    }

    public JournalLine(Long tenantId, Long journalEntryId, int lineNumber, Long accountId,
                       String description, BigDecimal debit, BigDecimal credit,
                       Long driverId, Long equipmentId) {
        super(tenantId);
        this.journalEntryId = journalEntryId;
        this.lineNumber = lineNumber;
        this.accountId = accountId;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
        this.driverId = driverId;
        this.equipmentId = equipmentId;
    }

    public Long getJournalEntryId() {
        return journalEntryId;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public Long getDriverId() {
        return driverId;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }
}
