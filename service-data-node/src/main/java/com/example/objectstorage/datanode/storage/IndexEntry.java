package com.example.objectstorage.datanode.storage;

import java.util.UUID;

/**
 * In-memory index entry. Persistent layout (33 bytes per entry):
 *   [16 bytes object_id][8 bytes data offset][4 bytes payload length]
 *   [4 bytes CRC32C][1 byte flags (bit0=deleted)]
 */
public record IndexEntry(
        UUID objectId,
        int chunkNumber,
        long dataOffset,
        int payloadLength,
        int crc32c,
        boolean deleted
) {
    public static final int PERSISTED_BYTES = 16 + 8 + 4 + 4 + 1;
    public static final byte FLAG_DELETED = 0x1;

    public IndexEntry withDeleted() {
        return new IndexEntry(objectId, chunkNumber, dataOffset, payloadLength, crc32c, true);
    }
}
