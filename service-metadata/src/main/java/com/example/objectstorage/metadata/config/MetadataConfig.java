package com.example.objectstorage.metadata.config;

import com.example.objectstorage.core.sharding.ShardRouter;
import com.example.objectstorage.metadata.persistence.ShardedDataSourceProvider;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.IntStream;

@Configuration
@EnableConfigurationProperties(MetadataProperties.class)
public class MetadataConfig {

    @Bean
    public ShardRouter shardRouter(MetadataProperties props) {
        return new ShardRouter(props.shardCount());
    }

    @Bean(destroyMethod = "close")
    public HikariDataSource globalDataSource(MetadataProperties props) {
        return buildHikari(props.global(), "meta-global");
    }

    @Bean
    public ShardedDataSourceProvider shardedDataSourceProvider(MetadataProperties props) {
        if (props.shards().size() != props.shardCount()) {
            throw new IllegalStateException(
                    "shard-count=" + props.shardCount() +
                    " but shards list size=" + props.shards().size());
        }
        List<DataSource> dataSources = IntStream.range(0, props.shardCount())
                .mapToObj(i -> (DataSource) buildHikari(props.shards().get(i), "meta-shard-" + i))
                .toList();
        List<JdbcTemplate> jdbcTemplates = dataSources.stream()
                .map(JdbcTemplate::new)
                .toList();
        return new ShardedDataSourceProvider(dataSources, jdbcTemplates);
    }

    @Bean
    public JdbcTemplate globalJdbcTemplate(HikariDataSource globalDataSource) {
        return new JdbcTemplate(globalDataSource);
    }

    private static HikariDataSource buildHikari(MetadataProperties.DbSpec spec, String poolName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(spec.url());
        ds.setUsername(spec.username());
        ds.setPassword(spec.password());
        ds.setPoolName(poolName);
        ds.setMaximumPoolSize(8);
        ds.setMinimumIdle(1);
        return ds;
    }
}
