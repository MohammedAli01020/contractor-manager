package com.contractor.app.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;

/**
 * Encrypts TOTP seeds at rest using AES-256-GCM.
 * APP_PLATFORM_TOTP_ENCRYPTION_KEY must be a high-entropy, private environment secret.
 * Never log plaintext seeds or ciphertext values.
 */
@Service
public class PlatformTotpSecretCipher {
    private static final String PREFIX = "enc:v1:";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();

    public PlatformTotpSecretCipher(
            @Value("${app.platform.totp-encryption-key:}") String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("TOTP secret must not be empty");
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt TOTP secret", ex);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || !storedValue.startsWith(PREFIX)) {
            throw new IllegalStateException("TOTP secret is not stored in the supported encrypted format");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(storedValue.substring(PREFIX.length()));
            if (combined.length <= NONCE_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted TOTP secret");
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH);
            byte[] encrypted = new byte[combined.length - NONCE_LENGTH];
            System.arraycopy(combined, NONCE_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt TOTP secret; check the configured encryption key", ex);
        }
    }

    private SecretKeySpec key() throws GeneralSecurityException {
        if (configuredKey.length() < 32) {
            throw new IllegalStateException(
                    "APP_PLATFORM_TOTP_ENCRYPTION_KEY must contain at least 32 characters");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
