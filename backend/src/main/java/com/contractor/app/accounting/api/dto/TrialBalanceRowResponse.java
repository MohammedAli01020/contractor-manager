package com.contractor.app.accounting.api.dto;

import com.contractor.app.accounting.domain.AccountType;
import java.math.BigDecimal;

public class TrialBalanceRowResponse {
    private final Long accountId;
    private final String accountCode;
    private final String accountName;
    private final AccountType accountType;
    private final boolean active;
    private final BigDecimal openingDebit;
    private final BigDecimal openingCredit;
    private final BigDecimal periodDebit;
    private final BigDecimal periodCredit;
    private final BigDecimal closingDebit;
    private final BigDecimal closingCredit;

    public TrialBalanceRowResponse(Long accountId, String accountCode, String accountName,
                                   AccountType accountType, boolean active,
                                   BigDecimal openingDebit, BigDecimal openingCredit,
                                   BigDecimal periodDebit, BigDecimal periodCredit,
                                   BigDecimal closingDebit, BigDecimal closingCredit) {
        this.accountId = accountId;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.accountType = accountType;
        this.active = active;
        this.openingDebit = openingDebit;
        this.openingCredit = openingCredit;
        this.periodDebit = periodDebit;
        this.periodCredit = periodCredit;
        this.closingDebit = closingDebit;
        this.closingCredit = closingCredit;
    }

    public Long getAccountId() { return accountId; }
    public String getAccountCode() { return accountCode; }
    public String getAccountName() { return accountName; }
    public AccountType getAccountType() { return accountType; }
    public boolean isActive() { return active; }
    public BigDecimal getOpeningDebit() { return openingDebit; }
    public BigDecimal getOpeningCredit() { return openingCredit; }
    public BigDecimal getPeriodDebit() { return periodDebit; }
    public BigDecimal getPeriodCredit() { return periodCredit; }
    public BigDecimal getClosingDebit() { return closingDebit; }
    public BigDecimal getClosingCredit() { return closingCredit; }
}
