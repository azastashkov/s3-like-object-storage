package com.example.objectstorage.api;

import java.time.Instant;
import java.util.UUID;

public record MultipartPart(
        UUID uploadId,
        int partNumber,
        UUID partObjectId,
        long sizeBytes,
        String etag,
        Instant uploadedAt
) {}
