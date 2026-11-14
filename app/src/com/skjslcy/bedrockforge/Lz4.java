package com.skjslcy.bedrockforge;

/** LZ4 block 解压（RocksDB/部分 LevelDB 分支使用的类型 4）。 */
final class Lz4 {

    private Lz4() {
    }

    static byte[] decompress(byte[] in, int off, int len, int expected) throws Exception {
        if (expected <= 0 || expected > (64 << 20)) expected = Math.max(len * 8, 4096);
        byte[] out = new byte[expected];
        int ip = off;
        int iend = off + len;
        int op = 0;
        while (ip < iend) {
            int token = in[ip++] & 0xFF;
            int lit = token >>> 4;
            if (lit == 15) {
                int b;
                do {
                    if (ip >= iend) throw new Exception(I18n.t("lz4 literal 长度越界"));
                    b = in[ip++] & 0xFF;
                    lit += b;
                } while (b == 255);
            }
            if (ip + lit > iend) throw new Exception(I18n.t("lz4 literal 越界"));
            if (op + lit > out.length) throw new Exception(I18n.t("lz4 输出越界"));
            System.arraycopy(in, ip, out, op, lit);
            ip += lit;
            op += lit;
            if (ip >= iend) break;
            if (ip + 2 > iend) throw new Exception(I18n.t("lz4 offset 越界"));
            int offset = (in[ip] & 0xFF) | ((in[ip + 1] & 0xFF) << 8);
            ip += 2;
            if (offset <= 0 || offset > op) throw new Exception(I18n.t("lz4 offset 非法"));
            int match = (token & 0x0F) + 4;
            if ((token & 0x0F) == 15) {
                int b;
                do {
                    if (ip >= iend) throw new Exception(I18n.t("lz4 match 长度越界"));
                    b = in[ip++] & 0xFF;
                    match += b;
                } while (b == 255);
            }
            if (op + match > out.length) throw new Exception(I18n.t("lz4 输出越界(match)"));
            int src = op - offset;
            for (int i = 0; i < match; i++) out[op++] = out[src++];
        }
        if (op == out.length) return out;
        byte[] r = new byte[op];
        System.arraycopy(out, 0, r, 0, op);
        return r;
    }
}
