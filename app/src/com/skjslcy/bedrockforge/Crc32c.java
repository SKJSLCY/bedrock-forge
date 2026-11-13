package com.skjslcy.bedrockforge;

/** CRC32C（Castagnoli），LevelDB 日志校验使用。 */
final class Crc32c {

    private static final int[] TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? (0x82F63B78 ^ (c >>> 1)) : (c >>> 1);
            }
            TABLE[i] = c;
        }
    }

    private Crc32c() {
    }

    static int compute(byte[] data, int off, int len) {
        int c = 0xFFFFFFFF;
        for (int i = off; i < off + len; i++) {
            c = TABLE[(c ^ data[i]) & 0xFF] ^ (c >>> 8);
        }
        return ~c;
    }
}
