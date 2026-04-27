package com.example.objectstorage.api;

import java.util.List;
import java.util.UUID;

public record AssembleRequest(
        UUID targetObjectId,
        List<UUID> sourceObjectIds,
        List<String> replicaNodes,
        String primaryNode
) {}
