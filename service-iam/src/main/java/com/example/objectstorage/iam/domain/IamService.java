package com.example.objectstorage.iam.domain;

import com.example.objectstorage.api.BucketAcl;
import com.example.objectstorage.api.IamLoginRequest;
import com.example.objectstorage.api.IamLoginResponse;
import com.example.objectstorage.api.Permission;
import com.example.objectstorage.api.Principal;
import com.example.objectstorage.core.auth.JwtIssuer;
import com.example.objectstorage.iam.persistence.BucketAclRepository;
import com.example.objectstorage.iam.persistence.PrincipalRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class IamService {

    private final PrincipalRepository principals;
    private final BucketAclRepository acls;
    private final JwtIssuer jwt;

    public IamService(PrincipalRepository principals, BucketAclRepository acls, JwtIssuer jwt) {
        this.principals = principals;
        this.acls = acls;
        this.jwt = jwt;
    }

    public IamLoginResponse login(IamLoginRequest req) {
        if (req == null || req.principalId() == null || req.apiKey() == null) {
            throw new InvalidCredentialsException("missing principal or apiKey");
        }
        String hash = sha256Hex(req.apiKey());
        Principal p = principals.findByApiKeyHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("unknown key"));
        if (!p.principalId().equals(req.principalId())) {
            throw new InvalidCredentialsException("principal mismatch");
        }
        String token = jwt.issue(p.principalId(), List.of("user"));
        return new IamLoginResponse(token, jwt.ttlSeconds(), p.principalId());
    }

    public Principal getPrincipal(String id) {
        return principals.findById(id)
                .orElseThrow(() -> new PrincipalNotFoundException(id));
    }

    public boolean hasPermission(String bucket, String principal, Permission p) {
        return acls.hasPermission(bucket, principal, p);
    }

    public void grant(BucketAcl acl) {
        acls.grant(acl);
    }

    public void deleteBucketAcls(String bucket) {
        acls.deleteAllForBucket(bucket);
    }

    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
