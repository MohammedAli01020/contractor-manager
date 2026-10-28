package com.contractor.app.platform.api;

import com.contractor.app.common.security.LoginAttemptRateLimiter;
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

    public PlatformController(PlatformAdminService service, LoginAttemptRateLimiter loginRateLimiter) {
        this.service = service;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/auth/login")
    public PlatformLoginResponse login(@Valid @RequestBody PlatformLoginRequest request,
                                       HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();
        synchronized (loginRateLimiter.identityLock(clientIp, request.getUsername())) {
            loginRateLimiter.checkAllowed(clientIp, request.getUsername());
            try {
                PlatformLoginResponse response = service.login(request);
                loginRateLimiter.recordSuccess(clientIp, request.getUsername(), "platform_login");
                return response;
            } catch (ResponseStatusException ex) {
                if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
                    loginRateLimiter.recordFailure(clientIp, request.getUsername(), "platform_login");
                }
                throw ex;
            }
        }
    }

    @PostMapping("/auth/mfa/setup")
    public PlatformMfaSetupResponse beginMfaSetup(@Valid @RequestBody PlatformMfaChallengeRequest request) {
        return service.beginTotpSetup(request);
    }

    @PostMapping("/auth/mfa/enable")
    public PlatformLoginResponse enableMfa(@Valid @RequestBody PlatformMfaCodeRequest request) {
        return service.enableTotp(request);
    }

    @PostMapping("/auth/mfa/verify")
    public PlatformLoginResponse verifyMfa(@Valid @RequestBody PlatformMfaCodeRequest request) {
        return service.verifyTotp(request);
    }

    @GetMapping("/tenants")
    public List<PlatformTenantSummary> listTenants() {
        return service.listTenants();
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
