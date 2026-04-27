package com.example.objectstorage.metadata.persistence;

import com.example.objectstorage.api.Bucket;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BucketRepository {

    private final JdbcTemplate jdbc;

    public BucketRepository(JdbcTemplate globalJdbcTemplate) {
        this.jdbc = globalJdbcTemplate;
    }

    private static final RowMapper<Bucket> ROW = (rs, n) -> new Bucket(
            rs.getString("name"),
            rs.getString("owner_principal"),
            rs.getBoolean("versioning_enabled"),
            rs.getTimestamp("created_at").toInstant()
    );

    public boolean create(String name, String owner, boolean versioning) {
        try {
            jdbc.update("""
                    INSERT INTO buckets (name, owner_principal, versioning_enabled)
                    VALUES (?, ?, ?)
                    """, name, owner, versioning);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<Bucket> findByName(String name) {
        try {
            return Optional.of(jdbc.queryForObject("""
                    SELECT name, owner_principal, versioning_enabled, created_at
                    FROM buckets WHERE name = ?
                    """, ROW, name));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean delete(String name) {
        return jdbc.update("DELETE FROM buckets WHERE name = ?", name) > 0;
    }

    public void setVersioning(String name, boolean enabled) {
        jdbc.update("UPDATE buckets SET versioning_enabled = ? WHERE name = ?", enabled, name);
    }

    public List<Bucket> listForOwner(String owner) {
        return jdbc.query("""
                SELECT name, owner_principal, versioning_enabled, created_at
                FROM buckets WHERE owner_principal = ? ORDER BY name
                """, ROW, owner);
    }
}
