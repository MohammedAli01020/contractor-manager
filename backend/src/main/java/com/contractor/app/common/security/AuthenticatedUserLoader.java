package com.contractor.app.common.security;

import com.contractor.app.tenant.domain.TenantStatus;
import com.contractor.app.tenant.infra.TenantRepository;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * يتحقق في كل طلب أن المستخدم لا يزال موجودًا وفعّالًا وأن مقاوله فعّال،
 * ويقرأ الدور من قاعدة البيانات لا من الرمز، فتسري أي تغييرات فورًا.
 */
@Service
public class AuthenticatedUserLoader {

    private final UserRepository users;
    private final TenantRepository tenants;

    public AuthenticatedUserLoader(UserRepository users, TenantRepository tenants) {
        this.users = users;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> load(Long userId, Long tenantId) {
        return users.findByIdAndTenantId(userId, tenantId)
                .filter(User::isActive)
                .filter(u -> tenants.findById(u.getTenantId())
                        .map(t -> t.getStatus() == TenantStatus.ACTIVE)
                        .orElse(false))
                .map(u -> new AuthenticatedUser(u.getId(), u.getTenantId(), u.getUsername(), u.getRole()));
    }
}
