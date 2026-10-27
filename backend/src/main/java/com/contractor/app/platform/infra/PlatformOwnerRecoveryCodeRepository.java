package com.contractor.app.platform.infra;

import com.contractor.app.platform.domain.PlatformOwnerRecoveryCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOwnerRecoveryCodeRepository extends JpaRepository<PlatformOwnerRecoveryCode, Long> {
    @Modifying
    @Query("update PlatformOwnerRecoveryCode c set c.usedAt = CURRENT_TIMESTAMP where c.ownerId = :ownerId and c.codeHash = :codeHash and c.usedAt is null")
    int consumeIfUnused(@Param("ownerId") Long ownerId, @Param("codeHash") String codeHash);

    @Modifying
    @Query("delete from PlatformOwnerRecoveryCode c where c.ownerId = :ownerId")
    int deleteAllForOwner(@Param("ownerId") Long ownerId);

    Optional<PlatformOwnerRecoveryCode> findByOwnerIdAndCodeHashAndUsedAtIsNull(Long ownerId, String codeHash);
}
