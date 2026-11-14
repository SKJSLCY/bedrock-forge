package com.skjslcy.bedrockforge;

import java.io.File;
import java.io.RandomAccessFile;

/** 以 LevelDB WAL 追加方式写回玩家数据（仅在服务端停止时使用）。 */
final class LevelDbWriter {

    private static final int BLOCK = 32768;

    private LevelDbWriter() {
    }

    static void append(File dbDir, long seq, byte[] key, byte[] value) throws Exception {
        java.io.ByteArrayOutputStream batch = new java.io.ByteArrayOutputStream();
        writeLong(batch, seq);
        writeInt(batch, 1);
        batch.write(1); // put
        writeVarLong(batch, key.length);
        batch.write(key);
        writeVarLong(batch, value.length);
        batch.write(value);
        writeRecord(dbDir, batch.toByteArray());
    }

    static void appendDeletes(File dbDir, long seq, String... keys) throws Exception {
        java.io.ByteArrayOutputStream batch = new java.io.ByteArrayOutputStream();
        writeLong(batch, seq);
        writeInt(batch, keys.length);
        for (String k : keys) {
            batch.write(0); // delete
            byte[] key = k.getBytes("UTF-8");
            writeVarLong(batch, key.length);
            batch.write(key);
        }
        writeRecord(dbDir, batch.toByteArray());
    }

    static void appendPlayerOps(File dbDir, long seq, String stubKey, byte[] stub,
                                String dataKey, byte[] data, String deleteKey) throws Exception {
        java.io.ByteArrayOutputStream batch = new java.io.ByteArrayOutputStream();
        writeLong(batch, seq);
        writeInt(batch, deleteKey == null ? 2 : 3);
        putEntry(batch, stubKey, stub);
        putEntry(batch, dataKey, data);
        if (deleteKey != null) {
            batch.write(0);
            byte[] k = deleteKey.getBytes("UTF-8");
            writeVarLong(batch, k.length);
            batch.write(k);
        }
        writeRecord(dbDir, batch.toByteArray());
    }

    private static void putEntry(java.io.ByteArrayOutputStream batch, String key, byte[] value)
            throws Exception {
        batch.write(1);
        byte[] k = key.getBytes("UTF-8");
        writeVarLong(batch, k.length);
        batch.write(k);
        writeVarLong(batch, value.length);
        batch.write(value);
    }

    private static void writeRecord(File dbDir, byte[] data) throws Exception {
        java.io.File log = newestLog(dbDir);
        if (log == null) throw new Exception(I18n.t("没有可追加的 WAL 日志（请先启动一次服务端）"));

        java.io.ByteArrayOutputStream rec = new java.io.ByteArrayOutputStream();
        int type = 1; // FULL
        rec.write(type);
        rec.write(data);
        byte[] payload = rec.toByteArray();
        int crc = Crc32c.compute(payload, 0, payload.length);
        // LevelDB 日志校验使用 masked CRC32C
        crc = ((crc >>> 15) | (crc << 17)) + 0xA282EAD8;

        try (RandomAccessFile raf = new RandomAccessFile(log, "rw")) {
            long len = raf.length();
            int inBlock = (int) (len % BLOCK);
            int need = 7 + data.length;
            if (inBlock + need > BLOCK) {
                int pad = BLOCK - inBlock;
                raf.seek(len);
                raf.write(new byte[pad]);
                len += pad;
            }
            raf.seek(len);
            raf.write(crc & 0xFF);
            raf.write((crc >>> 8) & 0xFF);
            raf.write((crc >>> 16) & 0xFF);
            raf.write((crc >>> 24) & 0xFF);
            raf.write(data.length & 0xFF);
            raf.write((data.length >>> 8) & 0xFF);
            raf.write(type);
            raf.write(data);
        }
    }

    static java.io.File newestLog(java.io.File dir) {
        java.io.File best = null;
        long bestNo = -1;
        java.io.File[] files = dir.listFiles();
        if (files == null) return null;
        for (java.io.File f : files) {
            String n = f.getName();
            if (!n.endsWith(".log")) continue;
            try {
                long no = Long.parseLong(n.substring(0, n.length() - 4));
                if (no > bestNo) {
                    bestNo = no;
                    best = f;
                }
            } catch (Throwable ignored) {
            }
        }
        return best;
    }

    private static void writeLong(java.io.ByteArrayOutputStream o, long v) {
        for (int i = 0; i < 8; i++) o.write((int) ((v >>> (8 * i)) & 0xFF));
    }

    private static void writeInt(java.io.ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 24) & 0xFF);
    }

    private static void writeVarLong(java.io.ByteArrayOutputStream o, long v) {
        while ((v & ~0x7FL) != 0) {
            o.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        o.write((int) v);
    }
}
