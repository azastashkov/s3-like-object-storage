package com.example.objectstorage.api;

import java.time.Instant;

public record Principal(
        String principalId,
        String displayName,
        Instant createdAt
) {}
