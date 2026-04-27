package com.example.objectstorage.core.auth;

public class InvalidJwtException extends RuntimeException {
    public InvalidJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
