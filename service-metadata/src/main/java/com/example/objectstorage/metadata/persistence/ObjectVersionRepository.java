package com.example.objectstorage.metadata.persistence;

import com.example.objectstorage.api.ObjectVersionRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ObjectVersionRepository {

    private final ShardedDataSourceProvider shards;
    private final ObjectMapper json = new ObjectMapper();

    public ObjectVersionRepository(ShardedDataSourceProvider shards) {
        this.shards = shards;
    }

    private final RowMapper<ObjectVersionRow> ROW = (rs, n) -> {
        Map<String, String> meta = parseMeta(rs.getString("user_metadata"));
        UUID oid = (UUID) rs.getObject("object_id");
        return new ObjectVersionRow(
                rs.getString("bucket_name"),
                rs.getString("object_key"),
                rs.getString("version_id"),
                oid,
                rs.getBoolean("is_delete_marker"),
                rs.getLong("size_bytes"),
                rs.getString("content_type"),
                rs.getString("etag"),
                meta,
                rs.getTimestamp("created_at").toInstant()
        );
    };

    private Map<String, String> parseMeta(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try {
            return json.readValue(s, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeMeta(Map<String, String> meta) {
        if (meta == null || meta.isEmpty()) return null;
        try {
            return json.writeValueAsString(meta);
        } catch (Exception e) {
            return null;
        }
    }

    public void insert(int shard, ObjectVersionRow row) {
        JdbcTemplate j = shards.jdbcTemplate(shard);
        j.update("""
                INSERT INTO object_versions
                (bucket_name, object_key, version_id, object_id, is_delete_marker,
                 size_bytes, content_type, etag, user_metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::text)
                """,
                row.bucketName(), row.objectKey(), row.versionId(),
                row.objectId(), row.isDeleteMarker(),
                row.sizeBytes(), row.contentType(), row.etag(),
                writeMeta(row.userMetadata()));
    }

    public Optional<ObjectVersionRow> findCurrent(int shard, String bucket, String key) {
        JdbcTemplate j = shards.jdbcTemplate(shard);
        try {
            return Optional.of(j.queryForObject("""
                    SELECT bucket_name, object_key, version_id, object_id, is_delete_marker,
                           size_bytes, content_type, etag, user_metadata, created_at
                    FROM object_versions
                    WHERE bucket_name = ? AND object_key = ?
                    ORDER BY version_id DESC LIMIT 1
                    """, ROW, bucket, key));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ObjectVersionRow> findVersion(int shard, String bucket, String key, String versionId) {
        JdbcTemplate j = shards.jdbcTemplate(shard);
        try {
            return Optional.of(j.queryForObject("""
                    SELECT bucket_name, object_key, version_id, object_id, is_delete_marker,
                           size_bytes, content_type, etag, user_metadata, created_at
                    FROM object_versions
                    WHERE bucket_name = ? AND object_key = ? AND version_id = ?
                    """, ROW, bucket, key, versionId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ObjectVersionRow> listCurrentByPrefix(String bucket, String prefix, String startAfter, int limit) {
        // Cross-shard scatter-gather. Each shard returns its lex-sorted current versions; we merge.
        var startKey = startAfter == null ? "" : startAfter;
        String prefixLike = (prefix == null ? "" : prefix) + "%";
        java.util.List<ObjectVersionRow> all = new java.util.ArrayList<>();
        for (int s = 0; s < shards.shardCount(); s++) {
            JdbcTemplate j = shards.jdbcTemplate(s);
            // current version per (bucket, key) within the shard
            all.addAll(j.query("""
                    SELECT DISTINCT ON (bucket_name, object_key)
                           bucket_name, object_key, version_id, object_id, is_delete_marker,
                           size_bytes, content_type, etag, user_metadata, created_at
                    FROM object_versions
                    WHERE bucket_name = ? AND object_key LIKE ? AND object_key > ?
                    ORDER BY bucket_name, object_key, version_id DESC
                    LIMIT ?
                    """, ROW, bucket, prefixLike, startKey, limit));
        }
        // Filter out delete markers, then sort and limit
        return all.stream()
                .filter(r -> !r.isDeleteMarker())
                .sorted(java.util.Comparator.comparing(ObjectVersionRow::objectKey))
                .limit(limit)
                .toList();
    }

    public boolean deleteVersion(int shard, String bucket, String key, String versionId) {
        JdbcTemplate j = shards.jdbcTemplate(shard);
        return j.update("""
                DELETE FROM object_versions
                WHERE bucket_name = ? AND object_key = ? AND version_id = ?
                """, bucket, key, versionId) > 0;
    }

    public int countObjectsInBucket(String bucket) {
        int total = 0;
        for (int s = 0; s < shards.shardCount(); s++) {
            Integer c = shards.jdbcTemplate(s).queryForObject("""
                    SELECT COUNT(DISTINCT object_key) FROM object_versions
                    WHERE bucket_name = ? AND is_delete_marker = false
                    """, Integer.class, bucket);
            total += (c == null ? 0 : c);
        }
        return total;
    }
}
