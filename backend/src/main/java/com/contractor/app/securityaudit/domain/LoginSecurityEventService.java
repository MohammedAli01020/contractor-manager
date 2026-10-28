package com.contractor.app.securityaudit.domain;

import com.contractor.app.platform.domain.PlatformOwner;
import com.contractor.app.platform.infra.PlatformOwnerRepository;
import com.contractor.app.securityaudit.api.dto.LoginSecurityEventResponse;
import com.contractor.app.securityaudit.infra.LoginSecurityEventRepository;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginSecurityEventService {
    private final LoginSecurityEventRepository events;
    private final UserRepository users;
    private final PlatformOwnerRepository owners;

    public LoginSecurityEventService(LoginSecurityEventRepository events, UserRepository users,
                                     PlatformOwnerRepository owners) {
        this.events = events;
        this.users = users;
        this.owners = owners;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTenantSuccess(Long userId, Long tenantId, String username, String clientIp, String requestId) {
        record(LoginPrincipalType.TENANT_USER, userId, tenantId, username,
                LoginEventOutcome.SUCCESS, null, clientIp, requestId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPlatformSuccess(Long ownerId, String username, String clientIp, String requestId) {
        record(LoginPrincipalType.PLATFORM_OWNER, ownerId, null, username,
                LoginEventOutcome.SUCCESS, null, clientIp, requestId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPlatformChallenge(Long ownerId, String username, String clientIp, String requestId) {
        record(LoginPrincipalType.PLATFORM_OWNER, ownerId, null, username,
                LoginEventOutcome.MFA_CHALLENGE, "MFA_REQUIRED", clientIp, requestId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTenantFailure(String username, String reason, String clientIp, String requestId) {
        User user = users.findByUsername(normalize(username)).orElse(null);
        record(LoginPrincipalType.TENANT_USER, user == null ? null : user.getId(),
                user == null ? null : user.getTenantId(), username,
                LoginEventOutcome.FAILURE, reason, clientIp, requestId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPlatformFailure(String username, String reason, String clientIp, String requestId) {
        PlatformOwner owner = owners.findByUsername(normalize(username)).orElse(null);
        record(LoginPrincipalType.PLATFORM_OWNER, owner == null ? null : owner.getId(), null,
                username, LoginEventOutcome.FAILURE, reason, clientIp, requestId);
    }

    @Transactional(readOnly = true)
    public Page<LoginSecurityEventResponse> list(LoginPrincipalType type, Pageable pageable) {
        return events.findByPrincipalTypeOrderByCreatedAtDescIdDesc(type, pageable)
                .map(LoginSecurityEventResponse::new);
    }

    private void record(LoginPrincipalType type, Long principalId, Long tenantId, String username,
                        LoginEventOutcome outcome, String reason, String clientIp, String requestId) {
        events.save(new LoginSecurityEvent(type, principalId, tenantId, username,
                outcome, reason, clientIp, requestId));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
