package com.skjslcy.bedrockforge;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 只读 LevelDB 读取器：扫描 .ldb（SST）与 .log（WAL），按 sequence 取最新值。
 * 只实现“按键查值”，不做完整数据库语义。
 */
final class LevelDbReader {

    static final class Result {
        byte[] value;
        boolean deleted;
        long seq = -1;
        String source = "";
    }

    private LevelDbReader() {
    }

    /** 列出所有以 prefix 开头的键（按 sequence 取最新，跳过删除）。 */
    static java.util.LinkedHashMap<String, byte[]> list(File dbDir, String prefix)
            throws Exception {
        java.util.LinkedHashMap<String, byte[]> out = new java.util.LinkedHashMap<>();
        java.util.HashMap<String, Long> seqs = new java.util.HashMap<>();
        File[] files = dbDir.listFiles();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : files) {
            String n = f.getName();
            if (!(n.endsWith(".ldb") || n.endsWith(".sst"))) continue;
            try {
                scanTableAll(f, prefix, out, seqs);
            } catch (Throwable ignored) {
            }
        }
        for (File f : files) {
            if (!f.getName().endsWith(".log")) continue;
            try {
                scanLogAll(f, prefix, out, seqs);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static void scanTableAll(File file, String prefix,
                                     java.util.LinkedHashMap<String, byte[]> out,
                                     java.util.HashMap<String, Long> seqs) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            if (len < 48) return;
            byte[] footer = new byte[48];
            raf.seek(len - 48);
            raf.readFully(footer);
            int[] p = {0};
            readVarLong(footer, p);
            readVarLong(footer, p);
            long idxOff = readVarLong(footer, p);
            long idxSize = readVarLong(footer, p);
            if (idxOff <= 0 || idxSize <= 0 || idxOff + idxSize > len) return;
            byte[] indexBlock = readBlock(raf, idxOff, idxSize, len);
            List<long[]> handles = new ArrayList<>();
            parseBlockEntries(indexBlock, (k, v) -> {
                int[] q = {0};
                long off = readVarLong(v, q);
                long size = readVarLong(v, q);
                handles.add(new long[]{off, size});
            }, 0, 0);
            for (long[] h : handles) {
                byte[] block = readBlock(raf, h[0], h[1], len);
                parseBlockEntries(block, (k, v) -> {
                    if (k.length < 8) return;
                    byte[] uk = userKey(k);
                    String s;
                    try {
                        s = new String(uk, "UTF-8");
                    } catch (Throwable t) {
                        return;
                    }
                    if (!s.startsWith(prefix)) return;
                    long trailer = readLongLE(k, k.length - 8);
                    long seq = trailer >>> 8;
                    boolean del = (trailer & 0xFF) == 0;
                    Long prev = seqs.get(s);
                    if (prev != null && prev >= seq) return;
                    seqs.put(s, seq);
                    if (del) out.remove(s);
                    else out.put(s, v);
                }, 0, 0);
            }
        }
    }

