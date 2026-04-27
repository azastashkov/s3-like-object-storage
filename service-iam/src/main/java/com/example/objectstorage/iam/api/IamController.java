package com.example.objectstorage.iam.api;

import com.example.objectstorage.api.ApiError;
import com.example.objectstorage.api.BucketAcl;
import com.example.objectstorage.api.IamAclCheckResponse;
import com.example.objectstorage.api.IamLoginRequest;
import com.example.objectstorage.api.IamLoginResponse;
import com.example.objectstorage.api.Permission;
import com.example.objectstorage.api.Principal;
import com.example.objectstorage.iam.domain.IamService;
import com.example.objectstorage.iam.domain.InvalidCredentialsException;
import com.example.objectstorage.iam.domain.PrincipalNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class IamController {

    private final IamService svc;

    public IamController(IamService svc) {
        this.svc = svc;
    }

    @PostMapping("/auth/login")
    public IamLoginResponse login(@RequestBody IamLoginRequest req) {
        return svc.login(req);
    }

    @GetMapping("/auth/principal/{id}")
    public Principal getPrincipal(@PathVariable String id) {
        return svc.getPrincipal(id);
    }

    @GetMapping("/acl/{bucket}/{principal}/{permission}")
    public IamAclCheckResponse hasPermission(
            @PathVariable String bucket,
            @PathVariable String principal,
            @PathVariable Permission permission) {
        return new IamAclCheckResponse(svc.hasPermission(bucket, principal, permission));
    }

    @PostMapping("/acl")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public void grant(@RequestBody BucketAcl acl) {
        svc.grant(acl);
    }

    @DeleteMapping("/acl/bucket/{bucket}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllForBucket(@PathVariable String bucket) {
        svc.deleteBucketAcls(bucket);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> invalid(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("InvalidCredentials", e.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(PrincipalNotFoundException.class)
    public ResponseEntity<ApiError> notFound(PrincipalNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("PrincipalNotFound", e.getMessage(), UUID.randomUUID().toString()));
    }
}
