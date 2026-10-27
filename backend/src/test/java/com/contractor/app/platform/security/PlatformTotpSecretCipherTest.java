package com.contractor.app.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlatformTotpSecretCipherTest {
    @Test
    void encryptsAndDecryptsSecretWithoutStoringPlaintext() {
        PlatformTotpSecretCipher cipher =
                new PlatformTotpSecretCipher("test-only-private-encryption-key-123456789");
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

        String encrypted = cipher.encrypt(secret);

        assertNotEquals(secret, encrypted);
        assertEquals(secret, cipher.decrypt(encrypted));
        assertNotEquals(encrypted, cipher.encrypt(secret), "Each encryption should use a fresh nonce");
    }

    @Test
    void rejectsCiphertextTampering() {
        PlatformTotpSecretCipher cipher =
                new PlatformTotpSecretCipher("test-only-private-encryption-key-123456789");
        String encrypted = cipher.encrypt("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1)
                + (last == 'A' ? 'B' : 'A');

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void refusesToEncryptWithoutConfiguredKey() {
        PlatformTotpSecretCipher cipher = new PlatformTotpSecretCipher("");

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("secret"));
    }
}
