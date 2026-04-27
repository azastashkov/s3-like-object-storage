package com.example.objectstorage.api;

import java.util.Map;
import java.util.UUID;

public record MetaPutVersionRequest(
        String bucketName,
        String objectKey,
        String versionId,
        UUID objectId,
        boolean isDeleteMarker,
        long sizeBytes,
        String contentType,
        String etag,
        Map<String, String> userMetadata
) {}
