package com.example.objectstorage.iam.domain;

import com.example.objectstorage.api.IamLoginRequest;
import com.example.objectstorage.api.Principal;
import com.example.objectstorage.core.auth.JwtIssuer;
import com.example.objectstorage.iam.persistence.BucketAclRepository;
import com.example.objectstorage.iam.persistence.PrincipalRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IamServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void loginIssuesJwtForValidCredentials() {
        var principals = mock(PrincipalRepository.class);
        var acls = mock(BucketAclRepository.class);
        var jwt = new JwtIssuer(SECRET, Duration.ofMinutes(10), "iam");

        var p = new Principal("loadtest", "Load test", Instant.now());
        when(principals.findByApiKeyHash(IamService.sha256Hex("loadtest-secret-key")))
                .thenReturn(Optional.of(p));

        var svc = new IamService(principals, acls, jwt);
        var resp = svc.login(new IamLoginRequest("loadtest", "loadtest-secret-key"));

        assertThat(resp.principalId()).isEqualTo("loadtest");
        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.expiresInSeconds()).isPositive();
    }

    @Test
    void loginFailsForUnknownKey() {
        var principals = mock(PrincipalRepository.class);
        var acls = mock(BucketAclRepository.class);
        var jwt = new JwtIssuer(SECRET, Duration.ofMinutes(10), "iam");
        when(principals.findByApiKeyHash(any())).thenReturn(Optional.empty());

        var svc = new IamService(principals, acls, jwt);
        assertThatThrownBy(() -> svc.login(new IamLoginRequest("x", "y")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static String any() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
