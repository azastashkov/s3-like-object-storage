package com.example.objectstorage.datanode.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkFileStoreTest {

    @Test
    void writeReadRoundTrip(@TempDir Path tmp) {
        var s = new ChunkFileStore(tmp, 1L << 20);
        var id = UUID.randomUUID();
        byte[] payload = "hello world".getBytes();
        s.write(id, payload);
        assertThat(s.read(id)).isEqualTo(payload);
        assertThat(s.exists(id)).isTrue();
    }

    @Test
    void rotatesWhenChunkCapExceeded(@TempDir Path tmp) {
        var s = new ChunkFileStore(tmp, 100); // tiny cap
        for (int i = 0; i < 10; i++) {
            s.write(UUID.randomUUID(), new byte[40]);
        }
        // active chunk number > 0 means rotation happened
        assertThat(s.activeChunkNumber()).isGreaterThan(0);
    }

    @Test
    void rebuildsIndexFromDiskOnRestart(@TempDir Path tmp) throws IOException {
        var id = UUID.randomUUID();
        byte[] payload = "rebuild me".getBytes();
        var s1 = new ChunkFileStore(tmp, 1L << 20);
        s1.write(id, payload);
        s1.close();

        var s2 = new ChunkFileStore(tmp, 1L << 20);
        assertThat(s2.read(id)).isEqualTo(payload);
        s2.close();
    }

    @Test
    void crcMismatchOnReadThrows(@TempDir Path tmp) throws IOException {
        var s = new ChunkFileStore(tmp, 1L << 20);
        var id = UUID.randomUUID();
        byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        s.write(id, payload);
        s.close();

        // Corrupt the chunk file
        Path chunk = tmp.resolve("chunks").resolve("chunk-00000000.dat");
        byte[] all = Files.readAllBytes(chunk);
        all[all.length - 1] ^= 0xFF;
        Files.write(chunk, all);

        var s2 = new ChunkFileStore(tmp, 1L << 20);
        assertThatThrownBy(() -> s2.read(id)).isInstanceOf(CorruptChunkException.class);
    }

    @Test
    void markDeletedHidesAndIsPersisted(@TempDir Path tmp) throws IOException {
        var s = new ChunkFileStore(tmp, 1L << 20);
        var id = UUID.randomUUID();
        s.write(id, new byte[]{9});
        s.markDeleted(id);
        assertThatThrownBy(() -> s.read(id)).isInstanceOf(NotFoundException.class);
        s.close();

        var s2 = new ChunkFileStore(tmp, 1L << 20);
        assertThatThrownBy(() -> s2.read(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void compactionReclaimsDeletedSpace(@TempDir Path tmp) {
        var s = new ChunkFileStore(tmp, 100); // tiny so we get multiple chunks
        var keep = UUID.randomUUID();
        var drop = UUID.randomUUID();
        s.write(keep, new byte[20]);
        s.write(drop, new byte[40]);
        // force rotation to a new chunk so chunk-0 becomes eligible
        for (int i = 0; i < 5; i++) s.write(UUID.randomUUID(), new byte[40]);
        s.markDeleted(drop);

        long beforeLive = s.allLiveBytes().values().stream().mapToLong(Long::longValue).sum();
        long beforeTotal = s.allTotalBytes().values().stream().mapToLong(Long::longValue).sum();
        assertThat(beforeLive).isLessThan(beforeTotal);

        int compacted = s.compactAll(0.3);
        assertThat(compacted).isGreaterThanOrEqualTo(1);

        // After compaction, totals should equal lives (no holes left in compacted chunks)
        long afterLive = s.allLiveBytes().values().stream().mapToLong(Long::longValue).sum();
        long afterTotal = s.allTotalBytes().values().stream().mapToLong(Long::longValue).sum();
        assertThat(afterTotal - afterLive).isLessThanOrEqualTo(beforeTotal - beforeLive);
        assertThat(s.read(keep)).hasSize(20);
    }
}
