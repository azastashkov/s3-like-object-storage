package com.example.objectstorage.api;

public record IamLoginResponse(
        String accessToken,
        long expiresInSeconds,
        String principalId
) {}
