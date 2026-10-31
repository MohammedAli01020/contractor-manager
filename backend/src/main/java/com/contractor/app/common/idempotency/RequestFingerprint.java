package com.contractor.app.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Creates an unambiguous SHA-256 fingerprint for the business fields of an idempotent command. */
public final class RequestFingerprint {
    private RequestFingerprint() {
    }

    public static String of(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            if (value == null) {
                canonical.append("-1:");
            } else {
                String text = String.valueOf(value);
                canonical.append(text.length()).append(':').append(text);
            }
            canonical.append(';');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
