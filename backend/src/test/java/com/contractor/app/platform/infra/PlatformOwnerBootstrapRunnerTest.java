package com.contractor.app.platform.infra;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.contractor.app.platform.domain.PlatformBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class PlatformOwnerBootstrapRunnerTest {

    @Test
    void rejectsWhitespaceOnlyBootstrapCredentialsBeforeCallingService() {
        PlatformBootstrapService bootstrap = mock(PlatformBootstrapService.class);
        PlatformOwnerBootstrapRunner runner = new PlatformOwnerBootstrapRunner(
                bootstrap, "owner_user", "Platform Owner", "                ");

        assertThrows(IllegalStateException.class,
                () -> runner.run(mock(ApplicationArguments.class)));

        verify(bootstrap, never()).createInitialOwner("owner_user", "Platform Owner", "                ");
    }

    @Test
    void rejectsWhitespaceOnlyUsernameBeforeCallingService() {
        PlatformBootstrapService bootstrap = mock(PlatformBootstrapService.class);
        PlatformOwnerBootstrapRunner runner = new PlatformOwnerBootstrapRunner(
                bootstrap, "   ", "Platform Owner", "ValidPassword123");

        assertThrows(IllegalStateException.class,
                () -> runner.run(mock(ApplicationArguments.class)));

        verify(bootstrap, never()).createInitialOwner(
                "   ", "Platform Owner", "ValidPassword123");
    }

    @Test
    void rejectsWhitespaceOnlyFullNameBeforeCallingService() {
        PlatformBootstrapService bootstrap = mock(PlatformBootstrapService.class);
        PlatformOwnerBootstrapRunner runner = new PlatformOwnerBootstrapRunner(
                bootstrap, "owner_user", "   ", "ValidPassword123");

        assertThrows(IllegalStateException.class,
                () -> runner.run(mock(ApplicationArguments.class)));

        verify(bootstrap, never()).createInitialOwner(
                "owner_user", "   ", "ValidPassword123");
    }
}
