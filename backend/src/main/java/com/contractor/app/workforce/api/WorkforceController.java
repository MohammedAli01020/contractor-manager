package com.contractor.app.workforce.api;

import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.user.domain.Role;
import com.contractor.app.workforce.api.dto.EndWorkforceAssignmentRequest;
import com.contractor.app.workforce.api.dto.WorkforceAssignmentRequest;
import com.contractor.app.workforce.api.dto.WorkforceAssignmentResponse;
import com.contractor.app.workforce.api.dto.WorkforceMemberDirectoryResponse;
import com.contractor.app.workforce.api.dto.WorkforceMemberRequest;
import com.contractor.app.workforce.api.dto.WorkforceMemberResponse;
import com.contractor.app.workforce.api.dto.WorkforceOnboardRequest;
import com.contractor.app.workforce.api.dto.WorkforceOnboardResponse;
import com.contractor.app.workforce.api.dto.WorkforceRoleRequest;
import com.contractor.app.workforce.api.dto.WorkforceRoleResponse;
import com.contractor.app.workforce.domain.WorkforceAssignmentService;
import com.contractor.app.workforce.domain.WorkforceIdentityService;
import com.contractor.app.workforce.domain.WorkforceOnboardingService;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import com.contractor.app.workforce.infra.WorkforceMemberDirectoryRow;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/workforce")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
@Tag(name = "Workforce")
public class WorkforceController {

    private final WorkforceAssignmentService service;
    private final WorkforceIdentityService identities;
    private final WorkforceMemberRepository members;
    private final WorkforceOnboardingService onboarding;

    public WorkforceController(WorkforceAssignmentService service, WorkforceIdentityService identities,
                                WorkforceMemberRepository members, WorkforceOnboardingService onboarding) {
        this.service = service;
        this.identities = identities;
        this.members = members;
        this.onboarding = onboarding;
    }

    @GetMapping("/members")
    public Page<WorkforceMemberDirectoryResponse> members(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @PageableDefault(size = 20) Pageable pageable) {
        String normalizedSearch = search == null || search.trim().isEmpty() ? null : search.trim();
        Page<WorkforceMemberDirectoryRow> page = members.searchDirectory(
                CurrentUser.tenantId(), includeInactive, normalizedSearch, pageable);
        java.util.List<Long> memberIds = page.getContent().stream()
                .map(WorkforceMemberDirectoryRow::getWorkforceMemberId).collect(Collectors.toList());
        Map<Long, java.util.List<com.contractor.app.workforce.api.dto.WorkforceRoleSummaryResponse>> summaries =
                service.activeRoleSummaries(CurrentUser.tenantId(), memberIds);
        return page.map(row -> WorkforceMemberDirectoryResponse.from(
                row, summaries.getOrDefault(row.getWorkforceMemberId(), Collections.emptyList())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkforceMemberResponse createMember(@Valid @RequestBody WorkforceMemberRequest request) {
        return WorkforceMemberResponse.from(
                identities.createMember(CurrentUser.tenantId(), request), true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/members/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkforceOnboardResponse onboardMember(@Valid @RequestBody WorkforceOnboardRequest request) {
        return onboarding.onboard(CurrentUser.tenantId(), request);
    }

    @GetMapping("/members/{memberId}")
    public WorkforceMemberResponse member(@PathVariable Long memberId) {
        return WorkforceMemberResponse.from(identities.requireMember(CurrentUser.tenantId(), memberId),
                CurrentUser.get().getRole() == Role.ADMIN);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/members/{memberId}")
    public WorkforceMemberResponse updateMember(@PathVariable Long memberId,
                                                  @Valid @RequestBody WorkforceMemberRequest request) {
        return WorkforceMemberResponse.from(
                identities.updateMember(CurrentUser.tenantId(), memberId, request), true);
    }

    @GetMapping("/roles")
    public List<WorkforceRoleResponse> roles(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listRoles(CurrentUser.tenantId(), includeInactive);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkforceRoleResponse createRole(@Valid @RequestBody WorkforceRoleRequest request) {
        return service.createRole(CurrentUser.tenantId(), request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateRole(@PathVariable Long roleId) {
        service.deactivateRole(CurrentUser.tenantId(), roleId);
    }

    @GetMapping("/members/{memberId}/assignments")
    public List<WorkforceAssignmentResponse> assignments(@PathVariable Long memberId) {
        return service.listAssignments(CurrentUser.tenantId(), memberId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/members/{memberId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkforceAssignmentResponse createAssignment(
            @PathVariable Long memberId,
            @Valid @RequestBody WorkforceAssignmentRequest request) {
        return service.createAssignment(CurrentUser.tenantId(), memberId, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/members/{memberId}/assignments/{assignmentId}/end")
    public WorkforceAssignmentResponse endAssignment(
            @PathVariable Long memberId,
            @PathVariable Long assignmentId,
            @Valid @RequestBody EndWorkforceAssignmentRequest request) {
        return service.endAssignment(CurrentUser.tenantId(), memberId, assignmentId, request);
    }
}
