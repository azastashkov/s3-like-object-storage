package com.example.objectstorage.metadata.domain;

import com.example.objectstorage.api.ObjectVersionRow;
import com.example.objectstorage.core.sharding.ShardRouter;
import com.example.objectstorage.metadata.persistence.ObjectVersionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ObjectVersionRoutedRepository {

    private final ObjectVersionRepository repo;
    private final ShardRouter router;

    public ObjectVersionRoutedRepository(ObjectVersionRepository repo, ShardRouter router) {
        this.repo = repo;
        this.router = router;
    }

    public void insert(ObjectVersionRow row) {
        repo.insert(router.shardOf(row.bucketName(), row.objectKey()), row);
    }

    public Optional<ObjectVersionRow> findCurrent(String bucket, String key) {
        return repo.findCurrent(router.shardOf(bucket, key), bucket, key);
    }

    public Optional<ObjectVersionRow> findVersion(String bucket, String key, String versionId) {
        return repo.findVersion(router.shardOf(bucket, key), bucket, key, versionId);
    }

    public boolean deleteVersion(String bucket, String key, String versionId) {
        return repo.deleteVersion(router.shardOf(bucket, key), bucket, key, versionId);
    }
}
