package com.contractor.app.platform.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contractor.app.platform.infra.PlatformBootstrapGuardRepository;
import com.contractor.app.platform.infra.PlatformOwnerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PlatformBootstrapServiceValidationTest {

    @Test
    void rejectsPasswordContainingOnlyWhitespaceBeforeEncodingOrSaving() {
        PlatformBootstrapGuardRepository guards = mock(PlatformBootstrapGuardRepository.class);
        PlatformOwnerRepository owners = mock(PlatformOwnerRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(guards.lockSingleton()).thenReturn(Optional.of(mock(PlatformBootstrapGuard.class)));
        when(owners.count()).thenReturn(0L);

        PlatformBootstrapService service = new PlatformBootstrapService(guards, owners, encoder);

        assertThrows(IllegalArgumentException.class,
                () -> service.createInitialOwner("owner_user", "Platform Owner", "            "));

        verify(encoder, never()).encode(anyString());
        verify(owners, never()).saveAndFlush(any(PlatformOwner.class));
    }
}
