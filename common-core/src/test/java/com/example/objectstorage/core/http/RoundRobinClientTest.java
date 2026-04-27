package com.example.objectstorage.core.http;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinClientTest {

    @Test
    void rotatesThroughBaseUrls() {
        var c = new RoundRobinClient(List.of("a", "b", "c"));
        assertThat(c.nextBaseUrl()).isEqualTo("a");
        assertThat(c.nextBaseUrl()).isEqualTo("b");
        assertThat(c.nextBaseUrl()).isEqualTo("c");
        assertThat(c.nextBaseUrl()).isEqualTo("a");
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> new RoundRobinClient(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
