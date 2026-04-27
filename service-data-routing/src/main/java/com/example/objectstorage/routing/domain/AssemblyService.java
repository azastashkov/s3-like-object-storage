package com.example.objectstorage.routing.domain;

import com.example.objectstorage.api.AssembleRequest;
import com.example.objectstorage.api.DataNodePutResult;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.routing.client.PlacementClient;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Stream-and-rewrite assembly: reads each part by objectId from data nodes,
 * concatenates into a single payload, writes a new packed object at targetObjectId.
 */
@Component
public class AssemblyService {

    private final ReplicationCoordinator coordinator;
    private final PlacementClient placement;

    public AssemblyService(ReplicationCoordinator coordinator, PlacementClient placement) {
        this.coordinator = coordinator;
        this.placement = placement;
    }

    public DataNodePutResult assemble(AssembleRequest req) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (java.util.UUID partObjectId : req.sourceObjectIds()) {
            PlacementDecision partPlacement = placement.get(partObjectId);
            if (partPlacement == null) {
                throw new IllegalStateException("Missing placement for part " + partObjectId);
            }
            byte[] partBytes = coordinator.read(partObjectId, partPlacement);
            try {
                baos.write(partBytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        byte[] full = baos.toByteArray();

        PlacementDecision target = new PlacementDecision(
                req.targetObjectId(), req.primaryNode(), req.replicaNodes());
        return coordinator.write(req.targetObjectId(), full, target);
    }
}
