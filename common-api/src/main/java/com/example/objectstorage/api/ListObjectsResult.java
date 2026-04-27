package com.example.objectstorage.api;

import java.util.List;

public record ListObjectsResult(
        String bucketName,
        String prefix,
        int keyCount,
        boolean truncated,
        String nextContinuationToken,
        List<ObjectVersionRow> objects
) {}
