package com.example.objectstorage.metadata.api;

import com.example.objectstorage.api.ListObjectsResult;
import com.example.objectstorage.api.MetaPutVersionRequest;
import com.example.objectstorage.api.ObjectVersionRow;
import com.example.objectstorage.metadata.domain.ObjectVersionRoutedRepository;
import com.example.objectstorage.metadata.persistence.ObjectVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/objects")
public class ObjectVersionController {

    private final ObjectVersionRoutedRepository routed;
    private final ObjectVersionRepository repo;

    public ObjectVersionController(ObjectVersionRoutedRepository routed, ObjectVersionRepository repo) {
        this.routed = routed;
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<ObjectVersionRow> put(@RequestBody MetaPutVersionRequest req) {
        var row = new ObjectVersionRow(
                req.bucketName(), req.objectKey(), req.versionId(),
                req.objectId(), req.isDeleteMarker(), req.sizeBytes(),
                req.contentType(), req.etag(), req.userMetadata(),
                Instant.now()
        );
        routed.insert(row);
        return ResponseEntity.status(HttpStatus.CREATED).body(row);
    }

    @GetMapping("/{bucket}/current")
    public ResponseEntity<ObjectVersionRow> current(@PathVariable String bucket, @RequestParam String key) {
        return routed.findCurrent(bucket, key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{bucket}/version")
    public ResponseEntity<ObjectVersionRow> version(@PathVariable String bucket,
                                                     @RequestParam String key,
                                                     @RequestParam String versionId) {
        return routed.findVersion(bucket, key, versionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{bucket}/version")
    public ResponseEntity<Void> deleteVersion(@PathVariable String bucket,
                                               @RequestParam String key,
                                               @RequestParam String versionId) {
        return routed.deleteVersion(bucket, key, versionId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/{bucket}")
    public ListObjectsResult list(@PathVariable String bucket,
                                   @RequestParam(required = false, defaultValue = "") String prefix,
                                   @RequestParam(required = false, defaultValue = "") String startAfter,
                                   @RequestParam(required = false, defaultValue = "1000") int limit) {
        if (limit > 1000) limit = 1000;
        List<ObjectVersionRow> rows = repo.listCurrentByPrefix(bucket, prefix, startAfter, limit);
        boolean truncated = rows.size() == limit;
        String next = truncated ? rows.get(rows.size() - 1).objectKey() : null;
        return new ListObjectsResult(bucket, prefix, rows.size(), truncated, next, rows);
    }
}
