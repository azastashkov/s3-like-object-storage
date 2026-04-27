package com.example.objectstorage.datanode.api;

import com.example.objectstorage.api.ApiError;
import com.example.objectstorage.api.DataNodePutResult;
import com.example.objectstorage.core.checksum.Crc32cUtil;
import com.example.objectstorage.datanode.config.DataNodeProperties;
import com.example.objectstorage.datanode.storage.ChunkFileStore;
import com.example.objectstorage.datanode.storage.CorruptChunkException;
import com.example.objectstorage.datanode.storage.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/chunks")
public class DataNodeController {

    private final ChunkFileStore store;
    private final String nodeId;

    private final Counter writesCounter;
    private final Counter readsCounter;
    private final Counter crcMismatch;
    private final Timer writeTimer;
    private final Timer readTimer;
    private final AtomicLong bytesIn = new AtomicLong();
    private final AtomicLong bytesOut = new AtomicLong();

    public DataNodeController(ChunkFileStore store, DataNodeProperties props, MeterRegistry meters) {
        this.store = store;
        this.nodeId = props.id();
        this.writesCounter = meters.counter("datanode.chunks.writes");
        this.readsCounter = meters.counter("datanode.chunks.reads");
        this.crcMismatch = meters.counter("datanode.chunks.crc.mismatch");
        this.writeTimer = meters.timer("datanode.chunks.write.duration");
        this.readTimer = meters.timer("datanode.chunks.read.duration");
        meters.gauge("datanode.chunks.bytes.written", bytesIn);
        meters.gauge("datanode.chunks.bytes.read", bytesOut);
    }

    @PutMapping(value = "/{objectId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public DataNodePutResult put(@PathVariable UUID objectId,
                                  @RequestHeader(value = "X-CRC32C", required = false) String crcHeader,
                                  @RequestBody byte[] payload) {
        return writeTimer.record(() -> {
            int expected = crcHeader == null ? Crc32cUtil.compute(payload) : Crc32cUtil.fromHex(crcHeader);
            int actual = Crc32cUtil.compute(payload);
            if (expected != actual) {
                crcMismatch.increment();
                throw new CorruptChunkException("CRC mismatch on PUT");
            }
            store.write(objectId, payload, actual);
            writesCounter.increment();
            bytesIn.addAndGet(payload.length);
            return new DataNodePutResult(Crc32cUtil.toHex(actual), payload.length, actual);
        });
    }

    @GetMapping(value = "/{objectId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> get(@PathVariable UUID objectId) {
        return readTimer.record(() -> {
            byte[] data = store.read(objectId);
            readsCounter.increment();
            bytesOut.addAndGet(data.length);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-CRC32C", Crc32cUtil.toHex(store.indexEntry(objectId).crc32c()));
            headers.set("X-Node-Id", nodeId);
            return ResponseEntity.ok().headers(headers).body(data);
        });
    }

    @RequestMapping(value = "/{objectId}", method = org.springframework.web.bind.annotation.RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable UUID objectId) {
        if (!store.exists(objectId)) return ResponseEntity.notFound().build();
        HttpHeaders h = new HttpHeaders();
        h.set("X-Node-Id", nodeId);
        return ResponseEntity.ok().headers(h).build();
    }

    @DeleteMapping("/{objectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID objectId) {
        store.markDeleted(objectId);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NotFound", e.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(CorruptChunkException.class)
    public ResponseEntity<ApiError> corrupt(CorruptChunkException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("CorruptChunk", e.getMessage(), UUID.randomUUID().toString()));
    }
}
