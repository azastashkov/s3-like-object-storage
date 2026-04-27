package com.example.objectstorage.api;

public record DataNode(
        String id,
        String host,
        int port,
        String rack
) {
    public String baseUrl() {
        return "http://" + host + ":" + port;
    }
}
