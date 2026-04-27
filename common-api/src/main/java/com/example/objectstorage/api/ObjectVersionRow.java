package com.example.objectstorage.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ObjectVersionRow(
        String bucketName,
        String objectKey,
        String versionId,
        UUID objectId,
        boolean isDeleteMarker,
        long sizeBytes,
        String contentType,
        String etag,
        Map<String, String> userMetadata,
        Instant createdAt
) {}
