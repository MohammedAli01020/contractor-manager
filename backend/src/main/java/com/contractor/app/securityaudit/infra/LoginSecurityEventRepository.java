package com.contractor.app.securityaudit.infra;

import com.contractor.app.securityaudit.domain.LoginPrincipalType;
import com.contractor.app.securityaudit.domain.LoginSecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSecurityEventRepository extends JpaRepository<LoginSecurityEvent, Long> {
    Page<LoginSecurityEvent> findByPrincipalTypeOrderByCreatedAtDescIdDesc(
            LoginPrincipalType principalType, Pageable pageable);
}
