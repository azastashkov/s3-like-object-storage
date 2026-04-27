package com.example.objectstorage.core.sharding;

import java.nio.charset.StandardCharsets;

public final class ShardRouter {
    private final int shardCount;

    public ShardRouter(int shardCount) {
        if (shardCount <= 0) throw new IllegalArgumentException("shardCount must be > 0");
        this.shardCount = shardCount;
    }

    public int shardCount() {
        return shardCount;
    }

    public int shardOf(String bucket, String key) {
        long h = fnv1a64(bucket + "/" + key);
        h = mix(h);
        return (int) Math.floorMod(h, shardCount);
    }

    private static long fnv1a64(String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        long h = 0xcbf29ce484222325L;
        for (byte b : data) {
            h ^= (b & 0xffL);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }
}
