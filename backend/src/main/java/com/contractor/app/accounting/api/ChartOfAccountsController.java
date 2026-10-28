package com.contractor.app.accounting.api;

import com.contractor.app.accounting.api.dto.ChartAccountResponse;
import com.contractor.app.accounting.domain.ChartOfAccountsQueryService;
import com.contractor.app.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounting/chart-of-accounts")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
@Tag(name = "Accounting")
public class ChartOfAccountsController {

    private final ChartOfAccountsQueryService service;

    public ChartOfAccountsController(ChartOfAccountsQueryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "دليل حسابات الشركة الحالية مرتّبًا بكود الحساب")
    public List<ChartAccountResponse> list() {
        return service.listForTenant(CurrentUser.tenantId()).stream()
                .map(ChartAccountResponse::new)
                .collect(Collectors.toList());
    }
}
