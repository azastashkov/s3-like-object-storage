package com.example.objectstorage.gc.domain;

import com.example.objectstorage.api.ClusterMap;
import com.example.objectstorage.api.DataNode;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.core.http.RoundRobinClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReReplicator {

    private static final Logger log = LoggerFactory.getLogger(ReReplicator.class);

    private final RoundRobinClient placement;
    private final RestClient nodeHttp;
    private final Counter repaired;
    private final Counter failures;
    private final Timer runDuration;

    public ReReplicator(RoundRobinClient placementHttp, MeterRegistry meters) {
        this.placement = placementHttp;
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.nodeHttp = RestClient.builder()
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(jdk))
                .build();
        this.repaired = meters.counter("gc.rereplication.repaired");
        this.failures = meters.counter("gc.rereplication.failures");
        this.runDuration = meters.timer("gc.run.duration", "kind", "rereplication");
    }

    @Scheduled(initialDelay = 180_000, fixedDelayString = "${gc.rereplication-interval-ms}")
    public void rereplicate() {
        runDuration.record(this::doRereplicate);
    }

    public int doRereplicate() {
        try {
            String base = placement.nextBaseUrl();
            ClusterMap map = placement.client().get()
                    .uri(base + "/cluster/nodes")
                    .retrieve()
                    .body(ClusterMap.class);
            if (map == null || map.nodes().isEmpty()) return 0;
            Map<String, DataNode> byId = new HashMap<>();
            for (DataNode n : map.nodes()) byId.put(n.id(), n);

            String placementBase = placement.nextBaseUrl();
            List<PlacementDecision> active = placement.client().get()
                    .uri(placementBase + "/placements/active?limit=500")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PlacementDecision>>() {});
            if (active == null) return 0;

            int total = 0;
            for (PlacementDecision raw : active) {
                String missingNode = null;
                String healthyNode = null;
                for (String nodeId : raw.replicaNodes()) {
                    DataNode node = byId.get(nodeId);
                    if (node == null) continue;
                    boolean exists;
                    try {
                        var status = nodeHttp.head()
                                .uri(node.baseUrl() + "/chunks/" + raw.objectId())
                                .retrieve()
                                .toBodilessEntity()
                                .getStatusCode();
                        exists = status.is2xxSuccessful();
                    } catch (Exception e) {
                        exists = false;
                    }
                    if (exists && healthyNode == null) healthyNode = node.id();
                    if (!exists && missingNode == null) missingNode = node.id();
                }
                if (missingNode != null && healthyNode != null) {
                    DataNode target = byId.get(missingNode);
                    DataNode source = byId.get(healthyNode);
                    try {
                        nodeHttp.post()
                                .uri(target.baseUrl() + "/replicate")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .body(Map.of(
                                        "objectId", raw.objectId().toString(),
                                        "sourceUrl", source.baseUrl()
                                ))
                                .retrieve()
                                .toBodilessEntity();
                        repaired.increment();
                        total++;
                    } catch (Exception e) {
                        failures.increment();
                        log.warn("Rereplication of {} from {} to {} failed: {}",
                                raw.objectId(), source.id(), target.id(), e.getMessage());
                    }
                }
            }
            log.info("rereplication: repaired {} objects", total);
            return total;
        } catch (Exception e) {
            log.error("Rereplication run failed", e);
            return 0;
        }
    }
}
