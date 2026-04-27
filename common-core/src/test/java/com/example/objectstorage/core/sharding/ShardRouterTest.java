package com.example.objectstorage.core.sharding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShardRouterTest {

    @Test
    void distributesEvenlyAcrossFourShards() {
        var router = new ShardRouter(4);
        var counts = new int[4];
        for (int i = 0; i < 10_000; i++) {
            counts[router.shardOf("bucket-" + i, "key-" + i)]++;
        }
        for (int c : counts) {
            assertThat(c).as("shard count").isBetween(2200, 2800);
        }
    }

    @Test
    void deterministicForSameInput() {
        var router = new ShardRouter(4);
        assertThat(router.shardOf("b", "k")).isEqualTo(router.shardOf("b", "k"));
    }

    @Test
    void differentKeysProduceDifferentShardsOften() {
        var router = new ShardRouter(4);
        int diff = 0;
        for (int i = 0; i < 100; i++) {
            if (router.shardOf("b1", "k" + i) != router.shardOf("b2", "k" + i)) diff++;
        }
        assertThat(diff).isGreaterThan(50);
    }
}
