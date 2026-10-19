package com.contractor.app.auth.api;

import com.contractor.app.auth.api.dto.AuthResponse;
import com.contractor.app.auth.api.dto.ChangePasswordRequest;
import com.contractor.app.auth.api.dto.LoginRequest;
import com.contractor.app.auth.api.dto.RefreshTokenRequest;
import com.contractor.app.auth.api.dto.RegisterTenantRequest;
import com.contractor.app.auth.domain.AuthService;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.user.api.dto.UserResponse;
import com.contractor.app.user.domain.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register-tenant")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "تسجيل مقاول جديد مع أول مستخدم (مدير)")
    public AuthResponse registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        return authService.registerTenant(request);
    }

    @PostMapping("/login")
    @Operation(summary = "تسجيل الدخول")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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
