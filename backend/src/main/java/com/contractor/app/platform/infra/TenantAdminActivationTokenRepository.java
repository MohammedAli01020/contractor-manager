package com.contractor.app.platform.infra;

import com.contractor.app.platform.domain.TenantAdminActivationToken;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantAdminActivationTokenRepository extends JpaRepository<TenantAdminActivationToken, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update TenantAdminActivationToken t set t.revoked = true where t.userId = :userId and t.tenantId = :tenantId and t.revoked = false and t.consumedAt is null")
    int revokeOutstandingForUser(@org.springframework.data.repository.query.Param("userId") Long userId,
                                 @org.springframework.data.repository.query.Param("tenantId") Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TenantAdminActivationToken t where t.tokenHash = :hash")
    Optional<TenantAdminActivationToken> findByTokenHashForUpdate(@Param("hash") String hash);
}
