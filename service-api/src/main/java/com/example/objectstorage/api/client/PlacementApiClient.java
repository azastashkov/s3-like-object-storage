package com.example.objectstorage.api.client;

import com.example.objectstorage.api.PlacementCreateRequest;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.core.http.RoundRobinClient;
import org.springframework.http.MediaType;

import java.util.UUID;

public class PlacementApiClient {

    private final RoundRobinClient http;

    public PlacementApiClient(RoundRobinClient http) {
        this.http = http;
    }

    public PlacementDecision create(UUID objectId) {
        String base = http.nextBaseUrl();
        return http.client().post()
                .uri(base + "/placements")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PlacementCreateRequest(objectId))
                .retrieve()
                .body(PlacementDecision.class);
    }
}
