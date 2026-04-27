package com.example.objectstorage.core.checksum;

import java.util.zip.CRC32C;

public final class Crc32cUtil {
    private Crc32cUtil() {}

    public static int compute(byte[] data) {
        return compute(data, 0, data.length);
    }

    public static int compute(byte[] data, int off, int len) {
        CRC32C c = new CRC32C();
        c.update(data, off, len);
        return (int) c.getValue();
    }

    public static String toHex(int crc) {
        return String.format("%08x", crc);
    }

    public static int fromHex(String hex) {
        return (int) Long.parseLong(hex, 16);
    }
}
