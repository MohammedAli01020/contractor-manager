package com.contractor.app.workforce.api;

import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.workforce.api.dto.CompensationAgreementRequest;
import com.contractor.app.workforce.api.dto.CompensationAgreementResponse;
import com.contractor.app.workforce.domain.CompensationAgreementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workforce/members/{memberId}/compensation-agreements")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
@Tag(name = "Compensation Agreements")
public class CompensationAgreementController {

    private final CompensationAgreementService service;

    public CompensationAgreementController(CompensationAgreementService service) {
        this.service = service;
    }

    @GetMapping
    public List<CompensationAgreementResponse> list(@PathVariable Long memberId) {
        return service.list(CurrentUser.tenantId(), memberId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompensationAgreementResponse createDraft(@PathVariable Long memberId,
            @Valid @RequestBody CompensationAgreementRequest request) {
        return service.createDraft(CurrentUser.tenantId(), memberId,
                CurrentUser.get().getUserId(), request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{agreementId}/approve")
    public CompensationAgreementResponse approve(@PathVariable Long memberId,
                                                   @PathVariable Long agreementId) {
        return service.approve(CurrentUser.tenantId(), memberId, agreementId,
                CurrentUser.get().getUserId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{agreementId}/approve-replacement")
    public CompensationAgreementResponse approveReplacement(@PathVariable Long memberId,
            @PathVariable Long agreementId) {
        return service.approveReplacement(CurrentUser.tenantId(), memberId, agreementId,
                CurrentUser.get().getUserId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{agreementId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelDraft(@PathVariable Long memberId, @PathVariable Long agreementId) {
        service.cancelDraft(CurrentUser.tenantId(), memberId, agreementId,
                CurrentUser.get().getUserId());
    }
}
