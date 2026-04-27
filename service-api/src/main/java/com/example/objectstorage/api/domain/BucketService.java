package com.example.objectstorage.api.domain;

import com.example.objectstorage.api.Bucket;
import com.example.objectstorage.api.BucketAcl;
import com.example.objectstorage.api.MetaCreateBucketRequest;
import com.example.objectstorage.api.Permission;
import com.example.objectstorage.api.client.IamClient;
import com.example.objectstorage.api.client.MetadataClient;
import com.example.objectstorage.api.security.AclChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BucketService {

    private final MetadataClient meta;
    private final IamClient iam;
    private final AclChecker acl;

    public BucketService(MetadataClient meta, IamClient iam, AclChecker acl) {
        this.meta = meta;
        this.iam = iam;
        this.acl = acl;
    }

    public Bucket createBucket(String name, String principal, boolean versioning) {
        validateBucketName(name);
        Bucket b = meta.createBucket(new MetaCreateBucketRequest(name, principal, versioning))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "BucketAlreadyExists", "bucket exists: " + name));
        iam.grant(new BucketAcl(name, principal, Permission.OWNER));
        iam.grant(new BucketAcl(name, principal, Permission.READ));
        iam.grant(new BucketAcl(name, principal, Permission.WRITE));
        return b;
    }

    public Bucket getBucket(String name, String principal) {
        ensureRead(name, principal);
        return meta.getBucket(name)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "NoSuchBucket", "no such bucket: " + name));
    }

    public void deleteBucket(String name, String principal) {
        ensureOwner(name, principal);
        if (!meta.deleteBucket(name)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NoSuchBucket", "no such bucket: " + name);
        }
        iam.deleteAclsForBucket(name);
        acl.invalidateBucket(name);
    }

    public void setVersioning(String name, boolean enabled, String principal) {
        ensureOwner(name, principal);
        meta.getBucket(name).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NoSuchBucket", "no such bucket: " + name));
        meta.setVersioning(name, enabled);
    }

    public List<Bucket> listForOwner(String principal) {
        return meta.listBuckets(principal);
    }

    private void ensureRead(String bucket, String principal) {
        if (!acl.hasPermission(bucket, principal, Permission.READ)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AccessDenied", "no READ on " + bucket);
        }
    }

    private void ensureOwner(String bucket, String principal) {
        if (!acl.hasPermission(bucket, principal, Permission.OWNER)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AccessDenied", "no OWNER on " + bucket);
        }
    }

    private static void validateBucketName(String name) {
        if (name == null || name.length() < 3 || name.length() > 63
                || !name.matches("[a-z0-9][a-z0-9-]*[a-z0-9]")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "InvalidBucketName",
                    "bucket name must be 3-63 lowercase alphanumeric/dash chars");
        }
    }
}
