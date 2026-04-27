package com.example.objectstorage.api;

import java.util.List;
import java.util.UUID;

public record PlacementDecision(
        UUID objectId,
        String primaryNode,
        List<String> replicaNodes
) {}
