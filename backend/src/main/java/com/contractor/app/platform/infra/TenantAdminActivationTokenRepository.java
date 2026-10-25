package com.contractor.app.platform.infra;

import com.contractor.app.platform.domain.TenantAdminActivationToken;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantAdminActivationTokenRepository extends JpaRepository<TenantAdminActivationToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TenantAdminActivationToken t where t.tokenHash = :hash")
    Optional<TenantAdminActivationToken> findByTokenHashForUpdate(@Param("hash") String hash);
}
