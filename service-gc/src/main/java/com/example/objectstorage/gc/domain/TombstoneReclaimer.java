package com.example.objectstorage.gc.domain;

import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.core.http.RoundRobinClient;
import com.example.objectstorage.gc.config.GcProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.UUID;

@Component
public class TombstoneReclaimer {

    private static final Logger log = LoggerFactory.getLogger(TombstoneReclaimer.class);

    private final RoundRobinClient placement;
    private final RoundRobinClient routing;
    private final GcProperties props;
    private final Counter reclaimed;
    private final Counter failures;
    private final Timer runDuration;

    public TombstoneReclaimer(RoundRobinClient placementHttp, RoundRobinClient dataRoutingHttp,
                               GcProperties props, MeterRegistry meters) {
        this.placement = placementHttp;
        this.routing = dataRoutingHttp;
        this.props = props;
        this.reclaimed = meters.counter("gc.tombstone.reclaimed");
        this.failures = meters.counter("gc.tombstone.failures");
        this.runDuration = meters.timer("gc.run.duration", "kind", "tombstone");
    }

    @Scheduled(initialDelay = 30_000, fixedDelayString = "${gc.tombstone-interval-ms}")
    public void reclaim() {
        runDuration.record(this::doReclaim);
    }

    public int doReclaim() {
        try {
            String base = placement.nextBaseUrl();
            @SuppressWarnings("unchecked")
            List<PlacementDecision> tombstoned = (List<PlacementDecision>) placement.client().get()
                    .uri(base + "/placements?state=TOMBSTONED&olderThanSeconds=" + props.tombstoneAgeSeconds() + "&limit=1000")
                    .retrieve()
                    .body(List.class);
            if (tombstoned == null) return 0;

            int count = 0;
            for (var raw : tombstoned) {
                UUID id = raw.objectId();
                try {
                    String routingBase = routing.nextBaseUrl();
                    routing.client().delete()
                            .uri(routingBase + "/data/" + id)
                            .retrieve()
                            .toBodilessEntity();
                    String placementBase = placement.nextBaseUrl();
                    placement.client().post()
                            .uri(placementBase + "/placements/" + id + "/reclaim")
                            .retrieve()
                            .toBodilessEntity();
                    reclaimed.increment();
                    count++;
                } catch (HttpClientErrorException.NotFound ignored) {
                    // already gone
                } catch (Exception e) {
                    failures.increment();
                    log.warn("Failed to reclaim {}: {}", id, e.getMessage());
                }
            }
            log.info("tombstone-reclaim: reclaimed {} objects", count);
            return count;
        } catch (Exception e) {
            log.error("Tombstone reclaim run failed", e);
            return 0;
        }
    }
}
