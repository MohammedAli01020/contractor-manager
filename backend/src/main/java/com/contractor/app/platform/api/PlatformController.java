package com.contractor.app.platform.api;

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
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.contractor.app.platform.domain.PlatformAdminService;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {
 private final PlatformAdminService service;
 public PlatformController(PlatformAdminService service){this.service=service;}
 @PostMapping("/auth/login")
 public PlatformLoginResponse login(@Valid @RequestBody PlatformLoginRequest request){return service.login(request);}
 @PostMapping("/auth/mfa/setup")
 public PlatformMfaSetupResponse beginMfaSetup(@Valid @RequestBody PlatformMfaChallengeRequest request){return service.beginTotpSetup(request);}
 @PostMapping("/auth/mfa/enable")
 public PlatformLoginResponse enableMfa(@Valid @RequestBody PlatformMfaCodeRequest request){return service.enableTotp(request);}
 @PostMapping("/auth/mfa/verify")
 public PlatformLoginResponse verifyMfa(@Valid @RequestBody PlatformMfaCodeRequest request){return service.verifyTotp(request);}
 @GetMapping("/tenants")
 public List<PlatformTenantSummary> listTenants(){return service.listTenants();}
 @PatchMapping("/tenants/{tenantId}/status")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 public void updateTenantStatus(@PathVariable Long tenantId, @Valid @RequestBody UpdateTenantStatusRequest request) {
  service.updateTenantStatus(tenantId, request.getStatus());
 }
 @PostMapping("/tenants")
 @ResponseStatus(HttpStatus.CREATED)
 public CreateTenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request){return service.createTenant(request);}
 @PostMapping("/admin-activation")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 public void activateAdmin(@Valid @RequestBody ActivateAdminRequest request){service.activateAdmin(request);}
 @PostMapping("/tenants/{tenantId}/admin-activation/resend")
 public CreateTenantResponse resendActivation(@PathVariable Long tenantId){return service.resendActivation(tenantId);}
}
