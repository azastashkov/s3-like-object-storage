package com.example.objectstorage.datanode.storage;

public class CorruptChunkException extends RuntimeException {
    public CorruptChunkException(String msg) { super(msg); }
}
