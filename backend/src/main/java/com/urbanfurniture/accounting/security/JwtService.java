package com.urbanfurniture.accounting.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class JwtService {

    private final String configuredSecret;
    private final long expirationMs;
    private final String issuer;
    private SecretKey key;

    public JwtService(@Value("${app.jwt.secret:}") String configuredSecret,
                      @Value("${app.jwt.expiration-ms:43200000}") long expirationMs,
                      @Value("${app.jwt.issuer:urban-furniture-accounting}") String issuer) {
        this.configuredSecret = configuredSecret;
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    @PostConstruct
    void init() {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret is not configured. Copy application-local.yml.example to "
                            + "application-local.yml and set a base64-encoded 32-byte secret.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException ex) {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * The subject is the login ID, which is what the user signs in with.
     * <p>
     * The book id is deliberately <em>not</em> a claim: it is resolved
     * from the database on every request, so revoking or moving a user
     * takes effect immediately rather than when their token expires.
     */
    public String generateToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getLoginId())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .claims(Map.of(
                        "uid", principal.getId(),
                        "access", principal.getAccessLevel().name(),
                        "name", principal.getFullName()))
                .signWith(key)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public long expiryMillis() {
        return expirationMs;
    }

    /** Returns the subject (login ID) if the token is valid, otherwise null. */
    public String extractSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }
}
