package com.example.objectstorage.core.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class MetricsAutoConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name:unknown}") String appName) {
        return registry -> registry.config().commonTags("application", appName);
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> denyHikariFiles() {
        return registry -> registry.config().meterFilter(MeterFilter.deny(id ->
                id.getName().startsWith("hikaricp.connections.creation")
        ));
    }
}
