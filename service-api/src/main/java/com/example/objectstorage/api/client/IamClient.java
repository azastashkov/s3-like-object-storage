package com.example.objectstorage.api.client;

import com.example.objectstorage.api.BucketAcl;
import com.example.objectstorage.api.IamAclCheckResponse;
import com.example.objectstorage.api.Permission;
import com.example.objectstorage.core.http.RoundRobinClient;

public class IamClient {

    private final RoundRobinClient http;

    public IamClient(RoundRobinClient http) {
        this.http = http;
    }

    public boolean hasPermission(String bucket, String principal, Permission p) {
        String base = http.nextBaseUrl();
        IamAclCheckResponse resp = http.client().get()
                .uri(base + "/acl/" + bucket + "/" + principal + "/" + p.name())
                .retrieve()
                .body(IamAclCheckResponse.class);
        return resp != null && resp.allowed();
    }

    public void grant(BucketAcl acl) {
        String base = http.nextBaseUrl();
        http.client().post()
                .uri(base + "/acl")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(acl)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteAclsForBucket(String bucket) {
        String base = http.nextBaseUrl();
        http.client().delete()
                .uri(base + "/acl/bucket/" + bucket)
                .retrieve()
                .toBodilessEntity();
    }
}
