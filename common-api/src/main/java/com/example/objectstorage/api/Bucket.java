package com.example.objectstorage.api;

import java.time.Instant;

public record Bucket(
        String name,
        String ownerPrincipal,
        boolean versioningEnabled,
        Instant createdAt
) {}
