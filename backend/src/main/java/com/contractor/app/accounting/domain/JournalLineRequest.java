package com.contractor.app.accounting.domain;

import java.math.BigDecimal;

public class JournalLineRequest {

    private Long accountId;
    private String description;
    private BigDecimal debit;
    private BigDecimal credit;
    private Long driverId;
    private Long equipmentId;

    public JournalLineRequest() {
    }

    public JournalLineRequest(Long accountId, String description, BigDecimal debit, BigDecimal credit,
                              Long driverId, Long equipmentId) {
        this.accountId = accountId;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
        this.driverId = driverId;
        this.equipmentId = equipmentId;
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
