package com.contractor.app.platform.domain;

import com.contractor.app.platform.infra.PlatformBootstrapGuardRepository;
import com.contractor.app.platform.infra.PlatformOwnerRepository;
import com.contractor.app.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformBootstrapService {
    private final PlatformBootstrapGuardRepository guards;
    private final PlatformOwnerRepository owners;
    private final PasswordEncoder encoder;
    public PlatformBootstrapService(PlatformBootstrapGuardRepository guards, PlatformOwnerRepository owners, PasswordEncoder encoder) {
        this.guards=guards; this.owners=owners; this.encoder=encoder;
    }
    @Transactional
    public void createInitialOwner(String username, String fullName, String password) {
        guards.lockSingleton().orElseThrow(() -> new IllegalStateException("Platform bootstrap guard row is missing; verify Flyway V11"));
        if (owners.count() != 0) throw new IllegalStateException("Platform owner already exists; bootstrap is single-use");
        String normalized = User.normalizeUsername(username);
        if (!normalized.matches("^[a-z0-9._@-]{3,60}$")) throw new IllegalArgumentException("Invalid platform owner username");
        if (fullName == null || fullName.trim().isEmpty() || fullName.trim().length() > 150) throw new IllegalArgumentException("Invalid platform owner full name");
        if (password == null || password.isBlank() || password.length() < 12 || password.length() > 100) throw new IllegalArgumentException("Bootstrap password must be 12-100 non-blank characters");
        owners.saveAndFlush(new PlatformOwner(normalized, fullName.trim(), encoder.encode(password)));
    }
}
