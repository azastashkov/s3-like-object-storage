package com.example.objectstorage.core.cluster;

import com.example.objectstorage.api.ClusterMap;
import com.example.objectstorage.api.DataNode;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ClusterMapLoader {

    @SuppressWarnings("unchecked")
    public ClusterMap load(Path yamlPath) {
        try (var in = Files.newInputStream(yamlPath)) {
            Map<String, Object> root = new Yaml().load(in);
            List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) root.get("nodes");
            if (rawNodes == null || rawNodes.isEmpty()) {
                throw new IllegalStateException("cluster-map.yml has no nodes");
            }
            List<DataNode> nodes = rawNodes.stream()
                    .map(m -> new DataNode(
                            (String) m.get("id"),
                            (String) m.get("host"),
                            ((Number) m.get("port")).intValue(),
                            (String) m.get("rack")
                    ))
                    .toList();
            return new ClusterMap(nodes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + yamlPath, e);
        }
    }
}
