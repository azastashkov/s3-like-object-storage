package com.example.objectstorage.routing.api;

import com.example.objectstorage.api.ApiError;
import com.example.objectstorage.api.AssembleRequest;
import com.example.objectstorage.api.DataNodePutResult;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.core.checksum.Crc32cUtil;
import com.example.objectstorage.routing.client.PlacementClient;
import com.example.objectstorage.routing.domain.AssemblyService;
import com.example.objectstorage.routing.domain.CorruptDataException;
import com.example.objectstorage.routing.domain.QuorumNotReachedException;
import com.example.objectstorage.routing.domain.ReplicationCoordinator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/data")
public class DataRoutingController {

    private final ReplicationCoordinator coordinator;
    private final AssemblyService assembly;
    private final PlacementClient placement;

    public DataRoutingController(ReplicationCoordinator coordinator,
                                  AssemblyService assembly,
                                  PlacementClient placement) {
        this.coordinator = coordinator;
        this.assembly = assembly;
        this.placement = placement;
    }

    @PutMapping(value = "/{objectId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public DataNodePutResult put(@PathVariable UUID objectId,
                                  @RequestHeader(value = "X-Replicas", required = false) String replicas,
                                  @RequestHeader(value = "X-Primary", required = false) String primary,
                                  @RequestBody byte[] payload) {
        PlacementDecision pd;
        if (replicas != null && primary != null) {
            pd = new PlacementDecision(objectId, primary, java.util.List.of(replicas.split(",")));
        } else {
            pd = placement.createOrGet(objectId);
        }
        return coordinator.write(objectId, payload, pd);
    }

    @GetMapping(value = "/{objectId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable UUID objectId) {
        PlacementDecision pd = placement.get(objectId);
        if (pd == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = coordinator.read(objectId, pd);
        HttpHeaders h = new HttpHeaders();
        h.set("X-CRC32C", Crc32cUtil.toHex(Crc32cUtil.compute(data)));
        return ResponseEntity.ok().headers(h).body(data);
    }

    @DeleteMapping("/{objectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID objectId) {
        PlacementDecision pd = placement.get(objectId);
        if (pd == null) return;
        coordinator.delete(objectId, pd);
        placement.tombstone(objectId);
    }

    @PostMapping("/assemble")
    public DataNodePutResult assemble(@RequestBody AssembleRequest req) {
        return assembly.assemble(req);
    }

    @ExceptionHandler(QuorumNotReachedException.class)
    public ResponseEntity<ApiError> quorum(QuorumNotReachedException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("QuorumNotReached", e.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(CorruptDataException.class)
    public ResponseEntity<ApiError> corrupt(CorruptDataException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("CorruptData", e.getMessage(), UUID.randomUUID().toString()));
    }
}
