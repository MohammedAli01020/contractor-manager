package com.contractor.app.auth.api;

import com.contractor.app.auth.api.dto.AuthResponse;
import com.contractor.app.auth.api.dto.ChangePasswordRequest;
import com.contractor.app.auth.api.dto.LoginRequest;
import com.contractor.app.auth.api.dto.RefreshTokenRequest;
import com.contractor.app.auth.api.dto.RegisterTenantRequest;
import com.contractor.app.auth.domain.AuthService;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.common.security.LoginAttemptRateLimiter;
import com.contractor.app.securityaudit.domain.LoginSecurityEventService;
import com.contractor.app.user.api.dto.UserResponse;
import com.contractor.app.user.domain.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final LoginAttemptRateLimiter loginRateLimiter;
    private final LoginSecurityEventService loginSecurityEvents;

    public AuthController(AuthService authService, UserService userService,
                          LoginAttemptRateLimiter loginRateLimiter, LoginSecurityEventService loginSecurityEvents) {
        this.authService = authService;
        this.userService = userService;
        this.loginRateLimiter = loginRateLimiter;
        this.loginSecurityEvents = loginSecurityEvents;
    }

    @PostMapping("/register-tenant")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "تسجيل مقاول جديد مع أول مستخدم (مدير)")
    public AuthResponse registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        return authService.registerTenant(request);
    }

    @PostMapping("/login")
    @Operation(summary = "تسجيل الدخول")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();
        String requestId = servletRequest.getHeader("X-Request-ID");
        try {
            try (LoginAttemptRateLimiter.Attempt attempt =
                         loginRateLimiter.beginAttempt(clientIp, request.getUsername())) {
                try {
                    AuthResponse response = authService.login(request, clientIp, requestId);
                    attempt.success("tenant_login");
                    return response;
                } catch (ApiException ex) {
                    if (ex.getStatus() == HttpStatus.UNAUTHORIZED || ex.getStatus() == HttpStatus.FORBIDDEN) {
                        attempt.failure("tenant_login");
                        loginSecurityEvents.recordTenantFailure(request.getUsername(),
                                ex.getStatus() == HttpStatus.FORBIDDEN ? "ACCOUNT_DISABLED_OR_PENDING" : "INVALID_CREDENTIALS",
                                clientIp, requestId);
                    }
                    throw ex;
                }
            }
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                loginSecurityEvents.recordTenantFailure(request.getUsername(), "RATE_LIMITED", clientIp, requestId);
            }
            throw ex;
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "تجديد الجلسة (الرمز القديم يُلغى ويصدر رمز جديد)")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "تسجيل الخروج وإلغاء رمز التجديد")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "بيانات المستخدم الحالي")
    public UserResponse me() {
        AuthenticatedUser me = CurrentUser.get();
        return userService.get(me.getTenantId(), me.getUserId());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "تغيير كلمة المرور (تُنهي كل الجلسات الأخرى)")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.get(), request);
    }
}
