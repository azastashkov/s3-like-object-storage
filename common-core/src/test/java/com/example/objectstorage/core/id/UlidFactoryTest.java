package com.example.objectstorage.core.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UlidFactoryTest {

    @Test
    void monotonicAcrossCalls() {
        var f = new UlidFactory();
        String prev = f.next();
        for (int i = 0; i < 100; i++) {
            String next = f.next();
            assertThat(next.compareTo(prev)).as("monotonic").isPositive();
            prev = next;
        }
    }

    @Test
    void produces26CharString() {
        assertThat(new UlidFactory().next()).hasSize(26);
    }
}
