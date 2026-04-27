package com.example.objectstorage.metadata.api;

import com.example.objectstorage.api.Bucket;
import com.example.objectstorage.api.MetaCreateBucketRequest;
import com.example.objectstorage.metadata.persistence.BucketRepository;
import com.example.objectstorage.metadata.persistence.ObjectVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/buckets")
public class BucketController {

    private final BucketRepository buckets;
    private final ObjectVersionRepository objects;

    public BucketController(BucketRepository buckets, ObjectVersionRepository objects) {
        this.buckets = buckets;
        this.objects = objects;
    }

    @PostMapping
    public ResponseEntity<Bucket> create(@RequestBody MetaCreateBucketRequest req) {
        boolean created = buckets.create(req.name(), req.ownerPrincipal(), req.versioningEnabled());
        if (!created) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buckets.findByName(req.name()).orElseThrow());
    }

    @GetMapping("/{name}")
    public ResponseEntity<Bucket> get(@PathVariable String name) {
        return buckets.findByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        if (buckets.findByName(name).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (objects.countObjectsInBucket(name) > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        buckets.delete(name);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{name}/versioning")
    public ResponseEntity<Void> setVersioning(@PathVariable String name, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) return ResponseEntity.badRequest().build();
        if (buckets.findByName(name).isEmpty()) return ResponseEntity.notFound().build();
        buckets.setVersioning(name, enabled);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<Bucket> listForOwner(@RequestParam String owner) {
        return buckets.listForOwner(owner);
    }
}
