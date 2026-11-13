package com.skjslcy.bedrockforge;

/** LevelDB 块使用的 Snappy 解压（只解压，不压缩）。 */
final class Snappy {

    private Snappy() {
    }

    static byte[] uncompress(byte[] in, int off, int len) throws Exception {
        int p = off;
        int end = off + len;
        long ulen = 0;
        int shift = 0;
        while (p < end) {
            int b = in[p++] & 0xFF;
            ulen |= ((long) (b & 0x7F)) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 35) throw new Exception("snappy 长度非法");
        }
        if (ulen <= 0 || ulen > (64L << 20)) throw new Exception("snappy 长度异常：" + ulen);
        byte[] out = new byte[(int) ulen];
        int op = 0;
        while (p < end) {
            int tag = in[p++] & 0xFF;
            int type = tag & 3;
            if (type == 0) {
                int n = (tag >>> 2) + 1;
                if (n > 60) {
                    int extra = n - 60;
                    int v = 0;
                    for (int i = 0; i < extra; i++) v |= (in[p++] & 0xFF) << (8 * i);
                    n = v + 1;
                }
                if (p + n > end || op + n > out.length) throw new Exception("snappy literal 越界");
                System.arraycopy(in, p, out, op, n);
                p += n;
                op += n;
            } else {
                int n;
                int offset;
                if (type == 1) {
                    n = 4 + ((tag >>> 2) & 0x7);
                    offset = ((tag >>> 5) & 0x7) << 8;
                    offset |= in[p++] & 0xFF;
                } else if (type == 2) {
                    n = 1 + (tag >>> 2);
                    offset = (in[p++] & 0xFF) | ((in[p++] & 0xFF) << 8);
                } else {
                    n = 1 + (tag >>> 2);
                    offset = (in[p++] & 0xFF) | ((in[p++] & 0xFF) << 8)
                            | ((in[p++] & 0xFF) << 16) | ((in[p++] & 0xFF) << 24);
                }
                if (offset <= 0 || offset > op || op + n > out.length) throw new Exception("snappy copy 越界");
                int src = op - offset;
                for (int i = 0; i < n; i++) out[op++] = out[src++];
            }
        }
        if (op != out.length) throw new Exception("snappy 解压不完整");
        return out;
    }

    static byte[] uncompress(byte[] in) throws Exception {
        return uncompress(in, 0, in.length);
    }
}
