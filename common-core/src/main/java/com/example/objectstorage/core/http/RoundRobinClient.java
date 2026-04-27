package com.example.objectstorage.core.http;

import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinClient {
    private final List<String> baseUrls;
    private final AtomicInteger idx = new AtomicInteger();
    private final RestClient client;

    public RoundRobinClient(List<String> baseUrls) {
        if (baseUrls == null || baseUrls.isEmpty()) {
            throw new IllegalArgumentException("baseUrls must be non-empty");
        }
        this.baseUrls = List.copyOf(baseUrls);
        this.client = RestClient.builder().build();
    }

    public RoundRobinClient(List<String> baseUrls, RestClient client) {
        if (baseUrls == null || baseUrls.isEmpty()) {
            throw new IllegalArgumentException("baseUrls must be non-empty");
        }
        this.baseUrls = List.copyOf(baseUrls);
        this.client = client;
    }

    public String nextBaseUrl() {
        return baseUrls.get(Math.floorMod(idx.getAndIncrement(), baseUrls.size()));
    }

    public RestClient client() {
        return client;
    }

    public int size() {
        return baseUrls.size();
    }
}
