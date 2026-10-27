package com.contractor.app.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformTotpServiceTest {
    private final PlatformTotpService service = new PlatformTotpService();

    @Test
    void matchesRfc6238SixDigitVector() {
        assertEquals("287082", service.codeAt(
                "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1L));
    }

    @Test
    void acceptsCurrentCodeAndRejectsMalformedOrWrongCodes() {
        String secret = service.generateSecret();
        long timestamp = 1_700_000_000L;
        String current = service.codeAt(secret, timestamp / 30L);

        assertTrue(service.verifyCode(secret, current, timestamp));
        assertFalse(service.verifyCode(secret, "12A456", timestamp));
        assertFalse(service.verifyCode(secret, "12345", timestamp));
        assertFalse(service.verifyCode(secret, "000000", timestamp)
                && !"000000".equals(current));
    }

    @Test
    void generatedSecretIsSuitableForAuthenticatorProvisioning() {
        String secret = service.generateSecret();
        String uri = service.provisioningUri("Contractor Manager", "owner@example.com", secret);

        assertEquals(32, secret.length());
        assertTrue(secret.matches("[A-Z2-7]+"));
        assertNotNull(uri);
        assertTrue(uri.startsWith("otpauth://totp/Contractor%20Manager:owner%40example.com?"));
        assertTrue(uri.contains("issuer=Contractor%20Manager"));
        assertTrue(uri.contains("period=30"));
    }
}
