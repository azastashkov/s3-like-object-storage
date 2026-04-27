package com.example.objectstorage.api.api;

import com.example.objectstorage.api.ApiError;
import com.example.objectstorage.api.domain.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> apiException(ApiException e) {
        return ResponseEntity.status(e.status())
                .body(new ApiError(e.code(), e.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<ApiError> downstream(HttpStatusCodeException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(new ApiError("Downstream", e.getStatusText(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("InternalError", e.getMessage(), UUID.randomUUID().toString()));
    }
}
