package com.example.objectstorage.datanode.storage;

import com.example.objectstorage.core.checksum.Crc32cUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only chunk file store. Many small objects packed into 1 GiB chunk files
 * with a side-car index per chunk. Supports CRC32C verification, soft delete via
 * index flag, and compaction (rewrite live entries to a fresh chunk).
 *
 * Record layout in chunk-NNNNNNNN.dat:
 *   [4 bytes payload length, BE]
 *   [16 bytes object_id]
 *   [4 bytes CRC32C, BE]
 *   [N bytes payload]
 *
 * Entry layout in chunk-NNNNNNNN.idx (33 bytes each, BE):
 *   [16 bytes object_id]
 *   [8 bytes data offset (start of payload, AFTER the 24-byte header)]
 *   [4 bytes payload length]
 *   [4 bytes CRC32C]
 *   [1 byte flags]
 */
public class ChunkFileStore {

    private static final int RECORD_HEADER = 4 + 16 + 4;

    private final Path root;
    private final long chunkSizeBytes;

    // object_id -> entry
    private final ConcurrentHashMap<UUID, IndexEntry> index = new ConcurrentHashMap<>();
    // chunk_number -> total live bytes (payload only)
    private final Map<Integer, Long> liveBytes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> totalBytes = new ConcurrentHashMap<>();

    private final ReentrantLock writeLock = new ReentrantLock();
    private int activeChunk = 0;
    private FileChannel activeData;
    private FileChannel activeIdx;
    private long activeSize = 0;

    public ChunkFileStore(Path root, long chunkSizeBytes) {
        this.root = root;
        this.chunkSizeBytes = chunkSizeBytes;
        try {
            Files.createDirectories(root.resolve("chunks"));
            recoverFromDisk();
            openActiveChunk();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize chunk store at " + root, e);
        }
    }

    private Path chunksDir() { return root.resolve("chunks"); }

    private Path dataPath(int chunk) {
        return chunksDir().resolve(String.format("chunk-%08d.dat", chunk));
    }

    private Path idxPath(int chunk) {
        return chunksDir().resolve(String.format("chunk-%08d.idx", chunk));
    }

