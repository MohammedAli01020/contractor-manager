package com.contractor.app.workforce.api.dto;

import java.math.BigDecimal;

/** Separate payable and recoverable balances for one canonical Workforce member. */
public class WorkforceLedgerBalanceResponse {
    private final Long workforceMemberId;
    private final BigDecimal payableBalance;
    private final BigDecimal recoverableAdvanceBalance;

    public WorkforceLedgerBalanceResponse(Long workforceMemberId, BigDecimal payableBalance,
                                           BigDecimal recoverableAdvanceBalance) {
        this.workforceMemberId = workforceMemberId;
        this.payableBalance = payableBalance;
        this.recoverableAdvanceBalance = recoverableAdvanceBalance;
    }

    public Long getWorkforceMemberId() { return workforceMemberId; }
    public BigDecimal getPayableBalance() { return payableBalance; }
    public BigDecimal getRecoverableAdvanceBalance() { return recoverableAdvanceBalance; }
}
