package com.example.objectstorage.placement.domain;

import com.example.objectstorage.api.DataNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RendezvousPlacement {

    private final List<DataNode> nodes;
    private final int replicationFactor;

    public RendezvousPlacement(List<DataNode> nodes, int replicationFactor) {
        if (nodes.size() < replicationFactor) {
            throw new IllegalStateException(
                    "cluster has " + nodes.size() + " nodes but replicationFactor=" + replicationFactor);
        }
        this.nodes = List.copyOf(nodes);
        this.replicationFactor = replicationFactor;
    }

    public List<DataNode> pick(UUID objectId) {
        long oidHi = objectId.getMostSignificantBits();
        long oidLo = objectId.getLeastSignificantBits();

        record Scored(DataNode node, long score) {}
        List<Scored> scored = new ArrayList<>(nodes.size());
        for (DataNode n : nodes) {
            long h = hash64(n.id().getBytes(StandardCharsets.UTF_8)) ^ oidHi ^ oidLo;
            scored.add(new Scored(n, mix(h)));
        }
        scored.sort(Comparator.comparingLong(Scored::score).reversed());

        List<DataNode> picked = new ArrayList<>(replicationFactor);
        Set<String> usedRacks = new HashSet<>();

        // First pass: prefer rack diversity
        for (Scored s : scored) {
            if (picked.size() == replicationFactor) break;
            if (usedRacks.add(s.node().rack())) {
                picked.add(s.node());
            }
        }
        // Second pass: fill remaining slots if not enough racks
        if (picked.size() < replicationFactor) {
            for (Scored s : scored) {
                if (picked.size() == replicationFactor) break;
                if (!picked.contains(s.node())) {
                    picked.add(s.node());
                }
            }
        }
        return picked;
    }

    private static long hash64(byte[] data) {
        long h = 0xcbf29ce484222325L;
        for (byte b : data) { h ^= (b & 0xffL); h *= 0x100000001b3L; }
        return h;
    }

    private static long mix(long h) {
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }
}
