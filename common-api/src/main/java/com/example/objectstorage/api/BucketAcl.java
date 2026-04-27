package com.example.objectstorage.api;

public record BucketAcl(
        String bucketName,
        String principalId,
        Permission permission
) {}
