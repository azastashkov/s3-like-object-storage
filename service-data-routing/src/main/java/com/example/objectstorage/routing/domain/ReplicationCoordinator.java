package com.example.objectstorage.routing.domain;

import com.example.objectstorage.api.DataNode;
import com.example.objectstorage.api.DataNodePutResult;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.core.checksum.Crc32cUtil;
import com.example.objectstorage.routing.client.PlacementClient;
import com.example.objectstorage.routing.config.RoutingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReplicationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ReplicationCoordinator.class);

    private final RestClient http;
    private final PlacementClient placement;
    private final RoutingProperties props;
    private final Counter quorumOk;
    private final Counter quorumFail;
    private final Counter tailFailures;
    private final Counter readRetries;
    private final Counter readCrcMismatch;
    private final Timer writeTimer;
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

    public ReplicationCoordinator(RestClient http, PlacementClient placement,
                                   RoutingProperties props, MeterRegistry meters) {
        this.http = http;
        this.placement = placement;
        this.props = props;
        this.quorumOk = meters.counter("routing.write.quorum", "result", "ok");
        this.quorumFail = meters.counter("routing.write.quorum", "result", "fail");
        this.tailFailures = meters.counter("routing.write.tail.replica.failures");
        this.readRetries = meters.counter("routing.read.retries");
        this.readCrcMismatch = meters.counter("routing.read.crc.mismatch");
        this.writeTimer = Timer.builder("routing.write.duration")
                .tags(Tags.empty())
                .publishPercentileHistogram()
                .register(meters);
    }

    public DataNodePutResult write(UUID objectId, byte[] payload, PlacementDecision decision) {
        long start = System.nanoTime();
        try {
            int crc = Crc32cUtil.compute(payload);
            String crcHex = Crc32cUtil.toHex(crc);

            List<String> nodeIds = decision.replicaNodes();
            int total = nodeIds.size();
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);
            ConcurrentLinkedQueue<DataNodePutResult> results = new ConcurrentLinkedQueue<>();

            List<CompletableFuture<Void>> futures = new ArrayList<>(total);
            for (String nodeId : nodeIds) {
                DataNode node = placement.resolveNode(nodeId);
                CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                    try {
                        DataNodePutResult result = http.put()
                                .uri(node.baseUrl() + "/chunks/" + objectId)
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .header("X-CRC32C", crcHex)
                                .body(payload)
                                .retrieve()
                                .body(DataNodePutResult.class);
                        results.add(result);
                        successes.incrementAndGet();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        tailFailures.increment();
                        log.warn("write to {} failed for {}: {}", node.id(), objectId, e.getMessage());
                    }
                }, io);
                futures.add(f);
            }

            int quorum = props.writeQuorum();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(props.writeTimeoutMs());
            while (System.nanoTime() < deadline) {
                if (successes.get() >= quorum) break;
                if (failures.get() > total - quorum) break;
                try { Thread.sleep(2); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (successes.get() < quorum) {
                quorumFail.increment();
                throw new QuorumNotReachedException(
                        "objectId=" + objectId + " quorum=" + quorum + " successes=" + successes.get());
            }
            quorumOk.increment();
            DataNodePutResult any = results.peek();
            if (any == null) {
                throw new QuorumNotReachedException("no result body for " + objectId);
            }
            return any;
        } finally {
            writeTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    public byte[] read(UUID objectId, PlacementDecision decision) {
        Exception last = null;
        for (String nodeId : orderedReplicas(decision)) {
            DataNode node = placement.resolveNode(nodeId);
            try {
                var entity = http.get()
                        .uri(node.baseUrl() + "/chunks/" + objectId)
                        .accept(MediaType.APPLICATION_OCTET_STREAM)
                        .retrieve()
                        .toEntity(byte[].class);
                byte[] body = entity.getBody();
                if (body == null) throw new IllegalStateException("empty body");

                String crcHeader = entity.getHeaders().getFirst("X-CRC32C");
                int actual = Crc32cUtil.compute(body);
                if (crcHeader != null && Crc32cUtil.fromHex(crcHeader) != actual) {
                    readCrcMismatch.increment();
                    throw new CorruptDataException(
                            "CRC mismatch from " + node.id() + " for " + objectId);
                }
                return body;
            } catch (Exception e) {
                last = e;
                readRetries.increment();
                log.warn("read from {} failed for {}: {}", node.id(), objectId, e.getMessage());
            }
        }
        throw new RuntimeException("All replicas failed for " + objectId, last);
    }

    public void delete(UUID objectId, PlacementDecision decision) {
        for (String nodeId : decision.replicaNodes()) {
            DataNode node = placement.resolveNode(nodeId);
            try {
                http.delete()
                        .uri(node.baseUrl() + "/chunks/" + objectId)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {})
                        .toBodilessEntity();
            } catch (Exception e) {
                log.debug("delete on {} for {} failed: {}", node.id(), objectId, e.getMessage());
            }
        }
    }

    private List<String> orderedReplicas(PlacementDecision d) {
        List<String> ordered = new ArrayList<>(d.replicaNodes().size());
        ordered.add(d.primaryNode());
        for (String n : d.replicaNodes()) if (!n.equals(d.primaryNode())) ordered.add(n);
        return ordered;
    }
}
