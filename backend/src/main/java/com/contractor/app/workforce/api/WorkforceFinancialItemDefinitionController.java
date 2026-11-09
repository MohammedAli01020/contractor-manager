package com.contractor.app.workforce.api;

import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.workforce.api.dto.UpdateWorkforceFinancialItemDefinitionRequest;
import com.contractor.app.workforce.api.dto.WorkforceFinancialItemDefinitionRequest;
import com.contractor.app.workforce.api.dto.WorkforceFinancialItemDefinitionResponse;
import com.contractor.app.workforce.domain.WorkforceFinancialItemDefinitionService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workforce/financial-item-definitions")
@PreAuthorize("hasAnyRole('ADMIN','VIEWER')")
public class WorkforceFinancialItemDefinitionController {

    private final WorkforceFinancialItemDefinitionService service;

    public WorkforceFinancialItemDefinitionController(WorkforceFinancialItemDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkforceFinancialItemDefinitionResponse> list(
            @RequestParam(required = false) Boolean active) {
        return service.list(CurrentUser.tenantId(), active);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkforceFinancialItemDefinitionResponse create(
            @Valid @RequestBody WorkforceFinancialItemDefinitionRequest request) {
        return service.create(CurrentUser.tenantId(), request);
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkforceFinancialItemDefinitionResponse update(@PathVariable Long itemId,
            @Valid @RequestBody UpdateWorkforceFinancialItemDefinitionRequest request) {
        return service.update(CurrentUser.tenantId(), itemId, request);
    }

    @PostMapping("/{itemId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkforceFinancialItemDefinitionResponse deactivate(@PathVariable Long itemId,
            @RequestParam Long version) {
        return service.deactivate(CurrentUser.tenantId(), itemId, version);
    }
}
