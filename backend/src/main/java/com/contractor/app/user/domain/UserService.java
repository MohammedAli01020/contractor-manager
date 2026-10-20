package com.contractor.app.user.domain;

import com.contractor.app.audit.domain.AuditAction;
import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditService;
import com.contractor.app.auth.infra.RefreshTokenRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.util.Texts;
import com.contractor.app.user.api.dto.CreateUserRequest;
import com.contractor.app.user.api.dto.ResetPasswordRequest;
import com.contractor.app.user.api.dto.UpdateUserRequest;
import com.contractor.app.user.api.dto.UserResponse;
import com.contractor.app.user.infra.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String NOT_FOUND = "المستخدم غير موجود.";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       AuditService audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(Long tenantId) {
        return users.findByTenantIdOrderByFullNameAsc(tenantId).stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long tenantId, Long id) {
        return UserResponse.from(find(tenantId, id));
    }

    @Transactional
    public UserResponse create(Long tenantId, CreateUserRequest request) {
        String username = User.normalizeUsername(request.getUsername());
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("اسم المستخدم مستخدم بالفعل.");
        }
        User user = new User(tenantId, username, request.getFullName().trim(),
                Texts.blankToNull(request.getPhone()), encoder.encode(request.getPassword()), Role.ADMIN);
        users.save(user);
        audit.record(tenantId, AuditEntityType.USER, user.getId(), AuditAction.CREATED, null, null,
                "username=" + user.getUsername());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(Long tenantId, Long id, UpdateUserRequest request, Long currentUserId) {
        User user = find(tenantId, id);
        boolean active = request.getActive();
        if (!active && user.getId().equals(currentUserId)) {
            throw ApiException.badRequest("لا يمكنك تعطيل حسابك الخاص.");
        }
        String oldName = user.getFullName();
        String oldPhone = user.getPhone();
        boolean oldActive = user.isActive();

        user.setFullName(request.getFullName().trim());
        user.setPhone(Texts.blankToNull(request.getPhone()));
        user.setActive(active);
        if (!active) {
            // أي جلسة مفتوحة لهذا المستخدم تُنهى فورًا
            refreshTokens.revokeAllForUser(user.getId(), tenantId);
        }
        audit.recordChanges(tenantId, AuditEntityType.USER, user.getId(), new AuditService.ChangeSet()
                .track("fullName", oldName, user.getFullName())
                .track("phone", oldPhone, user.getPhone())
                .track("active", oldActive, user.isActive()));
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(Long tenantId, Long id, ResetPasswordRequest request) {
        User user = find(tenantId, id);
        user.setPasswordHash(encoder.encode(request.getNewPassword()));
        refreshTokens.revokeAllForUser(user.getId(), tenantId);
        audit.record(tenantId, AuditEntityType.USER, user.getId(), AuditAction.PASSWORD_RESET, null, null, null);
    }

    /** المصدر الوحيد لقراءة مستخدم: دائمًا ضمن مقاول بعينه، وغير الموجود يعطي 404 حتى لو كان لمقاول آخر. */
    private User find(Long tenantId, Long id) {
        return users.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }
}
