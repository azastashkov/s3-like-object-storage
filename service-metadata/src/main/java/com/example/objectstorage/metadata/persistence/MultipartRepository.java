package com.example.objectstorage.metadata.persistence;

import com.example.objectstorage.api.MultipartPart;
import com.example.objectstorage.api.MultipartUpload;
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
public class MultipartRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public MultipartRepository(JdbcTemplate globalJdbcTemplate) {
        this.jdbc = globalJdbcTemplate;
    }

    private final RowMapper<MultipartUpload> UPLOAD_ROW = (rs, n) -> new MultipartUpload(
            (UUID) rs.getObject("upload_id"),
            rs.getString("bucket_name"),
            rs.getString("object_key"),
            rs.getString("initiator"),
            parseMeta(rs.getString("metadata_json")),
            rs.getTimestamp("initiated_at").toInstant()
    );

    private static final RowMapper<MultipartPart> PART_ROW = (rs, n) -> new MultipartPart(
            (UUID) rs.getObject("upload_id"),
            rs.getInt("part_number"),
            (UUID) rs.getObject("part_object_id"),
            rs.getLong("size_bytes"),
            rs.getString("etag"),
            rs.getTimestamp("uploaded_at").toInstant()
    );

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

    public void create(MultipartUpload up) {
        jdbc.update("""
                INSERT INTO multipart_uploads
                (upload_id, bucket_name, object_key, initiator, metadata_json)
                VALUES (?, ?, ?, ?, ?)
                """, up.uploadId(), up.bucketName(), up.objectKey(), up.initiator(),
                writeMeta(up.userMetadata()));
    }

    public Optional<MultipartUpload> findById(UUID uploadId) {
        try {
            return Optional.of(jdbc.queryForObject("""
                    SELECT upload_id, bucket_name, object_key, initiator, metadata_json, initiated_at
                    FROM multipart_uploads WHERE upload_id = ?
                    """, UPLOAD_ROW, uploadId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void addPart(MultipartPart part) {
        jdbc.update("""
                INSERT INTO multipart_parts
                (upload_id, part_number, part_object_id, size_bytes, etag)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (upload_id, part_number) DO UPDATE
                  SET part_object_id = EXCLUDED.part_object_id,
                      size_bytes = EXCLUDED.size_bytes,
                      etag = EXCLUDED.etag,
                      uploaded_at = now()
                """, part.uploadId(), part.partNumber(), part.partObjectId(),
                part.sizeBytes(), part.etag());
    }

    public List<MultipartPart> listParts(UUID uploadId) {
        return jdbc.query("""
                SELECT upload_id, part_number, part_object_id, size_bytes, etag, uploaded_at
                FROM multipart_parts WHERE upload_id = ? ORDER BY part_number
                """, PART_ROW, uploadId);
    }

    public boolean delete(UUID uploadId) {
        return jdbc.update("DELETE FROM multipart_uploads WHERE upload_id = ?", uploadId) > 0;
    }
}
