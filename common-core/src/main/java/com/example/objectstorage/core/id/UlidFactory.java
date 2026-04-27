package com.example.objectstorage.core.id;

import com.github.f4b6a3.ulid.UlidCreator;

public final class UlidFactory {
    public String next() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
