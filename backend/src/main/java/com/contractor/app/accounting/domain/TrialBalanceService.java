package com.contractor.app.accounting.domain;

import com.contractor.app.accounting.api.dto.TrialBalanceResponse;
import com.contractor.app.accounting.api.dto.TrialBalanceRowResponse;
import com.contractor.app.accounting.api.dto.TrialBalanceTotalsResponse;
import com.contractor.app.accounting.infra.TrialBalanceAccountRow;
import com.contractor.app.accounting.infra.TrialBalanceRepository;
import com.contractor.app.common.error.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrialBalanceService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final TrialBalanceRepository repository;

    public TrialBalanceService(TrialBalanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse getTrialBalance(Long tenantId, LocalDate fromDate, LocalDate toDate) {
        if (tenantId == null || tenantId <= 0) {
            throw ApiException.badRequest("معرّف الشركة مطلوب لعرض ميزان المراجعة.");
        }
        if (fromDate == null || toDate == null) {
            throw ApiException.badRequest("تاريخ بداية ونهاية الفترة مطلوبان.");
        }
        if (fromDate.isAfter(toDate)) {
            throw ApiException.badRequest("تاريخ بداية الفترة يجب ألا يتجاوز تاريخ نهايتها.");
        }

        List<TrialBalanceRowResponse> rows = new ArrayList<>();
        BigDecimal openingDebitTotal = ZERO;
        BigDecimal openingCreditTotal = ZERO;
        BigDecimal periodDebitTotal = ZERO;
        BigDecimal periodCreditTotal = ZERO;
        BigDecimal closingDebitTotal = ZERO;
        BigDecimal closingCreditTotal = ZERO;

        for (TrialBalanceAccountRow account : repository.findTrialBalance(tenantId, fromDate, toDate)) {
            BigDecimal openingNet = money(account.getOpeningDebit()).subtract(money(account.getOpeningCredit()));
            BigDecimal periodDebit = money(account.getPeriodDebit());
            BigDecimal periodCredit = money(account.getPeriodCredit());
            BigDecimal closingNet = openingNet.add(periodDebit).subtract(periodCredit);

            BigDecimal openingDebit = positive(openingNet);
            BigDecimal openingCredit = positive(openingNet.negate());
            BigDecimal closingDebit = positive(closingNet);
            BigDecimal closingCredit = positive(closingNet.negate());

            rows.add(new TrialBalanceRowResponse(account.getAccountId(), account.getAccountCode(),
                    account.getAccountName(), AccountType.valueOf(account.getAccountType()),
                    account.isActive(), openingDebit, openingCredit, periodDebit, periodCredit,
                    closingDebit, closingCredit));

            openingDebitTotal = openingDebitTotal.add(openingDebit);
            openingCreditTotal = openingCreditTotal.add(openingCredit);
            periodDebitTotal = periodDebitTotal.add(periodDebit);
            periodCreditTotal = periodCreditTotal.add(periodCredit);
            closingDebitTotal = closingDebitTotal.add(closingDebit);
            closingCreditTotal = closingCreditTotal.add(closingCredit);
        }

        TrialBalanceTotalsResponse totals = new TrialBalanceTotalsResponse(
                openingDebitTotal, openingCreditTotal, periodDebitTotal, periodCreditTotal,
                closingDebitTotal, closingCreditTotal);
        boolean balanced = closingDebitTotal.compareTo(closingCreditTotal) == 0;
        return new TrialBalanceResponse(fromDate, toDate, rows, totals, balanced);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal positive(BigDecimal value) {
        return value.compareTo(ZERO) > 0 ? value : ZERO;
    }
}
