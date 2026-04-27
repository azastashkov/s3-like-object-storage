package com.example.objectstorage.gc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gc")
public record GcProperties(
        List<String> placementBaseUrls,
        List<String> dataRoutingBaseUrls,
        long tombstoneIntervalMs,
        long compactionIntervalMs,
        long rereplicationIntervalMs,
        int tombstoneAgeSeconds
) {}
