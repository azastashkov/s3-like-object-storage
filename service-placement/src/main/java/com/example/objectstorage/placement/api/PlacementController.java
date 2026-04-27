package com.example.objectstorage.placement.api;

import com.example.objectstorage.api.ClusterMap;
import com.example.objectstorage.api.DataNode;
import com.example.objectstorage.api.PlacementCreateRequest;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.placement.domain.RendezvousPlacement;
import com.example.objectstorage.placement.persistence.PlacementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class PlacementController {

    private final RendezvousPlacement placer;
    private final PlacementRepository repo;
    private final ClusterMap clusterMap;

    public PlacementController(RendezvousPlacement placer, PlacementRepository repo, ClusterMap clusterMap) {
        this.placer = placer;
        this.repo = repo;
        this.clusterMap = clusterMap;
    }

    @PostMapping("/placements")
    public ResponseEntity<PlacementDecision> create(@RequestBody PlacementCreateRequest req) {
        var existing = repo.findById(req.objectId());
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get());
        }
        var picked = placer.pick(req.objectId());
        var decision = new PlacementDecision(
                req.objectId(),
                picked.get(0).id(),
                picked.stream().map(DataNode::id).toList()
        );
        repo.save(decision);
        return ResponseEntity.status(HttpStatus.CREATED).body(decision);
    }

    @GetMapping("/placements/{objectId}")
    public ResponseEntity<PlacementDecision> get(@PathVariable UUID objectId) {
        return repo.findById(objectId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/placements/{objectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void tombstone(@PathVariable UUID objectId) {
        repo.markTombstoned(objectId);
    }

    @PostMapping("/placements/{objectId}/reclaim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reclaim(@PathVariable UUID objectId) {
        repo.markReclaimed(objectId);
    }

    @GetMapping("/placements")
    public List<PlacementDecision> findByState(
            @RequestParam(defaultValue = "TOMBSTONED") String state,
            @RequestParam(defaultValue = "300") int olderThanSeconds,
            @RequestParam(defaultValue = "1000") int limit) {
        return repo.findByState(state, olderThanSeconds, limit);
    }

    @GetMapping("/placements/active")
    public List<PlacementDecision> findActive(
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(defaultValue = "0") long offset) {
        return repo.findActive(limit, offset);
    }

    @GetMapping("/cluster/nodes")
    public ClusterMap nodes() {
        return clusterMap;
    }
}
