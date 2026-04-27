package com.example.objectstorage.core.checksum;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Crc32cUtilTest {

    @Test
    void knownVectorMatches() {
        // CRC32C("123456789") = 0xe3069283
        int crc = Crc32cUtil.compute("123456789".getBytes(StandardCharsets.UTF_8));
        assertThat(Crc32cUtil.toHex(crc)).isEqualTo("e3069283");
    }

    @Test
    void hexRoundTrip() {
        int crc = Crc32cUtil.compute("hello world".getBytes(StandardCharsets.UTF_8));
        assertThat(Crc32cUtil.fromHex(Crc32cUtil.toHex(crc))).isEqualTo(crc);
    }

    @Test
    void differentBytesDifferentCrc() {
        int a = Crc32cUtil.compute("a".getBytes(StandardCharsets.UTF_8));
        int b = Crc32cUtil.compute("b".getBytes(StandardCharsets.UTF_8));
        assertThat(a).isNotEqualTo(b);
    }
}
