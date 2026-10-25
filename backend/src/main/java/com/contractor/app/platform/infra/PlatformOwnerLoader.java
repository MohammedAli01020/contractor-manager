package com.contractor.app.platform.infra;

import com.contractor.app.platform.domain.PlatformOwner;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOwnerLoader {
    private final PlatformOwnerRepository owners;
    public PlatformOwnerLoader(PlatformOwnerRepository owners) { this.owners = owners; }
    @Transactional(readOnly = true)
    public Optional<PlatformOwner> load(Long id) {
        return owners.findById(id).filter(PlatformOwner::isActive);
    }
}
