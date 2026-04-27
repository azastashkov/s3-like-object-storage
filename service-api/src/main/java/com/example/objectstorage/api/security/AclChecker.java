package com.example.objectstorage.api.security;

import com.example.objectstorage.api.Permission;
import com.example.objectstorage.api.client.IamClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

public class AclChecker {

    private final IamClient iam;
    private final Cache<String, Boolean> cache;

    public AclChecker(IamClient iam, int ttlSeconds) {
        this.iam = iam;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(50_000)
                .build();
    }

    public boolean hasPermission(String bucket, String principal, Permission perm) {
        String key = bucket + ":" + principal + ":" + perm.name();
        return cache.get(key, k -> iam.hasPermission(bucket, principal, perm));
    }

    public void invalidateBucket(String bucket) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(bucket + ":"));
    }
}
