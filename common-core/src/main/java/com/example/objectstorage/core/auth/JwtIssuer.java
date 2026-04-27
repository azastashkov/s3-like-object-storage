package com.example.objectstorage.core.auth;

import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public final class JwtIssuer {
    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtIssuer(String secret, Duration ttl, String issuer) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes (got " + bytes.length + ")");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public String issue(String principalId, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(principalId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
