package com.example.objectstorage.api;

public record DataNodePutResult(
        String etag,
        long sizeBytes,
        int crc32c
) {}
