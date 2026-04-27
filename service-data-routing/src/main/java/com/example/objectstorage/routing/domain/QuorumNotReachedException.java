package com.example.objectstorage.routing.domain;

public class QuorumNotReachedException extends RuntimeException {
    public QuorumNotReachedException(String msg) { super(msg); }
}
