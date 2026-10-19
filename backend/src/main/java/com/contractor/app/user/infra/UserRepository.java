package com.contractor.app.user.infra;

import com.contractor.app.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** للدخول فقط: اسم المستخدم فريد على مستوى النظام. */
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** كل الوصول للمستخدمين بعد الدخول يمرّ عبر tenantId. لا تضف find بدون tenantId. */
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    List<User> findByTenantIdOrderByFullNameAsc(Long tenantId);
}
