package com.contractor.app.accounting.api;

import com.contractor.app.accounting.api.dto.TrialBalanceResponse;
import com.contractor.app.accounting.domain.TrialBalanceService;
import com.contractor.app.common.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounting/reports")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
public class TrialBalanceController {

    private final TrialBalanceService service;

    public TrialBalanceController(TrialBalanceService service) {
        this.service = service;
    }

    @GetMapping("/trial-balance")
    public TrialBalanceResponse trialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getTrialBalance(CurrentUser.tenantId(), from, to);
    }
}
