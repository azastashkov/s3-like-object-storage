package com.example.objectstorage.api.domain;

import com.example.objectstorage.api.AssembleRequest;
import com.example.objectstorage.api.Bucket;
import com.example.objectstorage.api.DataNodePutResult;
import com.example.objectstorage.api.ListObjectsResult;
import com.example.objectstorage.api.MetaAddPartRequest;
import com.example.objectstorage.api.MetaCreateMultipartRequest;
import com.example.objectstorage.api.MetaPutVersionRequest;
import com.example.objectstorage.api.MultipartPart;
import com.example.objectstorage.api.MultipartUpload;
import com.example.objectstorage.api.MultipartUploadDetail;
import com.example.objectstorage.api.ObjectVersionRow;
import com.example.objectstorage.api.Permission;
import com.example.objectstorage.api.PlacementDecision;
import com.example.objectstorage.api.client.DataRoutingClient;
import com.example.objectstorage.api.client.MetadataClient;
import com.example.objectstorage.api.client.PlacementApiClient;
import com.example.objectstorage.api.security.AclChecker;
import com.example.objectstorage.core.id.UlidFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ObjectService {

    private final MetadataClient meta;
    private final PlacementApiClient placement;
    private final DataRoutingClient routing;
    private final AclChecker acl;
    private final UlidFactory ulid = new UlidFactory();

    private final Counter putCounter;
    private final Counter getCounter;
    private final Counter deleteCounter;
    private final Timer putTimer;
    private final Timer getTimer;
    private final AtomicLong bytesIn = new AtomicLong();
    private final AtomicLong bytesOut = new AtomicLong();

    public ObjectService(MetadataClient meta, PlacementApiClient placement,
                         DataRoutingClient routing, AclChecker acl, MeterRegistry meters) {
        this.meta = meta;
        this.placement = placement;
        this.routing = routing;
        this.acl = acl;
        this.putCounter = meters.counter("api.objects", "op", "put");
        this.getCounter = meters.counter("api.objects", "op", "get");
        this.deleteCounter = meters.counter("api.objects", "op", "delete");
        this.putTimer = Timer.builder("api.objects.duration")
                .tag("op", "put").publishPercentileHistogram().register(meters);
        this.getTimer = Timer.builder("api.objects.duration")
                .tag("op", "get").publishPercentileHistogram().register(meters);
        meters.gauge("api.bytes.in", bytesIn);
        meters.gauge("api.bytes.out", bytesOut);
    }

    public PutResult putObject(String bucket, String key, byte[] payload,
                                String contentType, Map<String, String> userMeta,
                                String principal) {
        return putTimer.record(() -> {
            requireWrite(bucket, principal);
            Bucket b = requireBucket(bucket);

            UUID objectId = UUID.randomUUID();
            String versionId = ulid.next();

            PlacementDecision pd = placement.create(objectId);
            DataNodePutResult dr = routing.put(objectId, payload, pd);

            String etag = dr.etag();
            ObjectVersionRow row = meta.putVersion(new MetaPutVersionRequest(
                    bucket, key, versionId, objectId, false,
                    payload.length, contentType, etag, userMeta));
            putCounter.increment();
            bytesIn.addAndGet(payload.length);
            return new PutResult(row, b.versioningEnabled() ? versionId : null);
        });
    }

    public GetResult getObject(String bucket, String key, String versionId, String principal) {
        return getTimer.record(() -> {
            requireRead(bucket, principal);
            ObjectVersionRow row;
            if (versionId == null) {
                row = meta.getCurrent(bucket, key)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NoSuchKey", "no such key"));
                if (row.isDeleteMarker()) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "NoSuchKey", "delete marker");
                }
            } else {
                row = meta.getVersion(bucket, key, versionId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NoSuchVersion", "no such version"));
                if (row.isDeleteMarker()) {
                    throw new ApiException(HttpStatus.METHOD_NOT_ALLOWED, "MethodNotAllowed", "version is delete marker");
                }
            }
            byte[] data = routing.get(row.objectId());
            if (data == null) {
                throw new ApiException(HttpStatus.GONE, "ObjectGone", "data missing in routing");
            }
            getCounter.increment();
            bytesOut.addAndGet(data.length);
            return new GetResult(row, data);
        });
    }

    public ObjectVersionRow headObject(String bucket, String key, String versionId, String principal) {
        requireRead(bucket, principal);
        ObjectVersionRow row;
        if (versionId == null) {
            row = meta.getCurrent(bucket, key)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NoSuchKey", "no such key"));
            if (row.isDeleteMarker()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NoSuchKey", "delete marker");
            }
        } else {
            row = meta.getVersion(bucket, key, versionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NoSuchVersion", "no such version"));
        }
        return row;
    }

    public DeleteResult deleteObject(String bucket, String key, String versionId, String principal) {
        requireWrite(bucket, principal);
        if (versionId != null) {
            // hard delete a specific version
            Optional<ObjectVersionRow> row = meta.getVersion(bucket, key, versionId);
            if (row.isEmpty()) return new DeleteResult(null, true);
            meta.deleteVersion(bucket, key, versionId);
            if (row.get().objectId() != null && !row.get().isDeleteMarker()) {
                routing.delete(row.get().objectId());
            }
            deleteCounter.increment();
            return new DeleteResult(versionId, false);
        }
        // soft delete = insert delete marker
        String marker = ulid.next();
        ObjectVersionRow row = meta.putVersion(new MetaPutVersionRequest(
                bucket, key, marker, null, true, 0, null, null, null));
        deleteCounter.increment();
        return new DeleteResult(marker, true);
    }

    public ListObjectsResult listObjects(String bucket, String prefix, String startAfter, int limit, String principal) {
        requireRead(bucket, principal);
        return meta.listObjects(bucket, prefix, startAfter, limit);
    }

    // Multipart

    public MultipartUpload initiateMultipart(String bucket, String key, String contentType,
                                              Map<String, String> userMeta, String principal) {
        requireWrite(bucket, principal);
        requireBucket(bucket);
        UUID uploadId = UUID.randomUUID();
        Map<String, String> meta = new HashMap<>(userMeta == null ? Map.of() : userMeta);
        if (contentType != null) meta.put("__contentType", contentType);
        return this.meta.createMultipart(new MetaCreateMultipartRequest(uploadId, bucket, key, principal, meta));
    }

    public MultipartPart uploadPart(UUID uploadId, int partNumber, byte[] payload, String principal) {
        MultipartUpload mu = meta.getMultipart(uploadId)
                .map(MultipartUploadDetail::upload)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NoSuchUpload", "no such upload"));
        requireWrite(mu.bucketName(), principal);

        UUID partObjectId = UUID.randomUUID();
        PlacementDecision pd = placement.create(partObjectId);
        DataNodePutResult dr = routing.put(partObjectId, payload, pd);
        return meta.addPart(new MetaAddPartRequest(uploadId, partNumber, partObjectId, payload.length, dr.etag()));
    }

    public PutResult completeMultipart(UUID uploadId, String principal, List<Integer> partNumbersInOrder) {
        MultipartUploadDetail detail = meta.getMultipart(uploadId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NoSuchUpload", "no such upload"));
        requireWrite(detail.upload().bucketName(), principal);
        Bucket b = requireBucket(detail.upload().bucketName());

        Map<Integer, MultipartPart> byNum = new HashMap<>();
        for (MultipartPart p : detail.parts()) byNum.put(p.partNumber(), p);

        List<UUID> ordered = new java.util.ArrayList<>(partNumbersInOrder.size());
        long totalSize = 0;
        for (Integer n : partNumbersInOrder) {
            MultipartPart p = byNum.get(n);
            if (p == null) throw new ApiException(HttpStatus.BAD_REQUEST, "InvalidPart", "missing part " + n);
            ordered.add(p.partObjectId());
            totalSize += p.sizeBytes();
        }

        UUID finalObjectId = UUID.randomUUID();
        PlacementDecision pd = placement.create(finalObjectId);
        DataNodePutResult dr = routing.assemble(new AssembleRequest(
                finalObjectId, ordered, pd.replicaNodes(), pd.primaryNode()));

        String versionId = ulid.next();
        String contentType = detail.upload().userMetadata() == null ? null
                : detail.upload().userMetadata().get("__contentType");
        Map<String, String> userMeta = detail.upload().userMetadata() == null ? Map.of()
                : detail.upload().userMetadata().entrySet().stream()
                    .filter(e -> !e.getKey().startsWith("__"))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        ObjectVersionRow row = meta.putVersion(new MetaPutVersionRequest(
                detail.upload().bucketName(), detail.upload().objectKey(), versionId,
                finalObjectId, false, totalSize, contentType, dr.etag() + "-" + ordered.size(),
                userMeta));

        // Tombstone the part objects
        for (UUID partOid : ordered) routing.delete(partOid);
        meta.deleteMultipart(uploadId);

        bytesIn.addAndGet(totalSize);
        return new PutResult(row, b.versioningEnabled() ? versionId : null);
    }

    public boolean abortMultipart(UUID uploadId, String principal) {
        Optional<MultipartUploadDetail> detail = meta.getMultipart(uploadId);
        if (detail.isEmpty()) return false;
        requireWrite(detail.get().upload().bucketName(), principal);
        for (MultipartPart p : detail.get().parts()) {
            routing.delete(p.partObjectId());
        }
        meta.deleteMultipart(uploadId);
        return true;
    }

    private void requireWrite(String bucket, String principal) {
        if (!acl.hasPermission(bucket, principal, Permission.WRITE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AccessDenied", "no WRITE on " + bucket);
        }
    }

    private void requireRead(String bucket, String principal) {
        if (!acl.hasPermission(bucket, principal, Permission.READ)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AccessDenied", "no READ on " + bucket);
        }
    }

    private Bucket requireBucket(String name) {
        return meta.getBucket(name)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NoSuchBucket", "no such bucket: " + name));
    }

    public record PutResult(ObjectVersionRow row, String versionId) {}
    public record GetResult(ObjectVersionRow row, byte[] data) {}
    public record DeleteResult(String versionId, boolean isMarker) {}
}