    private static void scanLogAll(File file, String prefix,
                                   java.util.LinkedHashMap<String, byte[]> out,
                                   java.util.HashMap<String, Long> seqs) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            byte[] block = new byte[32768];
            long pos = 0;
            java.io.ByteArrayOutputStream pending = null;
            while (pos < len) {
                int blockLen = (int) Math.min(32768, len - pos);
                raf.seek(pos);
                raf.readFully(block, 0, blockLen);
                int p = 0;
                while (p + 7 <= blockLen) {
                    int length = (block[p + 4] & 0xFF) | ((block[p + 5] & 0xFF) << 8);
                    int type = block[p + 6] & 0xFF;
                    p += 7;
                    if (type == 0 || length == 0) break;
                    if (p + length > blockLen) break;
                    if (type == 1) {
                        batchAll(block, p, length, prefix, out, seqs);
                        pending = null;
                    } else if (type == 2) {
                        pending = new java.io.ByteArrayOutputStream();
                        pending.write(block, p, length);
                    } else if (type == 3 && pending != null) {
                        pending.write(block, p, length);
                    } else if (type == 4 && pending != null) {
                        pending.write(block, p, length);
                        byte[] full = pending.toByteArray();
                        batchAll(full, 0, full.length, prefix, out, seqs);
                        pending = null;
                    }
                    p += length;
                }
                pos += blockLen;
            }
        }
    }

    private static void batchAll(byte[] buf, int off, int len, String prefix,
                                 java.util.LinkedHashMap<String, byte[]> out,
                                 java.util.HashMap<String, Long> seqs) {
        if (len < 12) return;
        long seq = readLongLE(buf, off);
        int count = readIntLE(buf, off + 8);
        if (count <= 0 || count > 400000) return;
        int p = off + 12;
        int end = off + len;
        for (int i = 0; i < count && p < end; i++) {
            int tag = buf[p++] & 0xFF;
            int[] q = {p};
            long klen;
            try {
                klen = readVarLong(buf, q);
            } catch (Throwable t) {
                return;
            }
            p = q[0];
            if (klen < 0 || p + klen > end) return;
            String key;
            try {
                key = new String(buf, p, (int) klen, "UTF-8");
            } catch (Throwable t) {
                return;
            }
            p += klen;
            byte[] v = null;
            if (tag == 1) {
                q[0] = p;
                long vlen;
                try {
                    vlen = readVarLong(buf, q);
                } catch (Throwable t) {
                    return;
                }
                p = q[0];
                if (vlen < 0 || p + vlen > end) return;
                v = new byte[(int) vlen];
                System.arraycopy(buf, p, v, 0, (int) vlen);
                p += vlen;
            }
            if (!key.startsWith(prefix)) continue;
            long entrySeq = seq + i;
            Long prev = seqs.get(key);
            if (prev != null && prev >= entrySeq) continue;
            seqs.put(key, entrySeq);
            if (tag == 0) out.remove(key);
            else if (v != null) out.put(key, v);
        }
    }

    /** 在整个目录里查 key，返回 sequence 最大的那条记录。 */
    static Result get(File dbDir, byte[] key) throws Exception {
        Result best = new Result();
        File[] files = dbDir.listFiles();
        if (files == null) throw new Exception("无法读取数据库目录");
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));

        for (File f : files) {
            String n = f.getName();
            if (n.endsWith(".ldb") || n.endsWith(".sst")) {
                try {
                    scanTable(f, key, best);
                } catch (Throwable ignored) {
                }
            }
        }
        for (File f : files) {
            String n = f.getName();
            if (n.endsWith(".log")) {
                try {
                    scanLog(f, key, best);
                } catch (Throwable ignored) {
                }
            }
        }
        return best;
    }

    /** 读取 key 的最新值；不存在返回 null。 */
    static byte[] value(File dbDir, String key) throws Exception {
        Result r = get(dbDir, key.getBytes("UTF-8"));
        return r.value;
    }

    /** 扫描整个数据库，取当前最大 sequence（用于追加 WAL）。 */
    static long lastSequence(File dbDir) throws Exception {
        final long[] max = {-1};
        File[] files = dbDir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            String n = f.getName();
            try {
                if (n.endsWith(".ldb") || n.endsWith(".sst")) {
                    try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                        long len = raf.length();
                        if (len < 48) continue;
                        byte[] footer = new byte[48];
                        raf.seek(len - 48);
                        raf.readFully(footer);
                        int[] p = {0};
                        readVarLong(footer, p);
                        readVarLong(footer, p);
                        long idxOff = readVarLong(footer, p);
                        long idxSize = readVarLong(footer, p);
                        if (idxOff <= 0 || idxSize <= 0) continue;
                        byte[] idx = readBlock(raf, idxOff, idxSize, len);
                        List<long[]> handles = new ArrayList<>();
                        parseBlockEntries(idx, (k, v) -> {
                            int[] q = {0};
                            handles.add(new long[]{readVarLong(v, q), readVarLong(v, q)});
                        }, 0, 0);
                        for (long[] h : handles) {
                            byte[] block = readBlock(raf, h[0], h[1], len);
                            parseBlockEntries(block, (k, v) -> {
                                if (k.length < 8) return;
                                long seq = readLongLE(k, k.length - 8) >>> 8;
                                if (seq > max[0]) max[0] = seq;
                            }, 0, 0);
                        }
                    }
                } else if (n.endsWith(".log")) {
                    try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                        long len = raf.length();
                        byte[] block = new byte[32768];
                        long pos = 0;
                        while (pos < len) {
                            int bl = (int) Math.min(32768, len - pos);
                            raf.seek(pos);
                            raf.readFully(block, 0, bl);
                            int p = 0;
                            while (p + 7 <= bl) {
                                int length = (block[p + 4] & 0xFF) | ((block[p + 5] & 0xFF) << 8);
                                int type = block[p + 6] & 0xFF;
                                p += 7;
                                if (type == 0 || length == 0) break;
                                if (p + length > bl) break;
                                if (type == 1 && length >= 12) {
                                    long seq = readLongLE(block, p);
                                    int count = readIntLE(block, p + 8);
                                    if (count > 0 && seq + count - 1 > max[0]) {
                                        max[0] = seq + count - 1;
                                    }
                                }
                                p += length;
                            }
                            pos += bl;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return max[0] < 0 ? 0 : max[0];
    }
    // ---------------------------------------------------------------- SST

    private static void scanTable(File file, byte[] key, Result best) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            if (len < 48) return;
            byte[] footer = new byte[48];
            raf.seek(len - 48);
            raf.readFully(footer);
            int[] p = {0};
            long metaOff = readVarLong(footer, p);
            long metaSize = readVarLong(footer, p);
            long idxOff = readVarLong(footer, p);
            long idxSize = readVarLong(footer, p);
            if (idxOff <= 0 || idxSize <= 0 || idxOff + idxSize > len) return;

            byte[] indexBlock = readBlock(raf, idxOff, idxSize, len);
            List<byte[]> indexKeys = new ArrayList<>();
            List<long[]> handles = new ArrayList<>();
            parseBlockEntries(indexBlock, (k, v) -> {
                indexKeys.add(k);
                int[] q = {0};
                long off = readVarLong(v, q);
                long size = readVarLong(v, q);
                handles.add(new long[]{off, size});
            }, 0, 0);

            if (indexKeys.isEmpty()) return;
            for (int i = 0; i < indexKeys.size(); i++) {
                byte[] sep = userKey(indexKeys.get(i));
                int cmp = compare(sep, key);
                if (cmp < 0) continue;
                // 该块覆盖 [prevSep, sep]，目标键可能在上一个块
                for (int b = Math.max(0, i - 1); b <= i; b++) {
                    long[] h = handles.get(b);
                    byte[] block = readBlock(raf, h[0], h[1], len);
                    parseBlockEntries(block, (k, v) -> consider(k, v, key, best, file.getName()),
                            0, 0);
                }
                return;
            }
        }
    }

    static byte[] readBlock(RandomAccessFile raf, long off, long size, long fileLen)
            throws Exception {
        if (off < 0 || size <= 0 || off + size > fileLen) throw new Exception("块越界");
        byte[] data = new byte[(int) size];
        raf.seek(off);
        raf.readFully(data);
        // 该 BDS 的 LevelDB 分支：BlockHandle.size 只含数据，类型字节在数据之后。
        int type = 0;
        if (off + size < fileLen) {
            raf.seek(off + size);
            type = raf.read() & 0xFF;
        }
        if (type == 1) return Snappy.uncompress(data);
        if (type == 4) return inflateRaw(data);
        return data;
    }

    private static byte[] inflateRaw(byte[] data) throws Exception {
        java.util.zip.Inflater inf = new java.util.zip.Inflater(true);
        try {
            inf.setInput(data);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(
                    Math.max(1024, data.length * 3));
            byte[] buf = new byte[65536];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n <= 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break;
                } else {
                    bos.write(buf, 0, n);
                    if (bos.size() > (64 << 20)) throw new Exception("块解压过大");
                }
            }
            if (bos.size() == 0) throw new Exception("块解压为空");
            return bos.toByteArray();
        } finally {
            inf.end();
        }
    }

    interface EntrySink {
        void accept(byte[] key, byte[] value);
    }

    /** 解析 block：entries + restart 数组。 */
    static void parseBlockEntries(byte[] block, EntrySink sink, int dummy1, int dummy2)
            throws Exception {
        if (block.length < 4) return;
        int restarts = readIntLE(block, block.length - 4);
        int limit = block.length - 4 - restarts * 4;
        if (limit <= 0) return;
        int p = 0;
        byte[] last = new byte[0];
        int guard = 0;
        while (p < limit) {
            if (++guard > 2000000) break;
            int before = p;
            int[] q = {p};
            int shared = (int) readVarLong(block, q);
            int nonShared = (int) readVarLong(block, q);
            int valueLen = (int) readVarLong(block, q);
            p = q[0];
            if (shared < 0 || nonShared < 0 || valueLen < 0) break;
            if (p + nonShared > limit) break;
            byte[] k = new byte[shared + nonShared];
            System.arraycopy(last, 0, k, 0, Math.min(shared, last.length));
            System.arraycopy(block, p, k, shared, nonShared);
            p += nonShared;
            if (p + valueLen > limit) break;
            byte[] v = new byte[valueLen];
            System.arraycopy(block, p, v, 0, valueLen);
            p += valueLen;
            if (p <= before) break;
            sink.accept(k, v);
            last = k;
        }
    }

    // ---------------------------------------------------------------- WAL

    private static void scanLog(File file, byte[] key, Result best) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            byte[] block = new byte[32768];
            long pos = 0;
            java.io.ByteArrayOutputStream pending = null;
            while (pos < len) {
                int blockLen = (int) Math.min(32768, len - pos);
                raf.seek(pos);
                raf.readFully(block, 0, blockLen);
                int p = 0;
                while (p + 7 <= blockLen) {
                    long crc = readUInt32LE(block, p);
                    int length = (block[p + 4] & 0xFF) | ((block[p + 5] & 0xFF) << 8);
                    int type = block[p + 6] & 0xFF;
                    p += 7;
                    if (type == 0 || length == 0) {
                        p = blockLen;
                        break;
                    }
                    if (p + length > blockLen) break;
                    if (type == 1) {
                        handleBatch(block, p, length, key, best, file.getName());
                        pending = null;
                    } else if (type == 2) {
                        pending = new java.io.ByteArrayOutputStream();
                        pending.write(block, p, length);
                    } else if (type == 3 && pending != null) {
                        pending.write(block, p, length);
                    } else if (type == 4 && pending != null) {
                        pending.write(block, p, length);
                        byte[] full = pending.toByteArray();
                        handleBatch(full, 0, full.length, key, best, file.getName());
                        pending = null;
                    }
                    p += length;
                }
                pos += blockLen;
            }
        }
    }

    private static void handleBatch(byte[] buf, int off, int len, byte[] key, Result best,
                                    String source) {
        if (len < 12) return;
        long seq = readLongLE(buf, off);
        int count = readIntLE(buf, off + 8);
        if (count <= 0 || count > 400000) return;
        int p = off + 12;
        int end = off + len;
        for (int i = 0; i < count && p < end; i++) {
            int tag = buf[p++] & 0xFF;
            int[] q = {p};
            long klen;
            try {
                klen = readVarLong(buf, q);
            } catch (Throwable t) {
                return;
            }
            p = q[0];
            if (klen < 0 || p + klen > end) return;
            byte[] k = new byte[(int) klen];
            System.arraycopy(buf, p, k, 0, (int) klen);
            p += klen;
            byte[] v = null;
            if (tag == 1) {
                q[0] = p;
                long vlen;
                try {
                    vlen = readVarLong(buf, q);
                } catch (Throwable t) {
                    return;
                }
                p = q[0];
                if (vlen < 0 || p + vlen > end) return;
                v = new byte[(int) vlen];
                System.arraycopy(buf, p, v, 0, (int) vlen);
                p += vlen;
            }
            long entrySeq = seq + i;
            if (java.util.Arrays.equals(k, key)) {
                if (entrySeq >= best.seq) {
                    best.seq = entrySeq;
                    best.value = v == null ? new byte[0] : v;
                    best.deleted = tag == 0;
                    best.source = source;
                }
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private static void consider(byte[] internalKey, byte[] value, byte[] target, Result best,
                                 String source) {
        consider(internalKey, value, target, best, source, -1, false);
    }

    private static void consider(byte[] internalKey, byte[] value, byte[] target, Result best,
                                 String source, long forcedSeq, boolean forcedDelete) {
        if (internalKey.length < 8) return;
        byte[] uk = userKey(internalKey);
        if (compare(uk, target) != 0) return;
        long trailer = readLongLE(internalKey, internalKey.length - 8);
        long seq = forcedSeq >= 0 ? forcedSeq : (trailer >>> 8);
        boolean del = forcedDelete || ((trailer & 0xFF) == 0);
        if (seq <= best.seq) return;
        best.seq = seq;
        best.deleted = del;
        best.value = value;
        best.source = source;
    }

    static byte[] userKey(byte[] internalKey) {
        if (internalKey.length <= 8) return internalKey;
        return Arrays.copyOf(internalKey, internalKey.length - 8);
    }

    private static int compare(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = a[i] & 0xFF;
            int y = b[i] & 0xFF;
            if (x != y) return x < y ? -1 : 1;
        }
        return Integer.compare(a.length, b.length);
    }

    static long readVarLong(byte[] buf, int[] pos) {
        long result = 0;
        int shift = 0;
        while (pos[0] < buf.length) {
            int b = buf[pos[0]++] & 0xFF;
            result |= ((long) (b & 0x7F)) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return result;
    }

    private static int readIntLE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static long readUInt32LE(byte[] b, int off) {
        return readIntLE(b, off) & 0xFFFFFFFFL;
    }

    private static long readLongLE(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v |= ((long) (b[off + i] & 0xFF)) << (8 * i);
        return v;
    }
}
