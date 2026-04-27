package com.example.objectstorage.api.client;

import com.example.objectstorage.api.AssembleRequest;
import com.example.objectstorage.api.DataNodePutResult;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.core.http.RoundRobinClient;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

public class DataRoutingClient {

    private final RoundRobinClient http;

    public DataRoutingClient(RoundRobinClient http) {
        this.http = http;
    }

    public DataNodePutResult put(UUID objectId, byte[] payload, PlacementDecision pd) {
        String base = http.nextBaseUrl();
        return http.client().put()
                .uri(base + "/data/" + objectId)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Replicas", String.join(",", pd.replicaNodes()))
                .header("X-Primary", pd.primaryNode())
                .body(payload)
                .retrieve()
                .body(DataNodePutResult.class);
    }

    public byte[] get(UUID objectId) {
        String base = http.nextBaseUrl();
        try {
            return http.client().get()
                    .uri(base + "/data/" + objectId)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .body(byte[].class);
        } catch (HttpClientErrorException.NotFound nf) {
            return null;
        }
    }

    public void delete(UUID objectId) {
        String base = http.nextBaseUrl();
        try {
            http.client().delete()
                    .uri(base + "/data/" + objectId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) { }
    }

    public DataNodePutResult assemble(AssembleRequest req) {
        String base = http.nextBaseUrl();
        return http.client().post()
                .uri(base + "/data/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(DataNodePutResult.class);
    }
}
