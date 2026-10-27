package com.contractor.app.platform.infra;

import com.contractor.app.platform.domain.PlatformOwner;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PlatformOwnerRepository extends JpaRepository<PlatformOwner, Long> {
    Optional<PlatformOwner> findByUsername(String username);
    boolean existsByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlatformOwner p where p.id = :id")
    Optional<PlatformOwner> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlatformOwner p order by p.id")
    java.util.List<PlatformOwner> findAllForUpdate();
}
