package com.example.objectstorage.core.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtIssuerValidatorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void issueAndValidateRoundTrip() {
        var issuer = new JwtIssuer(SECRET, Duration.ofMinutes(10), "iam");
        var validator = new JwtValidator(SECRET, "iam");
        String token = issuer.issue("loadtest", List.of("user"));
        JwtClaims claims = validator.validate(token);
        assertThat(claims.principalId()).isEqualTo("loadtest");
        assertThat(claims.roles()).containsExactly("user");
    }

    @Test
    void rejectsTamperedSignature() {
        var issuer = new JwtIssuer(SECRET, Duration.ofMinutes(10), "iam");
        var validator = new JwtValidator(SECRET, "iam");
        String token = issuer.issue("loadtest", List.of("user"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThatThrownBy(() -> validator.validate(tampered))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsWrongIssuer() {
        var issuer = new JwtIssuer(SECRET, Duration.ofMinutes(10), "rogue");
        var validator = new JwtValidator(SECRET, "iam");
        String token = issuer.issue("x", List.of());
        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsExpired() throws Exception {
        var issuer = new JwtIssuer(SECRET, Duration.ofMillis(1), "iam");
        var validator = new JwtValidator(SECRET, "iam");
        String token = issuer.issue("x", List.of());
        Thread.sleep(50);
        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsTooShortSecret() {
        assertThatThrownBy(() -> new JwtIssuer("short", Duration.ofMinutes(10), "iam"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
