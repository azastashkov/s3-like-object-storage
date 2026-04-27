package com.example.objectstorage.api;

public record ApiError(String code, String message, String requestId) {}
