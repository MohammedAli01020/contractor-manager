package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.workforce.api.dto.WorkforceLedgerEntryResponse;
import com.contractor.app.workforce.api.dto.WorkforceLedgerBalanceResponse;
import com.contractor.app.workforce.infra.WorkforceLedgerEntryRepository;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only Workforce ledger queries during the legacy-write compatibility phase. */
@Service
public class WorkforceLedgerQueryService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final WorkforceMemberRepository members;
    private final WorkforceLedgerEntryRepository ledger;

    public WorkforceLedgerQueryService(WorkforceMemberRepository members,
                                       WorkforceLedgerEntryRepository ledger) {
        this.members = members;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public WorkforceLedgerBalanceResponse balance(Long tenantId, Long memberId) {
        members.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
        return new WorkforceLedgerBalanceResponse(memberId,
                ledger.sumBalance(tenantId, memberId),
                ledger.sumRecoverableAdvanceBalance(tenantId, memberId));
    }

    @Transactional(readOnly = true)
    public List<WorkforceLedgerEntryResponse> statement(Long tenantId, Long memberId) {
        members.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));

        BigDecimal runningBalance = ZERO;
        List<WorkforceLedgerEntryResponse> statement = new ArrayList<>();
        for (WorkforceLedgerEntry entry :
                ledger.findByTenantIdAndWorkforceMemberIdOrderByTransactionDateAscIdAsc(tenantId, memberId)) {
            runningBalance = runningBalance.add(entry.getBalanceEffect());
            statement.add(new WorkforceLedgerEntryResponse(entry, runningBalance));
        }
        return statement;
    }
}
