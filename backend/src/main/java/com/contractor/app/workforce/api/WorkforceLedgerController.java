package com.contractor.app.workforce.api;

import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.workforce.api.dto.WorkforceLedgerEntryResponse;
import com.contractor.app.workforce.api.dto.WorkforceLedgerBalanceResponse;
import com.contractor.app.driver.api.dto.DriverLedgerAdvanceRequest;
import com.contractor.app.driver.api.dto.DriverLedgerPaymentRequest;
import com.contractor.app.workforce.domain.WorkforceLedgerCommandService;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import com.contractor.app.workforce.domain.WorkforceLedgerQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final WorkforceLedgerCommandService commands;

    public WorkforceLedgerController(WorkforceLedgerQueryService service,
                                     WorkforceLedgerCommandService commands) {
        this.service = service;
        this.commands = commands;
    }

    @GetMapping
    public List<WorkforceLedgerEntryResponse> statement(@PathVariable Long memberId) {
        return service.statement(CurrentUser.tenantId(), memberId);
    }

    @GetMapping("/balance")
    public WorkforceLedgerBalanceResponse balance(@PathVariable Long memberId) {
        return service.balance(CurrentUser.tenantId(), memberId);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public WorkforceLedgerEntryResponse pay(@PathVariable Long memberId,
            @Valid @RequestBody DriverLedgerPaymentRequest request) {
        return commands.pay(memberId, request);
    }

    @PostMapping("/advances")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public WorkforceLedgerEntryResponse advance(@PathVariable Long memberId,
            @Valid @RequestBody DriverLedgerAdvanceRequest request) {
        return commands.advance(memberId, request);
    }
}
