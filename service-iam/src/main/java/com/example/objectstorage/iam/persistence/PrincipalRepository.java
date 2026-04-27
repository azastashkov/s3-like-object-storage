package com.example.objectstorage.iam.persistence;

import com.example.objectstorage.api.Principal;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PrincipalRepository {

    private final JdbcTemplate jdbc;

    public PrincipalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Principal> ROW = (rs, n) -> new Principal(
            rs.getString("principal_id"),
            rs.getString("display_name"),
            rs.getTimestamp("created_at").toInstant()
    );

    public Optional<Principal> findByApiKeyHash(String hash) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT principal_id, display_name, created_at FROM principals WHERE api_key_hash = ?",
                    ROW, hash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Principal> findById(String principalId) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT principal_id, display_name, created_at FROM principals WHERE principal_id = ?",
                    ROW, principalId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
