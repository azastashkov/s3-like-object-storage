package com.example.objectstorage.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iam.jwt")
public record IamProperties(
        String secret,
        int ttlMinutes,
        String issuer
) {}
