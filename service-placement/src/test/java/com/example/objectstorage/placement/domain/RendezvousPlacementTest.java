package com.example.objectstorage.placement.domain;

import com.example.objectstorage.api.DataNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RendezvousPlacementTest {

    private static final List<DataNode> NODES = List.of(
            new DataNode("dn-1", "host-1", 8085, "A"),
            new DataNode("dn-2", "host-2", 8085, "B"),
            new DataNode("dn-3", "host-3", 8085, "C"),
            new DataNode("dn-4", "host-4", 8085, "A")
    );

    @Test
    void picksThreeDistinctRacks() {
        var placer = new RendezvousPlacement(NODES, 3);
        for (int i = 0; i < 100; i++) {
            var picked = placer.pick(UUID.randomUUID());
            assertThat(picked).hasSize(3);
            assertThat(picked.stream().map(DataNode::rack).collect(Collectors.toSet()))
                    .as("distinct racks for object " + i)
                    .hasSize(3);
        }
    }

    @Test
    void deterministicForSameObjectId() {
        var placer = new RendezvousPlacement(NODES, 3);
        var oid = UUID.randomUUID();
        assertThat(placer.pick(oid)).isEqualTo(placer.pick(oid));
    }

    @Test
    void rejectsTooFewNodes() {
        var nodes = List.of(new DataNode("a", "h", 1, "X"));
        try {
            new RendezvousPlacement(nodes, 3);
        } catch (IllegalStateException ok) {
            return;
        }
        throw new AssertionError("should have thrown");
    }
}
