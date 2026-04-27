package com.example.objectstorage.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MultipartUpload(
        UUID uploadId,
        String bucketName,
        String objectKey,
        String initiator,
        Map<String, String> userMetadata,
        Instant initiatedAt
) {}
