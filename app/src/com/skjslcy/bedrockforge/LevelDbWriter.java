package com.skjslcy.bedrockforge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;

/**
 * 以 LevelDB WAL 追加方式写回玩家数据（仅在服务端停止时使用）。
 *
 * <p>必须遵守 {@code log::Writer} 的物理记录约束：块 32768 字节、记录头 7 字节、
 * <b>单条物理记录载荷最长 32761 字节</b>，超出要切成 FIRST / MIDDLE / LAST 分片。
 * 旧实现永远只写一条 FULL 记录，超过 32761 字节时 {@code log::Reader} 判为截断并
 * 丢弃所在块的剩余部分——表现就是「写入无报错，但记录从未进入数据库」。
 * 大存档玩家数据 40~120 KB，正好全部落在这一区间；小世界 7 KB 所以一直正常。
 */
final class LevelDbWriter {

    static final int BLOCK = 32768;
    /** 物理记录头：4 字节 masked CRC32C + 2 字节长度 + 1 字节类型。 */
    static final int HEADER = 7;
    /** 单条物理记录的最大载荷。 */
    static final int MAX_FRAGMENT = BLOCK - HEADER;

    private static final int TYPE_FULL = 1;
    private static final int TYPE_FIRST = 2;
    private static final int TYPE_MIDDLE = 3;
    private static final int TYPE_LAST = 4;

    private static final int OP_DELETE = 0;
    private static final int OP_PUT = 1;

    private LevelDbWriter() {
    }

    // ------------------------------------------------------------ WriteBatch

    /** 一条 WriteBatch 里的操作。 */
    static final class Op {
        final int tag;
        final String key;
        final byte[] value;

        private Op(int tag, String key, byte[] value) {
            this.tag = tag;
            this.key = key;
            this.value = value;
        }

        static Op put(String key, byte[] value) {
            return new Op(OP_PUT, key, value);
        }

        static Op delete(String key) {
            return new Op(OP_DELETE, key, null);
        }
    }

