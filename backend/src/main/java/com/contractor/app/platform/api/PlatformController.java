package com.contractor.app.platform.api;

import com.contractor.app.common.security.LoginAttemptRateLimiter;
import com.contractor.app.common.web.RequestTrackingFilter;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.platform.api.dto.ActivateAdminRequest;
import com.contractor.app.platform.api.dto.CreateTenantRequest;
import com.contractor.app.platform.api.dto.CreateTenantResponse;
import com.contractor.app.platform.api.dto.PlatformLoginRequest;
import com.contractor.app.platform.api.dto.PlatformLoginResponse;
import com.contractor.app.platform.api.dto.PlatformMfaChallengeRequest;
import com.contractor.app.platform.api.dto.PlatformMfaCodeRequest;
import com.contractor.app.platform.api.dto.PlatformMfaSetupResponse;
import com.contractor.app.platform.api.dto.UpdateTenantStatusRequest;
import com.contractor.app.platform.api.dto.PlatformTenantSummary;
import com.contractor.app.platform.domain.PlatformAdminService;
import com.contractor.app.securityaudit.domain.LoginPrincipalType;
import com.contractor.app.securityaudit.domain.LoginSecurityEventService;
import com.contractor.app.securityaudit.api.dto.LoginSecurityEventResponse;
import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import java.util.function.Function;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {
    private final PlatformAdminService service;
    private final LoginAttemptRateLimiter loginRateLimiter;
    private final LoginSecurityEventService loginSecurityEvents;

    public PlatformController(PlatformAdminService service, LoginAttemptRateLimiter loginRateLimiter, LoginSecurityEventService loginSecurityEvents) {
        this.service = service;
        this.loginRateLimiter = loginRateLimiter;
        this.loginSecurityEvents = loginSecurityEvents;
    }

    @PostMapping("/auth/login")
    public PlatformLoginResponse login(@Valid @RequestBody PlatformLoginRequest request,
                                       HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();
        String requestId = (String) servletRequest.getAttribute(RequestTrackingFilter.REQUEST_ID_ATTRIBUTE);
        try {
            try (LoginAttemptRateLimiter.Attempt attempt =
                         loginRateLimiter.beginAttempt(clientIp, request.getUsername())) {
                try {
                    PlatformLoginResponse response = service.login(request);
                    attempt.success("platform_login");
                    loginSecurityEvents.recordPlatformChallenge(response.getOwnerId(), response.getUsername(), clientIp, requestId);
                    return response;
                } catch (ResponseStatusException ex) {
                    if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
                        attempt.failure("platform_login");
                        loginSecurityEvents.recordPlatformFailure(request.getUsername(), "INVALID_CREDENTIALS", clientIp, requestId);
                    }
                    throw ex;
                }
            }
        } catch (ResponseStatusException ex) {
            if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                loginSecurityEvents.recordPlatformFailure(request.getUsername(), "RATE_LIMITED", clientIp, requestId);
            }
            throw ex;
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                loginSecurityEvents.recordPlatformFailure(request.getUsername(), "RATE_LIMITED", clientIp, requestId);
            }
            throw ex;
        }
    }

    @PostMapping("/auth/mfa/setup")
    public PlatformMfaSetupResponse beginMfaSetup(@Valid @RequestBody PlatformMfaChallengeRequest request) {
        return service.beginTotpSetup(request);
    }

    @PostMapping("/auth/mfa/enable")
    public PlatformLoginResponse enableMfa(@Valid @RequestBody PlatformMfaCodeRequest request,
                                           HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();
        String requestId = servletRequest.getHeader("X-Request-ID");
        try {
            PlatformLoginResponse response = service.enableTotp(request);
            loginSecurityEvents.recordPlatformSuccess(response.getOwnerId(), response.getUsername(), clientIp, requestId);
            return response;
        } catch (ResponseStatusException ex) {
            if (ex.getStatus() == HttpStatus.UNAUTHORIZED || ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                loginSecurityEvents.recordPlatformMfaFailure(request.getChallengeToken(),
                        ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS ? "RATE_LIMITED" : "MFA_INVALID_OR_EXPIRED",
                        clientIp, requestId);
            }
            throw ex;
        }
    }

    @PostMapping("/auth/mfa/verify")
    public PlatformLoginResponse verifyMfa(@Valid @RequestBody PlatformMfaCodeRequest request,
                                           HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();
        String requestId = servletRequest.getHeader("X-Request-ID");
        try {
            PlatformLoginResponse response = service.verifyTotp(request);
            loginSecurityEvents.recordPlatformSuccess(response.getOwnerId(), response.getUsername(), clientIp, requestId);
            return response;
        } catch (ResponseStatusException ex) {
            if (ex.getStatus() == HttpStatus.UNAUTHORIZED || ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                loginSecurityEvents.recordPlatformMfaFailure(request.getChallengeToken(),
                        ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS ? "RATE_LIMITED" : "MFA_INVALID_OR_EXPIRED",
                        clientIp, requestId);
            }
            throw ex;
        }
    }

    @GetMapping("/tenants")
    public List<PlatformTenantSummary> listTenants() {
        return service.listTenants();
    }

    @GetMapping("/security/login-events")
    public PageResponse<LoginSecurityEventResponse> loginEvents(
            @RequestParam(defaultValue = "TENANT_USER") LoginPrincipalType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(loginSecurityEvents.list(type, PageParams.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))), Function.identity());
    }

    @PatchMapping("/tenants/{tenantId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTenantStatus(@PathVariable Long tenantId,
                                   @Valid @RequestBody UpdateTenantStatusRequest request) {
        service.updateTenantStatus(tenantId, request.getStatus());
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return service.createTenant(request);
    }

    @PostMapping("/admin-activation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateAdmin(@Valid @RequestBody ActivateAdminRequest request) {
        service.activateAdmin(request);
    }

    @PostMapping("/tenants/{tenantId}/admin-activation/resend")
    public CreateTenantResponse resendActivation(@PathVariable Long tenantId) {
        return service.resendActivation(tenantId);
    }
}
