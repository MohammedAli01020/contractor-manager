package com.contractor.app.common.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String TENANT_CLAIM = "tid";
    private static final String TYPE_CLAIM = "typ";
    private static final String OWNER_TYPE = "platform_owner";
    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSeconds;
    private final JwtParser parser;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.issuer:contractor-manager}") String issuer,
                      @Value("${app.jwt.access-token-minutes:15}") long accessTokenMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("APP_JWT_SECRET must be at least 32 characters long");
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = issuer;
        this.accessTtlSeconds = accessTokenMinutes * 60;
        this.parser = Jwts.parserBuilder().requireIssuer(issuer).setSigningKey(key).build();
    }

    public String createAccessToken(Long userId, Long tenantId) {
        Instant now = Instant.now();
        return Jwts.builder().setIssuer(issuer).setSubject(String.valueOf(userId))
                .claim(TENANT_CLAIM, tenantId).setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public String createPlatformOwnerAccessToken(Long ownerId) {
        Instant now = Instant.now();
        return Jwts.builder().setIssuer(issuer).setSubject(String.valueOf(ownerId))
                .claim(TYPE_CLAIM, OWNER_TYPE).setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public Optional<TokenClaims> parse(String token) {
        try {
            io.jsonwebtoken.Claims claims = parser.parseClaimsJws(token).getBody();
            Long id = Long.parseLong(claims.getSubject());
            if (OWNER_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
                if (claims.containsKey(TENANT_CLAIM)) return Optional.empty();
                return Optional.of(new TokenClaims(id, null, true));
            }
            Object tenant = claims.get(TENANT_CLAIM);
            if (!(tenant instanceof Number) || claims.containsKey(TYPE_CLAIM)) return Optional.empty();
            return Optional.of(new TokenClaims(id, ((Number) tenant).longValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }
}