    /** 拼一条 WriteBatch：8 字节 sequence + 4 字节条目数 + 逐条 (tag, key, value)。 */
    static byte[] batch(long seq, Op... ops) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLong(out, seq);
        writeInt(out, ops.length);
        for (Op op : ops) {
            out.write(op.tag);
            byte[] k = op.key.getBytes("UTF-8");
            writeVarLong(out, k.length);
            out.write(k);
            if (op.tag == OP_PUT) {
                writeVarLong(out, op.value.length);
                out.write(op.value);
            }
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------ 追加

    /**
     * 把一条 WriteBatch 追加到日志末尾，自动分片。返回该记录在文件中的起始偏移。
     */
    static long appendBatch(File log, byte[] data) throws Exception {
        long start;
        try (RandomAccessFile raf = new RandomAccessFile(log, "rw")) {
            long pos = raf.length();
            start = pos;
            raf.seek(pos);
            int off = 0;
            int left = data.length;
            boolean began = false;
            while (left > 0) {
                int room = BLOCK - (int) (pos % BLOCK);
                if (room < HEADER) {
                    // 块尾不足一个记录头：按 LevelDB 语义补零跨块
                    raf.write(new byte[room]);
                    pos += room;
                    continue;
                }
                int frag = Math.min(left, room - HEADER);
                boolean end = frag == left;
                int type;
                if (!began) {
                    type = end ? TYPE_FULL : TYPE_FIRST;
                } else {
                    type = end ? TYPE_LAST : TYPE_MIDDLE;
                }
                byte[] payload = new byte[frag + 1];
                payload[0] = (byte) type;
                System.arraycopy(data, off, payload, 1, frag);
                int crc = Crc32c.compute(payload, 0, payload.length);
                // LevelDB 日志校验使用 masked CRC32C
                crc = ((crc >>> 15) | (crc << 17)) + 0xA282EAD8;
                byte[] hdr = new byte[HEADER];
                hdr[0] = (byte) crc;
                hdr[1] = (byte) (crc >>> 8);
                hdr[2] = (byte) (crc >>> 16);
                hdr[3] = (byte) (crc >>> 24);
                hdr[4] = (byte) frag;
                hdr[5] = (byte) (frag >>> 8);
                hdr[6] = (byte) type;
                raf.write(hdr);
                raf.write(data, off, frag);
                pos += HEADER + frag;
                off += frag;
                left -= frag;
                began = true;
            }
            raf.getFD().sync();
        }
        return start;
    }

    /**
     * 把本地日志截回指定长度（回滚一次追加）。
     * 追加式写入只影响文件尾部，所以截断就等于撤销，不需要整世界备份。
     */
    static void truncateLocal(File log, long size) throws Exception {
        if (log == null || !log.exists() || size < 0) return;
        if (log.length() <= size) return;
        try (RandomAccessFile raf = new RandomAccessFile(log, "rw")) {
            raf.setLength(size);
            raf.getFD().sync();
        }
    }

    /**
     * 写前自检：整个日志按 LevelDB 的读法是否干净。
     * 有截断 / CRC 不符 / 非零填充就抛异常——继续追加只会让新记录跟着被丢弃。
     * 通过时返回可追加偏移（等于文件长度）。
     */
    static long checkedTail(File log) throws Exception {
        if (!log.exists()) return 0;
        try (RandomAccessFile raf = new RandomAccessFile(log, "r")) {
            long len = raf.length();
            byte[] block = new byte[BLOCK];
            long pos = 0;
            while (pos < len) {
                int blockLen = (int) Math.min(BLOCK, len - pos);
                raf.seek(pos);
                raf.readFully(block, 0, blockLen);
                int p = 0;
                while (p + HEADER <= blockLen) {
                    int length = (block[p + 4] & 0xFF) | ((block[p + 5] & 0xFF) << 8);
                    int type = block[p + 6] & 0xFF;
                    if (type == 0 && length == 0) {
                        for (int i = p; i < blockLen; i++) {
                            if (block[i] != 0) {
                                throw new Exception(I18n.t("日志第 ") + (pos + p)
                                        + I18n.t(" 字节处的填充非零，请先启动一次服务端让日志轮转"));
                            }
                        }
                        p = blockLen;
                        break;
                    }
                    if (p + HEADER + length > blockLen) {
                        throw new Exception(I18n.t("日志第 ") + (pos + p)
                                + I18n.t(" 字节处的记录被截断，请先启动一次服务端让日志轮转"));
                    }
                    int stored = LevelDbReader.readIntLE(block, p);
                    int calc = Crc32c.compute(block, p + 6, 1 + length);
                    calc = ((calc >>> 15) | (calc << 17)) + 0xA282EAD8;
                    if (stored != calc) {
                        throw new Exception(I18n.t("日志第 ") + (pos + p)
                                + I18n.t(" 字节处的校验和不符，请先启动一次服务端让日志轮转"));
                    }
                    p += HEADER + length;
                }
                pos += blockLen;
            }
            return len;
        }
    }

    /** 日志里是否有一条 sequence 与内容都匹配的记录（分片与 CRC 已校验）。 */
    static boolean containsBatch(File log, long seq, String key, byte[] value) throws Exception {
        return contains(log, seq, Op.put(key, value));
    }

    /** 日志里是否有一条删除记录包含全部给定的键（分片与 CRC 已校验）。 */
    static boolean containsDeletes(File log, long seq, String... keys) throws Exception {
        Op[] ops = new Op[keys.length];
        for (int i = 0; i < keys.length; i++) ops[i] = Op.delete(keys[i]);
        return contains(log, seq, ops);
    }

    static boolean contains(File log, long seq, Op... expect) throws Exception {
        final boolean[] found = {false};
        LevelDbReader.eachRecord(log, (type, payload) -> {
            if (found[0] || payload.length < 12) return;
            if (LevelDbReader.readLongLE(payload, 0) != seq) return;
            if (batchHas(payload, expect)) found[0] = true;
        });
        return found[0];
    }

    private static boolean batchHas(byte[] batch, Op[] expect) {
        int count = LevelDbReader.readIntLE(batch, 8);
        if (count != expect.length) return false;
        int p = 12;
        for (Op op : expect) {
            if (p >= batch.length) return false;
            int tag = batch[p++] & 0xFF;
            if (tag != op.tag) return false;
            int[] q = {p};
            long klen = LevelDbReader.readVarLong(batch, q);
            p = q[0];
            if (klen < 0 || p + klen > batch.length) return false;
            String key;
            try {
                key = new String(batch, p, (int) klen, "UTF-8");
            } catch (Exception e) {
                return false;
            }
            p += klen;
            if (!key.equals(op.key)) return false;
            if (tag == OP_PUT) {
                q[0] = p;
                long vlen = LevelDbReader.readVarLong(batch, q);
                p = q[0];
                if (vlen < 0 || p + vlen > batch.length) return false;
                byte[] v = new byte[(int) vlen];
                System.arraycopy(batch, p, v, 0, (int) vlen);
                p += vlen;
                if (!java.util.Arrays.equals(v, op.value)) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ 日志文件定位

    /** 日志文件名里的编号；不是日志返回 -1。 */
    static long logNumber(File f) {
        String n = f.getName();
        if (!n.endsWith(".log")) return -1;
        try {
            return Long.parseLong(n.substring(0, n.length() - 4));
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * 选出「会被服务端重放」的日志：编号 ≥ MANIFEST 的 log_number。
     * 一个都没有时按 next_file_number 新建——LevelDB 恢复只要求编号 ≥ log_number，
     * 新建编号一定更大，所以同样会被重放。
     */
    static File ensureLog(File dbDir, LevelDbReader.Manifest man) throws Exception {
        File best = null;
        long bestNo = -1;
        File[] files = dbDir.listFiles();
        if (files != null) {
            for (File f : files) {
                long no = logNumber(f);
                if (no < 0) continue;
                if (man.logNumber >= 0 && no < man.logNumber) continue;
                if (no > bestNo) {
                    bestNo = no;
                    best = f;
                }
            }
        }
        if (best != null) return best;
        long no = man.nextFileNumber;
        if (no <= 0) {
            no = 1;
            if (files != null) {
                for (File f : files) {
                    String n = f.getName();
                    int dot = n.indexOf('.');
                    if (dot <= 0) continue;
                    try {
                        no = Math.max(no, Long.parseLong(n.substring(0, dot)) + 1);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        File log = new File(dbDir, String.format(java.util.Locale.US, "%06d.log", no));
        if (!log.exists() && !log.createNewFile()) {
            throw new Exception(I18n.t("无法创建 WAL 日志：") + log.getName());
        }
        return log;
    }

    // ------------------------------------------------------------ 小工具

    private static void writeLong(ByteArrayOutputStream o, long v) {
        for (int i = 0; i < 8; i++) o.write((int) ((v >>> (8 * i)) & 0xFF));
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 24) & 0xFF);
    }

    private static void writeVarLong(ByteArrayOutputStream o, long v) {
        while ((v & ~0x7FL) != 0) {
            o.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        o.write((int) v);
    }


}
