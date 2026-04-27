package com.example.objectstorage.routing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "routing")
public record RoutingProperties(
        List<String> placementBaseUrls,
        int writeQuorum,
        int readTimeoutMs,
        int writeTimeoutMs,
        int clusterCacheTtlSeconds
) {}
