package com.contractor.app.accounting.api.dto;

import java.time.LocalDate;
import java.util.List;

public class TrialBalanceResponse {
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final List<TrialBalanceRowResponse> rows;
    private final TrialBalanceTotalsResponse totals;
    private final boolean balanced;

    public TrialBalanceResponse(LocalDate fromDate, LocalDate toDate,
                                List<TrialBalanceRowResponse> rows,
                                TrialBalanceTotalsResponse totals, boolean balanced) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.rows = rows;
        this.totals = totals;
        this.balanced = balanced;
    }

    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public List<TrialBalanceRowResponse> getRows() { return rows; }
    public TrialBalanceTotalsResponse getTotals() { return totals; }
    public boolean isBalanced() { return balanced; }
}
