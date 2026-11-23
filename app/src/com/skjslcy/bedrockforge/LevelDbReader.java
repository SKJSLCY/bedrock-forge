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
        java.util.LinkedHashMap<String, byte[]> out = list(dbDir, prefix, true);
        if (out.isEmpty()) {
            // 兜底：万一 MANIFEST 的键范围判定不成立，退回全量扫描，宁可慢也不能漏
            out = list(dbDir, prefix, false);
        }
        return out;
    }

    /** 本次扫描统计（诊断用）。 */
    static int scannedTables = 0;
    static int skippedTables = 0;

    private static java.util.LinkedHashMap<String, byte[]> list(File dbDir, String prefix,
                                                                boolean useRange)
            throws Exception {
        java.util.LinkedHashMap<String, byte[]> out = new java.util.LinkedHashMap<>();
        java.util.HashMap<String, Long> seqs = new java.util.HashMap<>();
        File[] files = dbDir.listFiles();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        byte[] lo = prefix.getBytes("UTF-8");
        byte[] hi = upperBound(lo);
        Manifest man = useRange ? Manifest.read(dbDir) : null;
        scannedTables = 0;
        skippedTables = 0;
        for (File f : files) {
            String n = f.getName();
            if (!(n.endsWith(".ldb") || n.endsWith(".sst"))) continue;
            if (man != null && man.ok && !man.mayContain(fileNumber(n), lo, hi)) {
                skippedTables++;
                continue;
            }
            scannedTables++;
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

    /** prefix 的上界：prefix + 0xFF（任何以 prefix 开头的键都小于它）。 */
    private static byte[] upperBound(byte[] prefix) {
        byte[] hi = Arrays.copyOf(prefix, prefix.length + 1);
        hi[prefix.length] = (byte) 0xFF;
        return hi;
    }

    /** 文件名里的编号（SST 都是纯数字名）。 */
    static long fileNumber(String name) {
        int dot = name.indexOf('.');
        String s = dot > 0 ? name.substring(0, dot) : name;
        try {
            return Long.parseLong(s);
        } catch (Throwable ignored) {
            return -1;
        }
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
                                   final java.util.LinkedHashMap<String, byte[]> out,
                                   final java.util.HashMap<String, Long> seqs) throws Exception {
        eachRecord(file, (type, payload) -> batchAll(payload, 0, payload.length, prefix, out, seqs));
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
        Result best = get(dbDir, key, true);
        if (best.seq < 0) {
            // 兜底：范围判定没命中时退回全量扫描
            best = get(dbDir, key, false);
        }
        return best;
    }

    private static Result get(File dbDir, byte[] key, boolean useRange) throws Exception {
        Result best = new Result();
        File[] files = dbDir.listFiles();
        if (files == null) throw new Exception(I18n.t("无法读取数据库目录"));
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        Manifest man = useRange ? Manifest.read(dbDir) : null;

        for (File f : files) {
            String n = f.getName();
            if (!(n.endsWith(".ldb") || n.endsWith(".sst"))) continue;
            if (man != null && man.ok && !man.mayContain(fileNumber(n), key, key)) continue;
            try {
                scanTable(f, key, best);
            } catch (Throwable ignored) {
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

    /**
     * 取当前最大 sequence（用于追加 WAL）。
     *
     * <p>口径：MANIFEST 的 {@code last_sequence} 与所有 .log 里分片记录的最大 seq 取大。
     * MANIFEST 每次 {@code LogAndApply}（落盘/压缩）都会写入当时的 LastSequence，
     * 它必然 ≥ 已在 .ldb 里的任何条目，所以不必再全量扫 .ldb（旧实现要扫 2 GB，
     * 且漏掉分片记录会把 sequence 算小，导致新记录被更"新"的旧记录压住）。
     */
    static long lastSequence(File dbDir) throws Exception {
        long max = 0;
        Manifest man = Manifest.read(dbDir);
        if (man.lastSequence > max) max = man.lastSequence;
        File[] files = dbDir.listFiles();
        if (files == null) return max;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : files) {
            if (!f.getName().endsWith(".log")) continue;
            final long[] logMax = {0};
            try {
                eachRecord(f, (type, payload) -> {
                    if (payload.length < 12) return;
                    long seq = readLongLE(payload, 0);
                    int count = readIntLE(payload, 8);
                    if (count > 0 && seq + count - 1 > logMax[0]) logMax[0] = seq + count - 1;
                });
            } catch (Throwable ignored) {
            }
            if (logMax[0] > max) max = logMax[0];
        }
        return max;
    }

    // ------------------------------------------------------------- MANIFEST

    /** MANIFEST 里真正影响写回的几个字段（VersionEdit 语义）。 */
    static final class Manifest {
        long logNumber = -1;
        long prevLogNumber = -1;
        long nextFileNumber = -1;
        long lastSequence = -1;
        String comparator = "";
        boolean ok;

        /** 文件编号 → {最小键, 最大键}（已去掉 8 字节内部前缀）。用于跳过不可能命中的 SST。 */
        final java.util.HashMap<Long, byte[][]> ranges = new java.util.HashMap<>();

        /** 该文件是否可能与 [lo, hi] 区间有交集；无信息时按「可能有」处理。 */
        boolean mayContain(long number, byte[] lo, byte[] hi) {
            byte[][] r = ranges.get(number);
            if (r == null || r[0] == null || r[1] == null) return true;
            return compare(r[0], hi) <= 0 && compare(r[1], lo) >= 0;
        }

        static Manifest read(File dbDir) {
            Manifest m = new Manifest();
            try {
                File current = new File(dbDir, "CURRENT");
                if (!current.exists()) return m;
                String name = new String(readAll(current), "UTF-8").trim();
                if (name.isEmpty()) return m;
                File man = new File(dbDir, name);
                if (!man.exists()) return m;
                eachRecord(man, (type, payload) -> m.apply(payload));
                m.ok = true;
            } catch (Throwable ignored) {
                m.ok = false;
            }
            return m;
        }

        private void apply(byte[] r) {
            if (r.length < 12) return;
            int[] p = {0};
            while (p[0] < r.length) {
                long tag = readVarLong(r, p);
                if (tag == 1) {                                   // comparator
                    long n = readVarLong(r, p);
                    if (n < 0 || p[0] + n > r.length) return;
                    try {
                        comparator = new String(r, p[0], (int) n, "UTF-8");
                    } catch (Throwable ignored) {
                    }
                    p[0] += (int) n;
                } else if (tag == 2) {                            // log_number
                    logNumber = readVarLong(r, p);
                } else if (tag == 3) {                            // next_file_number
                    nextFileNumber = readVarLong(r, p);
                } else if (tag == 4) {                            // last_sequence
                    lastSequence = readVarLong(r, p);
                } else if (tag == 5) {                            // compact_pointer
                    readVarLong(r, p);
                    skipSlice(r, p);
                } else if (tag == 6) {                            // deleted_file
                    readVarLong(r, p);
                    long num = readVarLong(r, p);
                    ranges.remove(num);
                } else if (tag == 7) {                            // new_file
                    readVarLong(r, p);
                    long num = readVarLong(r, p);
                    readVarLong(r, p);                            // file size
                    byte[] sm = slice(r, p);
                    byte[] lg = slice(r, p);
                    if (num >= 0) {
                        ranges.put(num, new byte[][]{userKey(sm), userKey(lg)});
                    }
                } else if (tag == 9) {                            // prev_log_number
                    prevLogNumber = readVarLong(r, p);
                } else {
                    return;
                }
            }
        }

        private static byte[] slice(byte[] r, int[] p) {
            long n = readVarLong(r, p);
            if (n < 0 || p[0] + n > r.length) {
                p[0] = r.length;
                return new byte[0];
            }
            byte[] out = Arrays.copyOfRange(r, p[0], p[0] + (int) n);
            p[0] += (int) n;
            return out;
        }

        private static void skipSlice(byte[] r, int[] p) {
            long n = readVarLong(r, p);
            if (n < 0 || p[0] + n > r.length) {
                p[0] = r.length;
                return;
            }
            p[0] += (int) n;
        }
    }

    private static byte[] readAll(File f) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] b = new byte[(int) raf.length()];
            raf.readFully(b);
            return b;
        }
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
        if (off < 0 || size <= 0 || off + size > fileLen) throw new Exception(I18n.t("块越界"));
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
                    if (bos.size() > (64 << 20)) throw new Exception(I18n.t("块解压过大"));
                }
            }
            if (bos.size() == 0) throw new Exception(I18n.t("块解压为空"));
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

    private static void scanLog(File file, byte[] key, final Result best) throws Exception {
        final String source = file.getName();
        eachRecord(file, (type, payload) ->
                handleBatch(payload, 0, payload.length, key, best, source));
    }

    interface RecordSink {
        void accept(int type, byte[] payload) throws Exception;
    }

    /**
     * 按 LevelDB 的读法遍历日志里的逻辑记录：跳过零填充，拼接 FIRST/MIDDLE/LAST 分片。
     * 记录长度越界即停止（与服务端恢复时的行为一致）。
     */
    static void eachRecord(File log, RecordSink sink) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(log, "r")) {
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
                        byte[] payload = new byte[length];
                        System.arraycopy(block, p, payload, 0, length);
                        sink.accept(type, payload);
                        pending = null;
                    } else if (type == 2) {
                        pending = new java.io.ByteArrayOutputStream();
                        pending.write(block, p, length);
                    } else if (type == 3 && pending != null) {
                        pending.write(block, p, length);
                    } else if (type == 4 && pending != null) {
                        pending.write(block, p, length);
                        sink.accept(type, pending.toByteArray());
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

    static int readIntLE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static long readUInt32LE(byte[] b, int off) {
        return readIntLE(b, off) & 0xFFFFFFFFL;
    }

    static long readLongLE(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v |= ((long) (b[off + i] & 0xFF)) << (8 * i);
        return v;
    }
}
