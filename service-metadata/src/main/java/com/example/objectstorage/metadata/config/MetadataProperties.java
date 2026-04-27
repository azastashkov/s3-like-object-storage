package com.example.objectstorage.metadata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "metadata")
public record MetadataProperties(
        int shardCount,
        DbSpec global,
        List<DbSpec> shards
) {
    public record DbSpec(String url, String username, String password) {}
}
