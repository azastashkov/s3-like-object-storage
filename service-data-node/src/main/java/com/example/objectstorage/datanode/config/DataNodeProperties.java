package com.example.objectstorage.datanode.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datanode")
public record DataNodeProperties(
        String id,
        String rack,
        String dataDir,
        long chunkSizeBytes,
        double compactionDeletedRatio
) {}
