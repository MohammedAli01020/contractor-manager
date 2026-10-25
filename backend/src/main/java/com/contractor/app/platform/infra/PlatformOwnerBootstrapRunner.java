package com.contractor.app.platform.infra;

import com.contractor.app.platform.domain.PlatformBootstrapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.platform.bootstrap.enabled", havingValue = "true")
public class PlatformOwnerBootstrapRunner implements ApplicationRunner {
    private final PlatformBootstrapService bootstrap;
    private final String username;
    private final String fullName;
    private final String password;
    public PlatformOwnerBootstrapRunner(PlatformBootstrapService bootstrap,
            @Value("${PLATFORM_OWNER_USERNAME:}") String username,
            @Value("${PLATFORM_OWNER_FULL_NAME:}") String fullName,
            @Value("${PLATFORM_OWNER_PASSWORD:}") String password) {
        this.bootstrap=bootstrap; this.username=username; this.fullName=fullName; this.password=password;
    }
    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || fullName.isBlank() || password.isBlank())
            throw new IllegalStateException("Set PLATFORM_OWNER_USERNAME, PLATFORM_OWNER_FULL_NAME and PLATFORM_OWNER_PASSWORD for one-time bootstrap");
        bootstrap.createInitialOwner(username, fullName, password);
    }
}
