package com.contractor.app.platform.api;

import com.contractor.app.platform.api.dto.ActivateAdminRequest;
import com.contractor.app.platform.api.dto.CreateTenantRequest;
import com.contractor.app.platform.api.dto.CreateTenantResponse;
import com.contractor.app.platform.api.dto.PlatformLoginRequest;
import com.contractor.app.platform.api.dto.PlatformLoginResponse;
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
 @PostMapping("/tenants")
 @ResponseStatus(HttpStatus.CREATED)
 public CreateTenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request){return service.createTenant(request);}
 @PostMapping("/admin-activation")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 public void activateAdmin(@Valid @RequestBody ActivateAdminRequest request){service.activateAdmin(request);}
}
