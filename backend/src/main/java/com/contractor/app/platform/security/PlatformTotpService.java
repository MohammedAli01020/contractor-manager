package com.contractor.app.platform.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * RFC 6238 TOTP primitives for Platform Owner MFA.
 * Secrets are generated from SecureRandom; callers must never log them.
 */
@Service
public class PlatformTotpService {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final long STEP_SECONDS = 30L;
    private static final int CODE_DIGITS = 6;
    private static final int ALLOWED_WINDOW_STEPS = 1;
    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String provisioningUri(String issuer, String accountName, String secret) {
        String label = urlEncode(issuer) + ":" + urlEncode(accountName);
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer) + "&algorithm=SHA1&digits=6&period=30";
    }

    public boolean verifyCode(String secret, String suppliedCode, long epochSeconds) {
        if (secret == null || suppliedCode == null || !suppliedCode.matches("\\d{6}")) {
            return false;
        }
        byte[] expected = new byte[CODE_DIGITS];
        byte[] supplied = suppliedCode.getBytes(StandardCharsets.US_ASCII);
        for (int offset = -ALLOWED_WINDOW_STEPS; offset <= ALLOWED_WINDOW_STEPS; offset++) {
            String candidate;
            try {
                candidate = codeAt(secret, Math.floorDiv(epochSeconds, STEP_SECONDS) + offset);
            } catch (RuntimeException ex) {
                return false;
            }
            if (MessageDigest.isEqual(candidate.getBytes(StandardCharsets.US_ASCII), supplied)) {
                return true;
            }
        }
        return false;
    }

    String codeAt(String secret, long counter) {
        try {
            byte[] key = base32Decode(secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] message = new byte[8];
            for (int i = 7; i >= 0; i--) {
                message[i] = (byte) (counter & 0xff);
                counter >>>= 8;
            }
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to generate TOTP code", ex);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) out.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        return out.toString();
    }

    static byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] output = new byte[(normalized.length() * 5) / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int digit = BASE32.indexOf(normalized.charAt(i));
            if (digit < 0) throw new IllegalArgumentException("Invalid Base32 secret");
            buffer = (buffer << 5) | digit;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        if (index != output.length) throw new IllegalArgumentException("Invalid Base32 secret");
        return output;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
