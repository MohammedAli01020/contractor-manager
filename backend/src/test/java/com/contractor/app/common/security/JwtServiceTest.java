package com.contractor.app.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    @Test
    void roundTripsUserAndTenant() {
        JwtService service = new JwtService(SECRET, "issuer-a", 15);

        String token = service.createAccessToken(7L, 42L);
        Optional<TokenClaims> claims = service.parse(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getUserId()).isEqualTo(7L);
        assertThat(claims.get().getTenantId()).isEqualTo(42L);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = new JwtService(SECRET, "issuer-a", 15);
        String token = service.createAccessToken(7L, 42L);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(service.parse(tampered)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = new JwtService(SECRET, "issuer-a", -1);

        assertThat(service.parse(service.createAccessToken(7L, 42L))).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService other = new JwtService("another-secret-another-secret-123456", "issuer-a", 15);
        JwtService service = new JwtService(SECRET, "issuer-a", 15);

        assertThat(service.parse(other.createAccessToken(7L, 42L))).isEmpty();
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtService other = new JwtService(SECRET, "issuer-b", 15);
        JwtService service = new JwtService(SECRET, "issuer-a", 15);

        assertThat(service.parse(other.createAccessToken(7L, 42L))).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        JwtService service = new JwtService(SECRET, "issuer-a", 15);

        assertThat(service.parse("not-a-jwt")).isEmpty();
        assertThat(service.parse("")).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", "issuer-a", 15))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hashesAreStableAndTokensAreRandom() {
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TokenHasher.newOpaqueToken()).isNotEqualTo(TokenHasher.newOpaqueToken());
    }
}
