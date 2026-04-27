package com.example.objectstorage.loadtest;

import com.example.objectstorage.api.IamLoginRequest;
import com.example.objectstorage.api.IamLoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Command(name = "loadtest", mixinStandardHelpOptions = true)
public class LoadTestMain implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(LoadTestMain.class);

    @Option(names = "--api-base") String apiBase = env("API_BASE", "http://localhost:8080");
    @Option(names = "--iam-base") String iamBase = env("IAM_BASE", "http://localhost:8081");
    @Option(names = "--principal") String principal = env("LOAD_PRINCIPAL", "loadtest");
    @Option(names = "--secret") String secret = env("LOAD_SECRET", "loadtest-secret-key");
    @Option(names = "--threads") int threads = envInt("LOAD_THREADS", 32);
    @Option(names = "--duration-s") int durationSec = envInt("LOAD_DURATION_SEC", 120);
    @Option(names = "--bucket") String bucket = "loadtest-" + System.currentTimeMillis();
    @Option(names = "--metrics-port") int metricsPort = 8087;

    public static void main(String[] args) {
        int code = new CommandLine(new LoadTestMain()).execute(args);
        System.exit(code);
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }

    private static int envInt(String k, int def) {
        try { return Integer.parseInt(env(k, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    @Override
    public void run() {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        startMetricsServer(registry);

        var http = httpClient();
        ObjectMapper json = new ObjectMapper();

        log.info("Starting load test: apiBase={} threads={} durationSec={} bucket={}",
                apiBase, threads, durationSec, bucket);

        String token = login(http);
        log.info("Login successful, token length={}", token.length());

        if (!createBucket(http, token, bucket)) {
            log.error("Failed to create bucket {}", bucket);
            return;
        }

        Counter putOk = registry.counter("loadtest.ops", "op", "put", "result", "ok");
        Counter putErr = registry.counter("loadtest.ops", "op", "put", "result", "err");
        Counter getOk = registry.counter("loadtest.ops", "op", "get", "result", "ok");
        Counter getErr = registry.counter("loadtest.ops", "op", "get", "result", "err");
        Counter delOk = registry.counter("loadtest.ops", "op", "delete", "result", "ok");
        Counter delErr = registry.counter("loadtest.ops", "op", "delete", "result", "err");
        Counter mpOk = registry.counter("loadtest.ops", "op", "multipart", "result", "ok");
        Counter mpErr = registry.counter("loadtest.ops", "op", "multipart", "result", "err");
        Timer putT = Timer.builder("loadtest.duration").tag("op", "put").publishPercentileHistogram().register(registry);
        Timer getT = Timer.builder("loadtest.duration").tag("op", "get").publishPercentileHistogram().register(registry);
        Timer delT = Timer.builder("loadtest.duration").tag("op", "delete").publishPercentileHistogram().register(registry);
        Timer mpT  = Timer.builder("loadtest.duration").tag("op", "multipart").publishPercentileHistogram().register(registry);

        java.util.concurrent.CopyOnWriteArrayList<String> uploadedKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger uploadedCount = new AtomicInteger();

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSec);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    var rnd = ThreadLocalRandom.current();
                    while (System.nanoTime() < deadline) {
                        int op = rnd.nextInt(100);
                        try {
                            if (op < 50) {
                                String key = "obj-" + threadId + "-" + uploadedCount.incrementAndGet();
                                int size = 1024 + rnd.nextInt(255 * 1024);
                                byte[] payload = new byte[size];
                                rnd.nextBytes(payload);
                                long s = System.nanoTime();
                                putObject(http, token, bucket, key, payload);
                                putT.record(System.nanoTime() - s, TimeUnit.NANOSECONDS);
                                putOk.increment();
                                uploadedKeys.add(key);
                            } else if (op < 80) {
                                String key = pickRandom(uploadedKeys, rnd);
                                if (key == null) continue;
                                long s = System.nanoTime();
                                getObject(http, token, bucket, key);
                                getT.record(System.nanoTime() - s, TimeUnit.NANOSECONDS);
                                getOk.increment();
                            } else if (op < 85) {
                                String key = "mp-" + threadId + "-" + uploadedCount.incrementAndGet();
                                long s = System.nanoTime();
                                multipartUpload(http, token, bucket, key);
                                mpT.record(System.nanoTime() - s, TimeUnit.NANOSECONDS);
                                mpOk.increment();
                                uploadedKeys.add(key);
                            } else {
                                String key = pickRandomAndRemove(uploadedKeys, rnd);
                                if (key == null) continue;
                                long s = System.nanoTime();
                                deleteObject(http, token, bucket, key);
                                delT.record(System.nanoTime() - s, TimeUnit.NANOSECONDS);
                                delOk.increment();
                            }
                        } catch (HttpClientErrorException e) {
                            if (op < 50) putErr.increment();
                            else if (op < 80) getErr.increment();
                            else if (op < 85) mpErr.increment();
                            else delErr.increment();
                            log.debug("op {} failed: {}", op, e.getStatusCode());
                        } catch (Exception e) {
                            if (op < 50) putErr.increment();
                            else if (op < 80) getErr.increment();
                            else if (op < 85) mpErr.increment();
                            else delErr.increment();
                            log.debug("op {} failed", op, e);
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        try { done.await(durationSec + 30L, TimeUnit.SECONDS); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        pool.shutdown();
        try { pool.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        long totalOps = (long) (putOk.count() + getOk.count() + delOk.count() + mpOk.count());
        long totalErr = (long) (putErr.count() + getErr.count() + delErr.count() + mpErr.count());

        log.info("=== Load Test Summary ===");
        log.info("Duration:       {} s", durationSec);
        log.info("Bucket:         {}", bucket);
        log.info("Total ops OK:   {}", totalOps);
        log.info("Total ops ERR:  {}", totalErr);
        log.info("Throughput:     {} ops/s", String.format("%.1f", totalOps / (double) durationSec));
        log.info("PUT ok={}  err={}", (long) putOk.count(), (long) putErr.count());
        log.info("GET ok={}  err={}", (long) getOk.count(), (long) getErr.count());
        log.info("MP  ok={}  err={}", (long) mpOk.count(), (long) mpErr.count());
        log.info("DEL ok={}  err={}", (long) delOk.count(), (long) delErr.count());
        log.info("PUT mean={} ms max={} ms", fmtMs(putT.mean(TimeUnit.MILLISECONDS)), fmtMs(putT.max(TimeUnit.MILLISECONDS)));
        log.info("GET mean={} ms max={} ms", fmtMs(getT.mean(TimeUnit.MILLISECONDS)), fmtMs(getT.max(TimeUnit.MILLISECONDS)));
        log.info("MP  mean={} ms max={} ms", fmtMs(mpT.mean(TimeUnit.MILLISECONDS)), fmtMs(mpT.max(TimeUnit.MILLISECONDS)));
        log.info("DEL mean={} ms max={} ms", fmtMs(delT.mean(TimeUnit.MILLISECONDS)), fmtMs(delT.max(TimeUnit.MILLISECONDS)));

        // Keep metrics server alive for a bit so Prometheus can scrape final state
        try { Thread.sleep(15_000); } catch (InterruptedException ignored) {}
    }

    private RestClient httpClient() {
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory).build();
    }

    private String login(RestClient http) {
        IamLoginResponse resp = http.post()
                .uri(iamBase + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new IamLoginRequest(principal, secret))
                .retrieve()
                .body(IamLoginResponse.class);
        return resp.accessToken();
    }

    private boolean createBucket(RestClient http, String token, String name) {
        try {
            http.put()
                    .uri(apiBase + "/" + name)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.Conflict c) {
            return true;
        } catch (Exception e) {
            log.error("createBucket failed", e);
            return false;
        }
    }

    private void putObject(RestClient http, String token, String bucket, String key, byte[] body) {
        http.put()
                .uri(apiBase + "/" + bucket + "/" + key)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private byte[] getObject(RestClient http, String token, String bucket, String key) {
        return http.get()
                .uri(apiBase + "/" + bucket + "/" + key)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .body(byte[].class);
    }

    private void deleteObject(RestClient http, String token, String bucket, String key) {
        http.delete()
                .uri(apiBase + "/" + bucket + "/" + key)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private void multipartUpload(RestClient http, String token, String bucket, String key) {
        Map<String, Object> initiate = http.post()
                .uri(apiBase + "/" + bucket + "/" + key + "?uploads")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(Map.class);
        String uploadId = (String) initiate.get("UploadId");

        int parts = 3;
        for (int i = 1; i <= parts; i++) {
            byte[] partBody = new byte[1024 * 1024]; // 1 MiB
            ThreadLocalRandom.current().nextBytes(partBody);
            http.put()
                    .uri(apiBase + "/" + bucket + "/" + key + "?uploadId=" + uploadId + "&partNumber=" + i)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(partBody)
                    .retrieve()
                    .toBodilessEntity();
        }

        List<Map<String, Object>> partsBody = new ArrayList<>();
        for (int i = 1; i <= parts; i++) {
            Map<String, Object> p = new HashMap<>();
            p.put("PartNumber", i);
            partsBody.add(p);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("Parts", partsBody);
        http.post()
                .uri(apiBase + "/" + bucket + "/" + key + "?uploadId=" + uploadId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private static String fmtMs(double v) {
        return Double.isFinite(v) ? String.format("%.1f", v) : "n/a";
    }

    private static String pickRandom(java.util.concurrent.CopyOnWriteArrayList<String> list,
                                      ThreadLocalRandom rnd) {
        int sz = list.size();
        if (sz == 0) return null;
        try {
            return list.get(rnd.nextInt(sz));
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static String pickRandomAndRemove(java.util.concurrent.CopyOnWriteArrayList<String> list,
                                                ThreadLocalRandom rnd) {
        int sz = list.size();
        if (sz == 0) return null;
        try {
            int idx = rnd.nextInt(sz);
            return list.remove(idx);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private void startMetricsServer(PrometheusMeterRegistry registry) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(metricsPort), 0);
            server.createContext("/metrics", exchange -> {
                String body = registry.scrape();
                byte[] bytes = body.getBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            log.info("Metrics endpoint listening on :{}/metrics", metricsPort);
        } catch (Exception e) {
            log.warn("Failed to start metrics server: {}", e.getMessage());
        }
    }
}
