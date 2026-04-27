package com.example.objectstorage.iam.persistence;

import com.example.objectstorage.api.BucketAcl;
import com.example.objectstorage.api.Permission;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BucketAclRepository {

    private final JdbcTemplate jdbc;

    public BucketAclRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasPermission(String bucket, String principal, Permission required) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM bucket_acls
                WHERE bucket_name = ? AND principal_id = ?
                  AND permission IN (?, 'OWNER')
                """, Integer.class, bucket, principal, required.name());
        return count != null && count > 0;
    }

    public void grant(BucketAcl acl) {
        jdbc.update("""
                INSERT INTO bucket_acls (bucket_name, principal_id, permission)
                VALUES (?, ?, ?)
                ON CONFLICT (bucket_name, principal_id, permission) DO NOTHING
                """, acl.bucketName(), acl.principalId(), acl.permission().name());
    }

    public void revokeAll(String bucket, String principal) {
        jdbc.update("DELETE FROM bucket_acls WHERE bucket_name = ? AND principal_id = ?",
                bucket, principal);
    }

    public void deleteAllForBucket(String bucket) {
        jdbc.update("DELETE FROM bucket_acls WHERE bucket_name = ?", bucket);
    }
}
