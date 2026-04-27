package com.example.objectstorage.api.client;

import com.example.objectstorage.api.Bucket;
import com.example.objectstorage.api.ListObjectsResult;
import com.example.objectstorage.api.MetaAddPartRequest;
import com.example.objectstorage.api.MetaCreateBucketRequest;
import com.example.objectstorage.api.MetaCreateMultipartRequest;
import com.example.objectstorage.api.MetaPutVersionRequest;
import com.example.objectstorage.api.MultipartPart;
import com.example.objectstorage.api.MultipartUpload;
import com.example.objectstorage.api.MultipartUploadDetail;
import com.example.objectstorage.api.ObjectVersionRow;
import com.example.objectstorage.core.http.RoundRobinClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MetadataClient {

    private final RoundRobinClient http;

    public MetadataClient(RoundRobinClient http) {
        this.http = http;
    }

    public Optional<Bucket> createBucket(MetaCreateBucketRequest req) {
        String base = http.nextBaseUrl();
        try {
            return Optional.ofNullable(http.client().post()
                    .uri(base + "/buckets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(Bucket.class));
        } catch (HttpClientErrorException.Conflict c) {
            return Optional.empty();
        }
    }

    public Optional<Bucket> getBucket(String name) {
        String base = http.nextBaseUrl();
        try {
            return Optional.ofNullable(http.client().get()
                    .uri(base + "/buckets/" + name)
                    .retrieve()
                    .body(Bucket.class));
        } catch (HttpClientErrorException.NotFound nf) {
            return Optional.empty();
        }
    }

    public boolean deleteBucket(String name) {
        String base = http.nextBaseUrl();
        try {
            http.client().delete()
                    .uri(base + "/buckets/" + name)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound nf) {
            return false;
        }
    }

    public void setVersioning(String bucket, boolean enabled) {
        String base = http.nextBaseUrl();
        http.client().put()
                .uri(base + "/buckets/" + bucket + "/versioning")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity();
    }

    public List<Bucket> listBuckets(String owner) {
        String base = http.nextBaseUrl();
        @SuppressWarnings("unchecked")
        List<Bucket> result = (List<Bucket>) http.client().get()
                .uri(base + "/buckets?owner=" + owner)
                .retrieve()
                .body(List.class);
        return result == null ? List.of() : result;
    }

    public ObjectVersionRow putVersion(MetaPutVersionRequest req) {
        String base = http.nextBaseUrl();
        return http.client().post()
                .uri(base + "/objects")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ObjectVersionRow.class);
    }

    public Optional<ObjectVersionRow> getCurrent(String bucket, String key) {
        String base = http.nextBaseUrl();
        try {
            return Optional.ofNullable(http.client().get()
                    .uri(uri -> uri.scheme("http")
                            .host(stripScheme(base))
                            .port(stripSchemePort(base))
                            .path("/objects/" + bucket + "/current")
                            .queryParam("key", key)
                            .build())
                    .retrieve()
                    .body(ObjectVersionRow.class));
        } catch (HttpClientErrorException.NotFound nf) {
            return Optional.empty();
        }
    }

    public Optional<ObjectVersionRow> getVersion(String bucket, String key, String versionId) {
        String base = http.nextBaseUrl();
        try {
            return Optional.ofNullable(http.client().get()
                    .uri(uri -> uri.scheme("http")
                            .host(stripScheme(base))
                            .port(stripSchemePort(base))
                            .path("/objects/" + bucket + "/version")
                            .queryParam("key", key)
                            .queryParam("versionId", versionId)
                            .build())
                    .retrieve()
                    .body(ObjectVersionRow.class));
        } catch (HttpClientErrorException.NotFound nf) {
            return Optional.empty();
        }
    }

    public boolean deleteVersion(String bucket, String key, String versionId) {
        String base = http.nextBaseUrl();
        try {
            http.client().delete()
                    .uri(uri -> uri.scheme("http")
                            .host(stripScheme(base))
                            .port(stripSchemePort(base))
                            .path("/objects/" + bucket + "/version")
                            .queryParam("key", key)
                            .queryParam("versionId", versionId)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound nf) {
            return false;
        }
    }

    public ListObjectsResult listObjects(String bucket, String prefix, String startAfter, int limit) {
        String base = http.nextBaseUrl();
        return http.client().get()
                .uri(uri -> uri.scheme("http")
                        .host(stripScheme(base))
                        .port(stripSchemePort(base))
                        .path("/objects/" + bucket)
                        .queryParam("prefix", prefix == null ? "" : prefix)
                        .queryParam("startAfter", startAfter == null ? "" : startAfter)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(ListObjectsResult.class);
    }

    public MultipartUpload createMultipart(MetaCreateMultipartRequest req) {
        String base = http.nextBaseUrl();
        return http.client().post()
                .uri(base + "/multipart")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(MultipartUpload.class);
    }

    public MultipartPart addPart(MetaAddPartRequest req) {
        String base = http.nextBaseUrl();
        return http.client().post()
                .uri(base + "/multipart/" + req.uploadId() + "/parts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(MultipartPart.class);
    }

    public Optional<MultipartUploadDetail> getMultipart(UUID uploadId) {
        String base = http.nextBaseUrl();
        try {
            return Optional.ofNullable(http.client().get()
                    .uri(base + "/multipart/" + uploadId)
                    .retrieve()
                    .body(MultipartUploadDetail.class));
        } catch (HttpClientErrorException.NotFound nf) {
            return Optional.empty();
        }
    }

    public boolean deleteMultipart(UUID uploadId) {
        String base = http.nextBaseUrl();
        try {
            http.client().delete()
                    .uri(base + "/multipart/" + uploadId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound nf) {
            return false;
        }
    }

    private static String stripScheme(String url) {
        int idx = url.indexOf("://");
        String afterScheme = idx >= 0 ? url.substring(idx + 3) : url;
        int colon = afterScheme.indexOf(':');
        return colon >= 0 ? afterScheme.substring(0, colon) : afterScheme;
    }

    private static int stripSchemePort(String url) {
        int idx = url.indexOf("://");
        String afterScheme = idx >= 0 ? url.substring(idx + 3) : url;
        int colon = afterScheme.indexOf(':');
        if (colon < 0) return 80;
        int slash = afterScheme.indexOf('/', colon);
        String portStr = slash > 0 ? afterScheme.substring(colon + 1, slash) : afterScheme.substring(colon + 1);
        return Integer.parseInt(portStr);
    }
}
