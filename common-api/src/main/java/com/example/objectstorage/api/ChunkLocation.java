package com.example.objectstorage.api;

import java.util.UUID;

public record ChunkLocation(
        UUID objectId,
        long chunkFileNumber,
        long offset,
        int payloadLength,
        int crc32c,
        boolean deleted
) {}
