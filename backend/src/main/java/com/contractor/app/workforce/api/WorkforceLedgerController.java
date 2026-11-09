package com.contractor.app.workforce.api;

import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.workforce.api.dto.WorkforceLedgerEntryResponse;
import com.contractor.app.workforce.domain.WorkforceLedgerQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Canonical Workforce ledger read API; writes remain on the legacy-compatible path for now. */
@RestController
@RequestMapping("/api/v1/workforce/members/{memberId}/ledger")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
@Tag(name = "Workforce Ledger")
public class WorkforceLedgerController {
    private final WorkforceLedgerQueryService service;

    public WorkforceLedgerController(WorkforceLedgerQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkforceLedgerEntryResponse> statement(@PathVariable Long memberId) {
        return service.statement(CurrentUser.tenantId(), memberId);
    }
}
