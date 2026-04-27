package com.example.objectstorage.core.cluster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterMapLoaderTest {

    @Test
    void loadsThreeNodesAcrossThreeRacks(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("cm.yml");
        Files.writeString(file, """
                nodes:
                  - { id: dn-1, host: data-node-1, port: 8085, rack: A }
                  - { id: dn-2, host: data-node-2, port: 8085, rack: B }
                  - { id: dn-3, host: data-node-3, port: 8085, rack: C }
                """);
        var loader = new ClusterMapLoader();
        var map = loader.load(file);
        assertThat(map.nodes()).hasSize(3);
        assertThat(map.nodes().stream().map(n -> n.rack()).distinct().count()).isEqualTo(3L);
        assertThat(map.nodes().get(0).baseUrl()).isEqualTo("http://data-node-1:8085");
    }
}
