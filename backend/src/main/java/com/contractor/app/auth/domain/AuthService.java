package com.contractor.app.auth.domain;

import com.contractor.app.audit.domain.AuditAction;
import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditService;
import com.contractor.app.auth.api.dto.AuthResponse;
import com.contractor.app.auth.api.dto.ChangePasswordRequest;
import com.contractor.app.auth.api.dto.LoginRequest;
import com.contractor.app.auth.api.dto.RegisterTenantRequest;
import com.contractor.app.auth.infra.RefreshTokenRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.security.JwtService;
import com.contractor.app.common.security.TokenHasher;
import com.contractor.app.common.util.Texts;
import com.contractor.app.tenant.domain.Tenant;
import com.contractor.app.tenant.domain.TenantStatus;
import com.contractor.app.tenant.infra.TenantRepository;
import com.contractor.app.user.api.dto.UserResponse;
import com.contractor.app.user.domain.Role;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import com.contractor.app.securityaudit.domain.LoginSecurityEventService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "اسم المستخدم أو كلمة المرور غير صحيحة.";
    private static final String INVALID_SESSION = "الجلسة غير صالحة. سجّل الدخول من جديد.";

    private final TenantRepository tenants;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final LoginSecurityEventService loginSecurityEvents;
    private final boolean registrationEnabled;
    private final long refreshTokenDays;
    /** يُستخدم عند غياب المستخدم كي يتساوى زمن الاستجابة ولا يمكن تخمين أسماء المستخدمين. */
    private final String dummyHash;

    public AuthService(TenantRepository tenants, UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder encoder, JwtService jwt, AuditService audit,
                       @Value("${app.registration.enabled:false}") boolean registrationEnabled,
                       @Value("${app.jwt.refresh-token-days:30}") long refreshTokenDays,
                       Environment environment, LoginSecurityEventService loginSecurityEvents) {
        this.tenants = tenants;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.loginSecurityEvents = loginSecurityEvents;
        // Direct tenant creation is only permitted in the isolated test profile.
        // An environment variable alone must never reopen public production registration.
        this.registrationEnabled = registrationEnabled && environment.acceptsProfiles(Profiles.of("test"));
        this.refreshTokenDays = refreshTokenDays;
        this.dummyHash = encoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public AuthResponse registerTenant(RegisterTenantRequest request) {
        if (!registrationEnabled) {
            throw ApiException.forbidden("تسجيل مقاولين جدد غير متاح حاليًا.");
        }
        String username = User.normalizeUsername(request.getUsername());
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("اسم المستخدم مستخدم بالفعل.");
        }
        Tenant tenant = tenants.save(new Tenant(request.getTenantName().trim()));
        User admin = users.save(new User(tenant.getId(), username, request.getFullName().trim(),
                Texts.blankToNull(request.getPhone()), encoder.encode(request.getPassword()), Role.ADMIN));
        return issueTokens(admin);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp, String requestId) {
        Optional<User> found = users.findByUsername(User.normalizeUsername(request.getUsername()));
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = encoder.matches(request.getPassword(), hash);
        if (!found.isPresent() || !passwordMatches) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        User user = found.get();
        Tenant tenant = tenants.findById(user.getTenantId())
                .orElseThrow(() -> ApiException.unauthorized(INVALID_CREDENTIALS));
        if (!user.isActive() || !user.isPasswordActivated() || tenant.getStatus() != TenantStatus.ACTIVE) {
            throw ApiException.forbidden("الحساب معطّل. تواصل مع مسؤول النظام.");
        }
        user.setLastLoginAt(LocalDateTime.now(ZoneOffset.UTC));
        loginSecurityEvents.recordTenantSuccess(user.getId(), user.getTenantId(), user.getUsername(), clientIp, requestId);
        return issueTokens(user);
    }

    /**
     * تجديد الجلسة مع تدوير الرمز: كل رمز يُستخدم مرة واحدة. إن ظهر رمز سبق استخدامه
     * نعتبره مسروقًا ونُنهي كل جلسات المستخدم. noRollbackFor ضروري كي تُحفظ هذه الإلغاءات
     * رغم رمي الاستثناء.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        RefreshToken token = refreshTokens.findByTokenHashForUpdate(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_SESSION));
        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUserId(), token.getTenantId());
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        User user = users.findByIdAndTenantId(token.getUserId(), token.getTenantId())
                .orElseThrow(() -> ApiException.unauthorized(INVALID_SESSION));
        boolean tenantActive = tenants.findById(user.getTenantId())
                .map(t -> t.getStatus() == TenantStatus.ACTIVE).orElse(false);
        if (!user.isActive() || !user.isPasswordActivated() || !tenantActive) {
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        token.setRevoked(true);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHashForUpdate(TokenHasher.sha256Hex(rawToken))
                .ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request) {
        User user = users.findByIdAndTenantId(principal.getUserId(), principal.getTenantId())
                .orElseThrow(() -> ApiException.unauthorized(INVALID_SESSION));
        if (!encoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("كلمة المرور الحالية غير صحيحة.");
        }
        user.setPasswordHash(encoder.encode(request.getNewPassword()));
        refreshTokens.revokeAllForUser(user.getId(), user.getTenantId());
        audit.record(user.getTenantId(), AuditEntityType.USER, user.getId(), AuditAction.PASSWORD_CHANGED,
                null, null, null);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwt.createAccessToken(user.getId(), user.getTenantId());
        String refreshRaw = TokenHasher.newOpaqueToken();
        refreshTokens.save(new RefreshToken(user.getTenantId(), user.getId(), TokenHasher.sha256Hex(refreshRaw),
                LocalDateTime.now(ZoneOffset.UTC).plusDays(refreshTokenDays)));
        return new AuthResponse(accessToken, refreshRaw, jwt.getAccessTtlSeconds(), UserResponse.from(user));
    }
}
