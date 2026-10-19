package com.contractor.app.auth.infra;

import com.contractor.app.auth.domain.RefreshToken;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** قفل الصف يمنع استخدام نفس الرمز مرتين في وقت واحد. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true "
            + "where t.userId = :userId and t.tenantId = :tenantId and t.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId, @Param("tenantId") Long tenantId);
}
