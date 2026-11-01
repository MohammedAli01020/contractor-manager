package com.contractor.app.accounting.api.dto;

import java.math.BigDecimal;

public class TrialBalanceTotalsResponse {
    private final BigDecimal openingDebit;
    private final BigDecimal openingCredit;
    private final BigDecimal periodDebit;
    private final BigDecimal periodCredit;
    private final BigDecimal closingDebit;
    private final BigDecimal closingCredit;

    public TrialBalanceTotalsResponse(BigDecimal openingDebit, BigDecimal openingCredit,
                                      BigDecimal periodDebit, BigDecimal periodCredit,
                                      BigDecimal closingDebit, BigDecimal closingCredit) {
        this.openingDebit = openingDebit;
        this.openingCredit = openingCredit;
        this.periodDebit = periodDebit;
        this.periodCredit = periodCredit;
        this.closingDebit = closingDebit;
        this.closingCredit = closingCredit;
    }

    public BigDecimal getOpeningDebit() { return openingDebit; }
    public BigDecimal getOpeningCredit() { return openingCredit; }
    public BigDecimal getPeriodDebit() { return periodDebit; }
    public BigDecimal getPeriodCredit() { return periodCredit; }
    public BigDecimal getClosingDebit() { return closingDebit; }
    public BigDecimal getClosingCredit() { return closingCredit; }
}
