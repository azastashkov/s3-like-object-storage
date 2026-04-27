package com.example.objectstorage.gc.domain;

import com.example.objectstorage.api.ClusterMap;
import com.example.objectstorage.api.DataNode;
import com.example.objectstorage.core.http.RoundRobinClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class CompactionTrigger {

    private static final Logger log = LoggerFactory.getLogger(CompactionTrigger.class);

    private final RoundRobinClient placement;
    private final RestClient nodeHttp;
    private final Counter triggered;
    private final Counter chunksCompacted;
    private final Counter failures;
    private final Timer runDuration;

    public CompactionTrigger(RoundRobinClient placementHttp, MeterRegistry meters) {
        this.placement = placementHttp;
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.nodeHttp = RestClient.builder()
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(jdk))
                .build();
        this.triggered = meters.counter("gc.compaction.triggered");
        this.chunksCompacted = meters.counter("gc.compaction.chunks");
        this.failures = meters.counter("gc.compaction.failures");
        this.runDuration = meters.timer("gc.run.duration", "kind", "compaction");
    }

    @Scheduled(initialDelay = 120_000, fixedDelayString = "${gc.compaction-interval-ms}")
    public void compact() {
        runDuration.record(this::doCompact);
    }

    public int doCompact() {
        try {
            String base = placement.nextBaseUrl();
            ClusterMap map = placement.client().get()
                    .uri(base + "/cluster/nodes")
                    .retrieve()
                    .body(ClusterMap.class);
            if (map == null) return 0;
            int total = 0;
            for (DataNode node : map.nodes()) {
                triggered.increment();
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resp = nodeHttp.post()
                            .uri(node.baseUrl() + "/compact")
                            .retrieve()
                            .body(Map.class);
                    if (resp != null && resp.get("compactedChunks") instanceof Number n) {
                        chunksCompacted.increment(n.doubleValue());
                        total += n.intValue();
                    }
                } catch (Exception e) {
                    failures.increment();
                    log.warn("Compaction on {} failed: {}", node.id(), e.getMessage());
                }
            }
            log.info("compaction: triggered {} nodes, compacted {} chunks total", map.nodes().size(), total);
            return total;
        } catch (Exception e) {
            log.error("Compaction run failed", e);
            return 0;
        }
    }
}
