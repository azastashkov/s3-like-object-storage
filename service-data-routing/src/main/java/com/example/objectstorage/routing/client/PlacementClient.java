package com.example.objectstorage.routing.client;

import com.example.objectstorage.api.ClusterMap;
import com.example.objectstorage.api.DataNode;
import com.example.objectstorage.api.PlacementCreateRequest;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.core.http.RoundRobinClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlacementClient {

    private final RoundRobinClient http;
    private final Cache<UUID, PlacementDecision> placementCache;
    private volatile Map<String, DataNode> nodeIndex = Map.of();
    private volatile long nodeIndexLoadedAt = 0;
    private final long nodeIndexTtlMs;

    public PlacementClient(RoundRobinClient http, int clusterCacheTtlSeconds) {
        this.http = http;
        this.placementCache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
        this.nodeIndexTtlMs = clusterCacheTtlSeconds * 1000L;
    }

    public PlacementDecision createOrGet(UUID objectId) {
        return placementCache.get(objectId, id -> {
            String base = http.nextBaseUrl();
            return http.client().post()
                    .uri(base + "/placements")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new PlacementCreateRequest(id))
                    .retrieve()
                    .body(PlacementDecision.class);
        });
    }

    public PlacementDecision get(UUID objectId) {
        PlacementDecision cached = placementCache.getIfPresent(objectId);
        if (cached != null) return cached;
        try {
            String base = http.nextBaseUrl();
            PlacementDecision pd = http.client().get()
                    .uri(base + "/placements/" + objectId)
                    .retrieve()
                    .body(PlacementDecision.class);
            placementCache.put(objectId, pd);
            return pd;
        } catch (HttpClientErrorException.NotFound nf) {
            return null;
        }
    }

    public void tombstone(UUID objectId) {
        placementCache.invalidate(objectId);
        String base = http.nextBaseUrl();
        http.client().delete()
                .uri(base + "/placements/" + objectId)
                .retrieve()
                .toBodilessEntity();
    }

    public DataNode resolveNode(String nodeId) {
        long now = System.currentTimeMillis();
        if (now - nodeIndexLoadedAt > nodeIndexTtlMs || nodeIndex.isEmpty()) {
            String base = http.nextBaseUrl();
            ClusterMap map = http.client().get()
                    .uri(base + "/cluster/nodes")
                    .retrieve()
                    .body(ClusterMap.class);
            Map<String, DataNode> idx = new HashMap<>();
            if (map != null) for (DataNode n : map.nodes()) idx.put(n.id(), n);
            this.nodeIndex = Map.copyOf(idx);
            this.nodeIndexLoadedAt = now;
        }
        DataNode n = nodeIndex.get(nodeId);
        if (n == null) {
            throw new IllegalStateException("Unknown node id: " + nodeId);
        }
        return n;
    }
}
