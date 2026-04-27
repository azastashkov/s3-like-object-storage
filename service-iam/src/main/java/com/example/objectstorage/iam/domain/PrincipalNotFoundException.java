package com.example.objectstorage.iam.domain;

public class PrincipalNotFoundException extends RuntimeException {
    public PrincipalNotFoundException(String principalId) {
        super("Principal not found: " + principalId);
    }
}
