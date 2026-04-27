package com.example.objectstorage.api;

public record MetaCreateBucketRequest(
        String name,
        String ownerPrincipal,
        boolean versioningEnabled
) {}
