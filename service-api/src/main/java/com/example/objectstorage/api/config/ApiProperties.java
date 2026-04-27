package com.example.objectstorage.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "api")
public record ApiProperties(
        Jwt jwt,
        List<String> iamBaseUrls,
        List<String> metadataBaseUrls,
        List<String> placementBaseUrls,
        List<String> dataRoutingBaseUrls,
        int aclCacheTtlSeconds
) {
    public record Jwt(String secret, String issuer) {}
}
