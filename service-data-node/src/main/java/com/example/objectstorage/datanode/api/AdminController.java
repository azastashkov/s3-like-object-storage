package com.example.objectstorage.datanode.api;

import com.example.objectstorage.datanode.config.DataNodeProperties;
import com.example.objectstorage.datanode.storage.ChunkFileStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@RestController
public class AdminController {

    private final ChunkFileStore store;
    private final DataNodeProperties props;
    private final RestClient http = RestClient.create();
    private final Counter compactRuns;
    private final Counter compactedChunks;
    private final Counter replicateOk;

    public AdminController(ChunkFileStore store, DataNodeProperties props, MeterRegistry meters) {
        this.store = store;
        this.props = props;
        this.compactRuns = meters.counter("datanode.compaction.runs");
        this.compactedChunks = meters.counter("datanode.compaction.chunks");
        this.replicateOk = meters.counter("datanode.replication.ok");
    }

    @PostMapping("/compact")
    public Map<String, Object> compact() {
        compactRuns.increment();
        int n = store.compactAll(props.compactionDeletedRatio());
        compactedChunks.increment(n);
        return Map.of("compactedChunks", n);
    }

    @PostMapping("/replicate")
    public Map<String, Object> replicate(@org.springframework.web.bind.annotation.RequestBody ReplicateRequest req) {
        // Pull from the source node and write locally
        byte[] data = http.get()
                .uri(req.sourceUrl() + "/chunks/" + req.objectId())
                .retrieve()
                .body(byte[].class);
        if (data == null) {
            return Map.of("ok", false, "reason", "empty");
        }
        store.write(req.objectId(), data);
        replicateOk.increment();
        return Map.of("ok", true, "bytes", data.length);
    }

    public record ReplicateRequest(UUID objectId, String sourceUrl) {}
}
