package com.contractor.app.platform.infra;

import com.contractor.app.platform.domain.PlatformBootstrapGuard;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PlatformBootstrapGuardRepository extends JpaRepository<PlatformBootstrapGuard, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from PlatformBootstrapGuard g where g.id = 1")
    Optional<PlatformBootstrapGuard> lockSingleton();
}
