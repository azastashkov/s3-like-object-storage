package com.example.objectstorage.api;

import java.util.Map;
import java.util.UUID;

public record MetaCreateMultipartRequest(
        UUID uploadId,
        String bucketName,
        String objectKey,
        String initiator,
        Map<String, String> userMetadata
) {}
