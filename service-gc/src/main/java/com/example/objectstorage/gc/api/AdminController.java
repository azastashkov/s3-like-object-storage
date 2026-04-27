package com.example.objectstorage.gc.api;

import com.example.objectstorage.gc.domain.CompactionTrigger;
import com.example.objectstorage.gc.domain.ReReplicator;
import com.example.objectstorage.gc.domain.TombstoneReclaimer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminController {

    private final TombstoneReclaimer tombstone;
    private final CompactionTrigger compaction;
    private final ReReplicator rereplication;

    public AdminController(TombstoneReclaimer tombstone, CompactionTrigger compaction, ReReplicator rereplication) {
        this.tombstone = tombstone;
        this.compaction = compaction;
        this.rereplication = rereplication;
    }

    @PostMapping("/gc/run/{kind}")
    public ResponseEntity<Map<String, Object>> run(@PathVariable String kind) {
        int count = switch (kind) {
            case "tombstone"     -> tombstone.doReclaim();
            case "compaction"    -> compaction.doCompact();
            case "rereplication" -> rereplication.doRereplicate();
            default -> -1;
        };
        if (count == -1) return ResponseEntity.badRequest().body(Map.of("error", "unknown kind: " + kind));
        return ResponseEntity.ok(Map.of("kind", kind, "count", count));
    }
}
