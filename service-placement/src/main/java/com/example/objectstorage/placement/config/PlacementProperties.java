package com.example.objectstorage.placement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "placement")
public record PlacementProperties(
        int replicationFactor,
        String clusterMapPath
) {}
