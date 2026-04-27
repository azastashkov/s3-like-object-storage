package com.example.objectstorage.metadata.api;

import com.example.objectstorage.api.MetaAddPartRequest;
import com.example.objectstorage.api.MetaCreateMultipartRequest;
import com.example.objectstorage.api.MultipartPart;
import com.example.objectstorage.api.MultipartUpload;
import com.example.objectstorage.api.MultipartUploadDetail;
import com.example.objectstorage.metadata.persistence.MultipartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/multipart")
public class MultipartController {

    private final MultipartRepository repo;

    public MultipartController(MultipartRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<MultipartUpload> create(@RequestBody MetaCreateMultipartRequest req) {
        var up = new MultipartUpload(
                req.uploadId(), req.bucketName(), req.objectKey(),
                req.initiator(), req.userMetadata(), Instant.now()
        );
        repo.create(up);
        return ResponseEntity.status(HttpStatus.CREATED).body(up);
    }

    @PostMapping("/{uploadId}/parts")
    public ResponseEntity<MultipartPart> addPart(@PathVariable UUID uploadId,
                                                  @RequestBody MetaAddPartRequest req) {
        if (!uploadId.equals(req.uploadId())) {
            return ResponseEntity.badRequest().build();
        }
        var part = new MultipartPart(
                req.uploadId(), req.partNumber(), req.partObjectId(),
                req.sizeBytes(), req.etag(), Instant.now()
        );
        repo.addPart(part);
        return ResponseEntity.status(HttpStatus.CREATED).body(part);
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<MultipartUploadDetail> get(@PathVariable UUID uploadId) {
        return repo.findById(uploadId)
                .map(up -> ResponseEntity.ok(new MultipartUploadDetail(up, repo.listParts(uploadId))))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> delete(@PathVariable UUID uploadId) {
        return repo.delete(uploadId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
