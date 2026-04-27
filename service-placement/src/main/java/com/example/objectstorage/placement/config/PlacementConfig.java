package com.example.objectstorage.placement.config;

import com.example.objectstorage.api.ClusterMap;
import com.example.objectstorage.core.cluster.ClusterMapLoader;
import com.example.objectstorage.placement.domain.RendezvousPlacement;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(PlacementProperties.class)
public class PlacementConfig {

    @Bean
    public ClusterMap clusterMap(PlacementProperties props) {
        return new ClusterMapLoader().load(Path.of(props.clusterMapPath()));
    }

    @Bean
    public RendezvousPlacement rendezvousPlacement(ClusterMap map, PlacementProperties props) {
        return new RendezvousPlacement(map.nodes(), props.replicationFactor());
    }
}
