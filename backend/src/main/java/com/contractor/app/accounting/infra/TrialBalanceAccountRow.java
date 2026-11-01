package com.contractor.app.accounting.infra;

import java.math.BigDecimal;

/** Read model for one account's aggregated trial-balance movements. */
public class TrialBalanceAccountRow {
    private final Long accountId;
    private final String accountCode;
    private final String accountName;
    private final String accountType;
    private final boolean active;
    private final BigDecimal openingDebit;
    private final BigDecimal openingCredit;
    private final BigDecimal periodDebit;
    private final BigDecimal periodCredit;

    public TrialBalanceAccountRow(Long accountId, String accountCode, String accountName,
                                  String accountType, boolean active, BigDecimal openingDebit,
                                  BigDecimal openingCredit, BigDecimal periodDebit,
                                  BigDecimal periodCredit) {
        this.accountId = accountId;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.accountType = accountType;
        this.active = active;
        this.openingDebit = openingDebit;
        this.openingCredit = openingCredit;
        this.periodDebit = periodDebit;
        this.periodCredit = periodCredit;
    }

    public Long getAccountId() { return accountId; }
    public String getAccountCode() { return accountCode; }
    public String getAccountName() { return accountName; }
    public String getAccountType() { return accountType; }
    public boolean isActive() { return active; }
    public BigDecimal getOpeningDebit() { return openingDebit; }
    public BigDecimal getOpeningCredit() { return openingCredit; }
    public BigDecimal getPeriodDebit() { return periodDebit; }
    public BigDecimal getPeriodCredit() { return periodCredit; }
}
