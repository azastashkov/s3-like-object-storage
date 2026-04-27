package com.example.objectstorage.metadata.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

public class ShardedDataSourceProvider {

    private final List<DataSource> dataSources;
    private final List<JdbcTemplate> jdbcTemplates;

    public ShardedDataSourceProvider(List<DataSource> dataSources, List<JdbcTemplate> jdbcTemplates) {
        this.dataSources = List.copyOf(dataSources);
        this.jdbcTemplates = List.copyOf(jdbcTemplates);
    }

    public int shardCount() {
        return dataSources.size();
    }

    public DataSource dataSource(int shard) {
        return dataSources.get(shard);
    }

    public JdbcTemplate jdbcTemplate(int shard) {
        return jdbcTemplates.get(shard);
    }
}
