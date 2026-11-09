package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.driver.api.dto.DriverLedgerAdvanceRequest;
import com.contractor.app.driver.api.dto.DriverLedgerPaymentRequest;
import com.contractor.app.driver.domain.DriverLedgerService;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.workforce.api.dto.WorkforceLedgerEntryResponse;
import com.contractor.app.workforce.infra.WorkforceLedgerEntryRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workforce-facing compatibility adapter.
 *
 * The legacy Driver ledger remains the single write authority for now. The
 * tenant-scoped Driver projection is resolved internally and its INSERT trigger
 * mirrors the committed movement into the append-only Workforce ledger.
 */
@Service
public class WorkforceLedgerCommandService {
    private final DriverRepository drivers;
    private final DriverLedgerService legacyCommands;
    private final WorkforceLedgerEntryRepository workforceLedger;

    public WorkforceLedgerCommandService(DriverRepository drivers,
            DriverLedgerService legacyCommands, WorkforceLedgerEntryRepository workforceLedger) {
        this.drivers = drivers;
        this.legacyCommands = legacyCommands;
        this.workforceLedger = workforceLedger;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkforceLedgerEntryResponse pay(Long memberId, DriverLedgerPaymentRequest request) {
        Long tenantId = CurrentUser.tenantId();
        Long driverId = drivers.findByWorkforceMemberIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("لا يوجد ملف تشغيلي مالي مرتبط بهذا الفرد."))
                .getId();
        legacyCommands.pay(driverId, request);
        return responseFor(tenantId, memberId, request.getClientRequestKey());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkforceLedgerEntryResponse advance(Long memberId, DriverLedgerAdvanceRequest request) {
        Long tenantId = CurrentUser.tenantId();
        Long driverId = drivers.findByWorkforceMemberIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("لا يوجد ملف تشغيلي مالي مرتبط بهذا الفرد."))
                .getId();
        legacyCommands.advance(driverId, request);
        return responseFor(tenantId, memberId, request.getClientRequestKey());
    }

    private WorkforceLedgerEntryResponse responseFor(Long tenantId, Long memberId, String key) {
        WorkforceLedgerEntry entry = workforceLedger.findByTenantIdAndClientRequestKey(tenantId, key)
                .filter(row -> row.getWorkforceMemberId().equals(memberId))
                .orElseThrow(() -> ApiException.conflict("تم تسجيل الحركة دون ظهورها في سجل القوى العاملة؛ راجع المطابقة قبل إعادة المحاولة."));
        BigDecimal balanceAfter = workforceLedger.sumBalance(tenantId, memberId);
        return new WorkforceLedgerEntryResponse(entry, balanceAfter);
    }
}
