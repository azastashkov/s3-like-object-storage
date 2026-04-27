package com.example.objectstorage.core.auth;

import java.time.Instant;
import java.util.List;

public record JwtClaims(
        String principalId,
        List<String> roles,
        Instant expiresAt
) {}
