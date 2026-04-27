package com.example.objectstorage.datanode.config;

import com.example.objectstorage.datanode.storage.ChunkFileStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(DataNodeProperties.class)
public class DataNodeConfig {

    @Bean(destroyMethod = "close")
    public ChunkFileStore chunkFileStore(DataNodeProperties props, MeterRegistry meters) {
        ChunkFileStore store = new ChunkFileStore(Path.of(props.dataDir()), props.chunkSizeBytes());
        meters.gauge("datanode.chunks.active", store, ChunkFileStore::activeChunkNumber);
        meters.gauge("datanode.bytes.live.total", store,
                s -> s.allLiveBytes().values().stream().mapToLong(Long::longValue).sum());
        meters.gauge("datanode.bytes.total", store,
                s -> s.allTotalBytes().values().stream().mapToLong(Long::longValue).sum());
        return store;
    }
}
