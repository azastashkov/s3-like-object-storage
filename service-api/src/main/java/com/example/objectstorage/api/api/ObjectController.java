package com.example.objectstorage.api.api;

import com.example.objectstorage.api.MultipartPart;
import com.example.objectstorage.api.MultipartUpload;
import com.example.objectstorage.api.ObjectVersionRow;
import com.example.objectstorage.api.domain.ObjectService;
import com.example.objectstorage.api.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class ObjectController {

    private final ObjectService svc;

    public ObjectController(ObjectService svc) {
        this.svc = svc;
    }

    /* -------------- single PUT (no uploadId, no partNumber) -------------- */

    @PutMapping(value = "/{bucket}/{*key}", params = {"!uploads", "!uploadId", "!partNumber"})
    public ResponseEntity<Void> put(@PathVariable String bucket,
                                     @PathVariable String key,
                                     @RequestHeader(value = "Content-Type", required = false) String ct,
                                     @RequestBody byte[] body,
                                     HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        Map<String, String> meta = extractUserMetadata(req);
        var result = svc.putObject(bucket, normalizeKey(key), body, ct, meta, principal);
        HttpHeaders h = new HttpHeaders();
        h.setETag("\"" + result.row().etag() + "\"");
        if (result.versionId() != null) h.set("x-amz-version-id", result.versionId());
        return ResponseEntity.ok().headers(h).build();
    }

    /* -------------- initiate multipart -------------- */

    @PostMapping(value = "/{bucket}/{*key}", params = "uploads")
    public ResponseEntity<Map<String, Object>> initiateMultipart(@PathVariable String bucket,
                                                                  @PathVariable String key,
                                                                  @RequestHeader(value = "Content-Type", required = false) String ct,
                                                                  HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        Map<String, String> meta = extractUserMetadata(req);
        MultipartUpload mu = svc.initiateMultipart(bucket, normalizeKey(key), ct, meta, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "Bucket", mu.bucketName(),
                "Key", mu.objectKey(),
                "UploadId", mu.uploadId().toString()
        ));
    }

    /* -------------- upload part -------------- */

    @PutMapping(value = "/{bucket}/{*key}", params = {"uploadId", "partNumber"})
    public ResponseEntity<Void> uploadPart(@PathVariable String bucket,
                                            @PathVariable String key,
                                            @RequestParam UUID uploadId,
                                            @RequestParam int partNumber,
                                            @RequestBody byte[] body,
                                            HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        MultipartPart p = svc.uploadPart(uploadId, partNumber, body, principal);
        HttpHeaders h = new HttpHeaders();
        h.setETag("\"" + p.etag() + "\"");
        return ResponseEntity.ok().headers(h).build();
    }

    /* -------------- complete multipart -------------- */

    @PostMapping(value = "/{bucket}/{*key}", params = "uploadId")
    public ResponseEntity<Map<String, Object>> completeMultipart(@PathVariable String bucket,
                                                                  @PathVariable String key,
                                                                  @RequestParam UUID uploadId,
                                                                  @RequestBody(required = false) Map<String, Object> body,
                                                                  HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        List<Integer> partNumbers;
        if (body != null && body.get("Parts") instanceof List<?> l) {
            partNumbers = l.stream()
                    .filter(o -> o instanceof Map<?, ?>)
                    .map(o -> ((Map<?, ?>) o).get("PartNumber"))
                    .map(o -> ((Number) o).intValue())
                    .collect(Collectors.toList());
        } else {
            // default: assume parts are in 1..N order
            var detail = ((com.example.objectstorage.api.client.MetadataClient)
                    org.springframework.web.context.support.WebApplicationContextUtils
                            .getRequiredWebApplicationContext(req.getServletContext())
                            .getBean(com.example.objectstorage.api.client.MetadataClient.class))
                    .getMultipart(uploadId)
                    .orElseThrow();
            partNumbers = detail.parts().stream().map(MultipartPart::partNumber).sorted().toList();
        }
        var result = svc.completeMultipart(uploadId, principal, partNumbers);
        HttpHeaders h = new HttpHeaders();
        h.setETag("\"" + result.row().etag() + "\"");
        if (result.versionId() != null) h.set("x-amz-version-id", result.versionId());
        return ResponseEntity.ok().headers(h).body(Map.of(
                "Bucket", bucket,
                "Key", normalizeKey(key),
                "ETag", result.row().etag(),
                "Location", "/" + bucket + "/" + normalizeKey(key)
        ));
    }

    /* -------------- abort multipart -------------- */

    @DeleteMapping(value = "/{bucket}/{*key}", params = "uploadId")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abortMultipart(@PathVariable String bucket,
                                @PathVariable String key,
                                @RequestParam UUID uploadId,
                                HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        svc.abortMultipart(uploadId, principal);
    }

    /* -------------- GET -------------- */

    @GetMapping(value = "/{bucket}/{*key}", params = {"!uploads", "!uploadId"})
    public ResponseEntity<byte[]> get(@PathVariable String bucket,
                                       @PathVariable String key,
                                       @RequestParam(required = false) String versionId,
                                       HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        var result = svc.getObject(bucket, normalizeKey(key), versionId, principal);
        HttpHeaders h = headersFromRow(result.row());
        return ResponseEntity.ok()
                .headers(h)
                .contentType(result.row().contentType() != null
                        ? MediaType.parseMediaType(result.row().contentType())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .body(result.data());
    }

    /* -------------- HEAD -------------- */

    @RequestMapping(value = "/{bucket}/{*key}", method = RequestMethod.HEAD,
            params = {"!uploads", "!uploadId"})
    public ResponseEntity<Void> head(@PathVariable String bucket,
                                      @PathVariable String key,
                                      @RequestParam(required = false) String versionId,
                                      HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        ObjectVersionRow row = svc.headObject(bucket, normalizeKey(key), versionId, principal);
        return ResponseEntity.ok().headers(headersFromRow(row)).build();
    }

    /* -------------- DELETE -------------- */

    @DeleteMapping(value = "/{bucket}/{*key}", params = {"!uploads", "!uploadId"})
    public ResponseEntity<Void> delete(@PathVariable String bucket,
                                        @PathVariable String key,
                                        @RequestParam(required = false) String versionId,
                                        HttpServletRequest req) {
        String principal = (String) req.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        var result = svc.deleteObject(bucket, normalizeKey(key), versionId, principal);
        HttpHeaders h = new HttpHeaders();
        if (result.versionId() != null) h.set("x-amz-version-id", result.versionId());
        if (result.isMarker()) h.set("x-amz-delete-marker", "true");
        return ResponseEntity.noContent().headers(h).build();
    }

    private static String normalizeKey(String key) {
        if (key == null) return "";
        return key.startsWith("/") ? key.substring(1) : key;
    }

    private static Map<String, String> extractUserMetadata(HttpServletRequest req) {
        Map<String, String> meta = new HashMap<>();
        var headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name.toLowerCase().startsWith("x-amz-meta-")) {
                meta.put(name.substring("x-amz-meta-".length()).toLowerCase(), req.getHeader(name));
            }
        }
        return meta;
    }

    private static HttpHeaders headersFromRow(ObjectVersionRow row) {
        HttpHeaders h = new HttpHeaders();
        if (row.etag() != null) h.setETag("\"" + row.etag() + "\"");
        h.setContentLength(row.sizeBytes());
        if (row.contentType() != null) h.set("Content-Type", row.contentType());
        h.set("x-amz-version-id", row.versionId());
        h.set("Last-Modified", row.createdAt().toString());
        if (row.userMetadata() != null) {
            row.userMetadata().forEach((k, v) -> h.set("x-amz-meta-" + k, v));
        }
        return h;
    }
}
