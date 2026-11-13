package com.skjslcy.bedrockforge;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 类型保留的基岩版 NBT 读写（小端），用于安全写回玩家数据。 */
final class NbtCodec {

    static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    static final class Tag {
        final int type;
        Object value;

        Tag(int type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private NbtCodec() {
    }

    static Tag parse(byte[] data) throws Exception {
        int[] pos = {0};
        int type = u8(data, pos);
        skipString(data, pos);
        return readPayload(type, data, pos);
    }

    static byte[] write(Tag root) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(root.type);
        writeString(out, "");
        writePayload(root, out);
        return out.toByteArray();
    }

    private static Tag readPayload(int type, byte[] d, int[] p) throws Exception {
        switch (type) {
            case BYTE:
                return new Tag(BYTE, d[p[0]++]);
            case SHORT:
                return new Tag(SHORT, (short) leShort(d, p));
            case INT:
                return new Tag(INT, leInt(d, p));
            case LONG:
                return new Tag(LONG, leLong(d, p));
            case FLOAT:
                return new Tag(FLOAT, Float.intBitsToFloat(leInt(d, p)));
            case DOUBLE:
                return new Tag(DOUBLE, Double.longBitsToDouble(leLong(d, p)));
            case STRING:
                return new Tag(STRING, readString(d, p));
            case BYTE_ARRAY: {
                int n = leInt(d, p);
                byte[] b = new byte[n];
                System.arraycopy(d, p[0], b, 0, n);
                p[0] += n;
                return new Tag(BYTE_ARRAY, b);
            }
            case LIST: {
                int et = u8(d, p);
                int n = leInt(d, p);
                List<Tag> list = new ArrayList<>();
                for (int i = 0; i < n; i++) list.add(readPayload(et, d, p));
                return new Tag(LIST, list);
            }
            case COMPOUND: {
                LinkedHashMap<String, Tag> map = new LinkedHashMap<>();
                while (true) {
                    int t = u8(d, p);
                    if (t == END) break;
                    String name = readString(d, p);
                    map.put(name, readPayload(t, d, p));
                }
                return new Tag(COMPOUND, map);
            }
            case INT_ARRAY: {
                int n = leInt(d, p);
                int[] arr = new int[n];
                for (int i = 0; i < n; i++) arr[i] = leInt(d, p);
                return new Tag(INT_ARRAY, arr);
            }
            case LONG_ARRAY: {
                int n = leInt(d, p);
                long[] arr = new long[n];
                for (int i = 0; i < n; i++) arr[i] = leLong(d, p);
                return new Tag(LONG_ARRAY, arr);
            }
            default:
                throw new Exception("未知 NBT 类型 " + type);
        }
    }

    private static void writePayload(Tag t, ByteArrayOutputStream out) throws Exception {
        switch (t.type) {
            case BYTE:
                out.write(((Number) t.value).intValue());
                break;
            case SHORT:
                writeShort(out, ((Number) t.value).intValue());
                break;
            case INT:
                writeInt(out, ((Number) t.value).intValue());
                break;
            case LONG:
                writeLong(out, ((Number) t.value).longValue());
                break;
            case FLOAT:
                writeInt(out, Float.floatToIntBits(((Number) t.value).floatValue()));
                break;
            case DOUBLE:
                writeLong(out, Double.doubleToLongBits(((Number) t.value).doubleValue()));
                break;
            case STRING:
                writeString(out, String.valueOf(t.value));
                break;
            case BYTE_ARRAY: {
                byte[] b = (byte[]) t.value;
                writeInt(out, b.length);
                out.write(b, 0, b.length);
                break;
            }
            case LIST: {
                @SuppressWarnings("unchecked")
                List<Tag> list = (List<Tag>) t.value;
                out.write(list.isEmpty() ? END : list.get(0).type);
                writeInt(out, list.size());
                for (Tag e : list) writePayload(e, out);
                break;
            }
            case COMPOUND: {
                @SuppressWarnings("unchecked")
                Map<String, Tag> map = (Map<String, Tag>) t.value;
                for (Map.Entry<String, Tag> e : map.entrySet()) {
                    out.write(e.getValue().type);
                    writeString(out, e.getKey());
                    writePayload(e.getValue(), out);
                }
                out.write(END);
                break;
            }
            case INT_ARRAY: {
                int[] a = (int[]) t.value;
                writeInt(out, a.length);
                for (int v : a) writeInt(out, v);
                break;
            }
            case LONG_ARRAY: {
                long[] a = (long[]) t.value;
                writeInt(out, a.length);
                for (long v : a) writeLong(out, v);
                break;
            }
            default:
                throw new Exception("无法写入类型 " + t.type);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Tag> map(Tag t) {
        return t != null && t.type == COMPOUND ? (Map<String, Tag>) t.value : null;
    }

    @SuppressWarnings("unchecked")
    static List<Tag> list(Tag t) {
        return t != null && t.type == LIST ? (List<Tag>) t.value : null;
    }

    private static void writeString(ByteArrayOutputStream out, String s) throws Exception {
        byte[] b = s.getBytes("UTF-8");
        writeShort(out, b.length);
        out.write(b, 0, b.length);
    }

    private static String readString(byte[] d, int[] p) throws Exception {
        int n = leShort(d, p) & 0xFFFF;
        String s = new String(d, p[0], n, "UTF-8");
        p[0] += n;
        return s;
    }

    private static void skipString(byte[] d, int[] p) throws Exception {
        int n = leShort(d, p) & 0xFFFF;
        p[0] += n;
    }

    private static int u8(byte[] d, int[] p) {
        return d[p[0]++] & 0xFF;
    }

    private static int leShort(byte[] d, int[] p) {
        int v = (d[p[0]] & 0xFF) | ((d[p[0] + 1] & 0xFF) << 8);
        p[0] += 2;
        return v;
    }

    private static int leInt(byte[] d, int[] p) {
        int v = (d[p[0]] & 0xFF) | ((d[p[0] + 1] & 0xFF) << 8)
                | ((d[p[0] + 2] & 0xFF) << 16) | ((d[p[0] + 3] & 0xFF) << 24);
        p[0] += 4;
        return v;
    }

    private static long leLong(byte[] d, int[] p) {
        long v = 0;
        for (int i = 0; i < 8; i++) v |= ((long) (d[p[0] + i] & 0xFF)) << (8 * i);
        p[0] += 8;
        return v;
    }

    private static void writeShort(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 24) & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream o, long v) {
        for (int i = 0; i < 8; i++) o.write((int) ((v >>> (8 * i)) & 0xFF));
    }
}
