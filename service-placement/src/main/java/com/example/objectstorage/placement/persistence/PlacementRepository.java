package com.example.objectstorage.placement.persistence;

import com.example.objectstorage.api.PlacementDecision;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PlacementRepository {

    private final JdbcTemplate jdbc;

    public PlacementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<PlacementDecision> ROW = (rs, n) -> {
        Array a = rs.getArray("replica_nodes");
        String[] replicas = (String[]) a.getArray();
        return new PlacementDecision(
                (UUID) rs.getObject("object_id"),
                rs.getString("primary_node"),
                List.of(replicas)
        );
    };

    public void save(PlacementDecision d) {
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO placements (object_id, primary_node, replica_nodes)
                    VALUES (?, ?, ?)
                    ON CONFLICT (object_id) DO NOTHING
                    """);
            ps.setObject(1, d.objectId());
            ps.setString(2, d.primaryNode());
            Array arr = con.createArrayOf("text", d.replicaNodes().toArray(new String[0]));
            ps.setArray(3, arr);
            return ps;
        });
    }

    public Optional<PlacementDecision> findById(UUID objectId) {
        try {
            return Optional.of(jdbc.queryForObject("""
                    SELECT object_id, primary_node, replica_nodes
                    FROM placements WHERE object_id = ?
                    """, ROW, objectId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<PlacementDecision> findByState(String state, int olderThanSeconds, int limit) {
        return jdbc.query("""
                SELECT object_id, primary_node, replica_nodes
                FROM placements
                WHERE state = ? AND created_at < now() - (? || ' seconds')::interval
                ORDER BY created_at
                LIMIT ?
                """, ROW, state, olderThanSeconds, limit);
    }

    public List<PlacementDecision> findActive(int limit, long offset) {
        return jdbc.query("""
                SELECT object_id, primary_node, replica_nodes
                FROM placements WHERE state = 'ACTIVE'
                ORDER BY created_at LIMIT ? OFFSET ?
                """, ROW, limit, offset);
    }

    public void markTombstoned(UUID objectId) {
        jdbc.update("UPDATE placements SET state = 'TOMBSTONED' WHERE object_id = ?", objectId);
    }

    public void markReclaimed(UUID objectId) {
        jdbc.update("UPDATE placements SET state = 'RECLAIMED' WHERE object_id = ?", objectId);
    }

    public void deleteReclaimed(int olderThanSeconds) {
        jdbc.update("""
                DELETE FROM placements
                WHERE state = 'RECLAIMED' AND created_at < now() - (? || ' seconds')::interval
                """, olderThanSeconds);
    }
}
