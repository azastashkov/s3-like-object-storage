package com.example.objectstorage.api;

import java.util.UUID;

public record MetaAddPartRequest(
        UUID uploadId,
        int partNumber,
        UUID partObjectId,
        long sizeBytes,
        String etag
) {}
