package com.example.objectstorage.api.api;

import com.example.objectstorage.api.Bucket;
import com.example.objectstorage.api.domain.BucketService;
import com.example.objectstorage.api.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class BucketController {

    private final BucketService svc;

    public BucketController(BucketService svc) {
        this.svc = svc;
    }

    @PutMapping("/{bucket}")
    public ResponseEntity<Bucket> create(@PathVariable String bucket,
                                          @RequestParam(required = false) String versioning,
                                          HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        if (versioning != null) {
            svc.setVersioning(bucket, true, principal);
            return ResponseEntity.noContent().build();
        }
        Bucket b = svc.createBucket(bucket, principal, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(b);
    }

    @GetMapping("/{bucket}")
    public ResponseEntity<?> get(@PathVariable String bucket,
                                  @RequestParam(required = false) String versioning,
                                  @RequestParam(name = "list-type", required = false) String listType,
                                  @RequestParam(required = false, defaultValue = "") String prefix,
                                  @RequestParam(name = "continuation-token", required = false) String continuationToken,
                                  @RequestParam(name = "max-keys", required = false, defaultValue = "1000") int maxKeys,
                                  HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        if (versioning != null) {
            Bucket b = svc.getBucket(bucket, principal);
            return ResponseEntity.ok(Map.of("status", b.versioningEnabled() ? "Enabled" : "Suspended"));
        }
        if ("2".equals(listType)) {
            String startAfter = continuationToken == null ? "" : continuationToken;
            return ResponseEntity.ok(((com.example.objectstorage.api.domain.ObjectService)
                    org.springframework.web.context.support.WebApplicationContextUtils
                            .getRequiredWebApplicationContext(req.getServletContext())
                            .getBean(com.example.objectstorage.api.domain.ObjectService.class))
                    .listObjects(bucket, prefix, startAfter, maxKeys, principal));
        }
        return ResponseEntity.ok(svc.getBucket(bucket, principal));
    }

    @DeleteMapping("/{bucket}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String bucket, HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        svc.deleteBucket(bucket, principal);
    }

    @GetMapping("/")
    public List<Bucket> list(HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        return svc.listForOwner(principal);
    }
}
