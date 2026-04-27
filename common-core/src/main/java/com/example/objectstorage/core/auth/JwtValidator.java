package com.example.objectstorage.core.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class JwtValidator {
    private final SecretKey key;
    private final String expectedIssuer;

    public JwtValidator(String secret, String expectedIssuer) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.expectedIssuer = expectedIssuer;
    }

    public JwtClaims validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(expectedIssuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.getOrDefault("roles", List.of());
            return new JwtClaims(
                    claims.getSubject(),
                    roles,
                    claims.getExpiration().toInstant()
            );
        } catch (JwtException e) {
            throw new InvalidJwtException("Invalid JWT: " + e.getMessage(), e);
        }
    }
}