    private void recoverFromDisk() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(chunksDir(), "chunk-*.idx")) {
            List<Integer> chunkNums = new ArrayList<>();
            for (Path p : stream) {
                String name = p.getFileName().toString();
                int num = Integer.parseInt(name.substring("chunk-".length(), name.length() - ".idx".length()));
                chunkNums.add(num);
            }
            for (int chunk : chunkNums) {
                loadIndex(chunk);
                if (chunk > activeChunk) activeChunk = chunk;
            }
            if (Files.exists(dataPath(activeChunk))) {
                activeSize = Files.size(dataPath(activeChunk));
            }
        }
    }

    private void loadIndex(int chunk) throws IOException {
        Path p = idxPath(chunk);
        long total = 0;
        long live = 0;
        try (FileChannel ch = FileChannel.open(p, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(IndexEntry.PERSISTED_BYTES);
            while (ch.read(buf) == IndexEntry.PERSISTED_BYTES) {
                buf.flip();
                long hi = buf.getLong();
                long lo = buf.getLong();
                long off = buf.getLong();
                int len = buf.getInt();
                int crc = buf.getInt();
                byte flags = buf.get();
                boolean deleted = (flags & IndexEntry.FLAG_DELETED) != 0;
                UUID id = new UUID(hi, lo);
                IndexEntry entry = new IndexEntry(id, chunk, off, len, crc, deleted);
                index.put(id, entry);
                total += len;
                if (!deleted) live += len;
                buf.clear();
            }
        }
        totalBytes.put(chunk, total);
        liveBytes.put(chunk, live);
    }

    private void openActiveChunk() throws IOException {
        if (!Files.exists(dataPath(activeChunk))) {
            Files.createFile(dataPath(activeChunk));
            Files.createFile(idxPath(activeChunk));
            totalBytes.putIfAbsent(activeChunk, 0L);
            liveBytes.putIfAbsent(activeChunk, 0L);
        }
        activeData = FileChannel.open(dataPath(activeChunk),
                StandardOpenOption.WRITE, StandardOpenOption.READ);
        activeIdx = FileChannel.open(idxPath(activeChunk),
                StandardOpenOption.WRITE, StandardOpenOption.READ);
        activeSize = Files.size(dataPath(activeChunk));
        activeData.position(activeSize);
        activeIdx.position(activeIdx.size());
    }

    private void rotateActive() throws IOException {
        activeData.force(true); activeData.close();
        activeIdx.force(true); activeIdx.close();
        activeChunk++;
        openActiveChunk();
    }

    public IndexEntry write(UUID objectId, byte[] payload) {
        int crc = Crc32cUtil.compute(payload);
        return write(objectId, payload, crc);
    }

    public IndexEntry write(UUID objectId, byte[] payload, int crc) {
        writeLock.lock();
        try {
            int recordSize = RECORD_HEADER + payload.length;
            if (activeSize + recordSize > chunkSizeBytes && activeSize > 0) {
                rotateActive();
            }
            long payloadOffset = activeSize + RECORD_HEADER;

            ByteBuffer rec = ByteBuffer.allocate(recordSize);
            rec.putInt(payload.length);
            rec.putLong(objectId.getMostSignificantBits());
            rec.putLong(objectId.getLeastSignificantBits());
            rec.putInt(crc);
            rec.put(payload);
            rec.flip();
            while (rec.hasRemaining()) activeData.write(rec);
            activeData.force(false);

            ByteBuffer ie = ByteBuffer.allocate(IndexEntry.PERSISTED_BYTES);
            ie.putLong(objectId.getMostSignificantBits());
            ie.putLong(objectId.getLeastSignificantBits());
            ie.putLong(payloadOffset);
            ie.putInt(payload.length);
            ie.putInt(crc);
            ie.put((byte) 0);
            ie.flip();
            while (ie.hasRemaining()) activeIdx.write(ie);
            activeIdx.force(false);

            activeSize += recordSize;

            IndexEntry entry = new IndexEntry(objectId, activeChunk, payloadOffset, payload.length, crc, false);
            index.put(objectId, entry);
            totalBytes.merge(activeChunk, (long) payload.length, Long::sum);
            liveBytes.merge(activeChunk, (long) payload.length, Long::sum);
            return entry;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write object " + objectId, e);
        } finally {
            writeLock.unlock();
        }
    }

    public byte[] read(UUID objectId) {
        IndexEntry e = index.get(objectId);
        if (e == null || e.deleted()) {
            throw new NotFoundException("Object not found: " + objectId);
        }
        try (FileChannel ch = FileChannel.open(dataPath(e.chunkNumber()), StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(e.payloadLength());
            int read = ch.read(buf, e.dataOffset());
            if (read != e.payloadLength()) {
                throw new CorruptChunkException("short read for " + objectId);
            }
            byte[] data = buf.array();
            int actualCrc = Crc32cUtil.compute(data);
            if (actualCrc != e.crc32c()) {
                throw new CorruptChunkException(
                        "CRC mismatch for " + objectId +
                        " expected=" + Crc32cUtil.toHex(e.crc32c()) +
                        " actual=" + Crc32cUtil.toHex(actualCrc));
            }
            return data;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read " + objectId, ex);
        }
    }

    public boolean exists(UUID objectId) {
        IndexEntry e = index.get(objectId);
        return e != null && !e.deleted();
    }

    public IndexEntry indexEntry(UUID objectId) {
        return index.get(objectId);
    }

    public void markDeleted(UUID objectId) {
        IndexEntry existing = index.get(objectId);
        if (existing == null || existing.deleted()) return;
        try {
            // Rewrite the index entry's flags byte in place.
            // We need to find the entry's offset inside the .idx file by sequentially scanning.
            // Cheap approach: rewrite the entire .idx by rebuilding it. For typical usage this
            // is rare. Here we choose to just append a "delete tombstone" marker to the .idx and
            // overlay logic, but to keep on-disk format simple we instead update in place using
            // a stable location (match by object_id).
            updateIndexFlagsInPlace(existing.chunkNumber(), objectId, (byte) IndexEntry.FLAG_DELETED);
            index.put(objectId, existing.withDeleted());
            liveBytes.merge(existing.chunkNumber(), -(long) existing.payloadLength(), Long::sum);
        } catch (IOException e) {
            throw new RuntimeException("Failed to mark deleted " + objectId, e);
        }
    }

    private void updateIndexFlagsInPlace(int chunk, UUID objectId, byte flags) throws IOException {
        Path p = idxPath(chunk);
        try (FileChannel ch = FileChannel.open(p, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(IndexEntry.PERSISTED_BYTES);
            long pos = 0;
            long size = ch.size();
            while (pos < size) {
                int read = ch.read(buf, pos);
                if (read != IndexEntry.PERSISTED_BYTES) break;
                buf.flip();
                long hi = buf.getLong(0);
                long lo = buf.getLong(8);
                if (hi == objectId.getMostSignificantBits() && lo == objectId.getLeastSignificantBits()) {
                    ByteBuffer flagBuf = ByteBuffer.wrap(new byte[]{flags});
                    ch.write(flagBuf, pos + IndexEntry.PERSISTED_BYTES - 1);
                    ch.force(true);
                    return;
                }
                buf.clear();
                pos += IndexEntry.PERSISTED_BYTES;
            }
        }
    }

    public int compactAll(double deletedRatioThreshold) {
        int compactedChunks = 0;
        for (Integer chunk : new ArrayList<>(totalBytes.keySet())) {
            long total = totalBytes.getOrDefault(chunk, 0L);
            long live = liveBytes.getOrDefault(chunk, 0L);
            if (total == 0) continue;
            if (chunk == activeChunk) continue; // never compact the live writer
            double deletedRatio = (double) (total - live) / total;
            if (deletedRatio >= deletedRatioThreshold) {
                try {
                    compactChunk(chunk);
                    compactedChunks++;
                } catch (IOException e) {
                    throw new RuntimeException("Compaction failed for chunk " + chunk, e);
                }
            }
        }
        return compactedChunks;
    }

    private void compactChunk(int chunk) throws IOException {
        Path oldData = dataPath(chunk);
        Path oldIdx = idxPath(chunk);
        Path newData = oldData.resolveSibling(oldData.getFileName() + ".compacting");
        Path newIdx = oldIdx.resolveSibling(oldIdx.getFileName() + ".compacting");

        Files.deleteIfExists(newData);
        Files.deleteIfExists(newIdx);
        Files.createFile(newData);
        Files.createFile(newIdx);

        long newTotal = 0;

        try (FileChannel inData = FileChannel.open(oldData, StandardOpenOption.READ);
             FileChannel outData = FileChannel.open(newData, StandardOpenOption.WRITE);
             FileChannel outIdx = FileChannel.open(newIdx, StandardOpenOption.WRITE)) {

            // Iterate live entries for this chunk
            for (IndexEntry e : new ArrayList<>(index.values())) {
                if (e.chunkNumber() != chunk || e.deleted()) continue;

                ByteBuffer payload = ByteBuffer.allocate(e.payloadLength());
                int read = inData.read(payload, e.dataOffset());
                if (read != e.payloadLength()) {
                    throw new CorruptChunkException("short read during compaction for " + e.objectId());
                }
                byte[] data = payload.array();

                int recordSize = RECORD_HEADER + data.length;
                ByteBuffer rec = ByteBuffer.allocate(recordSize);
                rec.putInt(data.length);
                rec.putLong(e.objectId().getMostSignificantBits());
                rec.putLong(e.objectId().getLeastSignificantBits());
                rec.putInt(e.crc32c());
                rec.put(data);
                rec.flip();
                long offsetBefore = outData.size();
                while (rec.hasRemaining()) outData.write(rec);
                outData.force(false);

                long payloadOffset = offsetBefore + RECORD_HEADER;

                ByteBuffer ie = ByteBuffer.allocate(IndexEntry.PERSISTED_BYTES);
                ie.putLong(e.objectId().getMostSignificantBits());
                ie.putLong(e.objectId().getLeastSignificantBits());
                ie.putLong(payloadOffset);
                ie.putInt(data.length);
                ie.putInt(e.crc32c());
                ie.put((byte) 0);
                ie.flip();
                while (ie.hasRemaining()) outIdx.write(ie);
                outIdx.force(false);

                IndexEntry rebuilt = new IndexEntry(e.objectId(), chunk, payloadOffset, data.length, e.crc32c(), false);
                index.put(e.objectId(), rebuilt);
                newTotal += data.length;
            }
        }

        // Atomic swap
        Files.move(newData, oldData, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Files.move(newIdx, oldIdx, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        totalBytes.put(chunk, newTotal);
        liveBytes.put(chunk, newTotal);
    }

    public long liveBytes(int chunk) { return liveBytes.getOrDefault(chunk, 0L); }
    public long totalBytes(int chunk) { return totalBytes.getOrDefault(chunk, 0L); }
    public int activeChunkNumber() { return activeChunk; }

    public Map<Integer, Long> allLiveBytes() { return Map.copyOf(liveBytes); }
    public Map<Integer, Long> allTotalBytes() { return Map.copyOf(totalBytes); }

    public synchronized void close() throws IOException {
        if (activeData != null) { activeData.force(true); activeData.close(); }
        if (activeIdx != null) { activeIdx.force(true); activeIdx.close(); }
    }
}
