package com.skjslcy.bedrockforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 基岩版小端 NBT 解析（只读）。 */
final class BedrockNbt {

    private final byte[] buf;
    private int pos;

    private BedrockNbt(byte[] buf) {
        this.buf = buf;
    }

    static Map<String, Object> parse(byte[] data) throws Exception {
        BedrockNbt r = new BedrockNbt(data);
        int type = r.u8();
        if (type != 10) throw new Exception("根不是 Compound（type=" + type + "）");
        r.readStringU16(); // 根名字通常为空
        return r.readCompoundBody();
    }

    // ------------------------------------------------------------ readers

    private int u8() throws Exception {
        if (pos >= buf.length) throw new Exception("NBT 越界");
        return buf[pos++] & 0xFF;
    }

    private int i32() throws Exception {
        need(4);
        int v = (buf[pos] & 0xFF) | ((buf[pos + 1] & 0xFF) << 8)
                | ((buf[pos + 2] & 0xFF) << 16) | ((buf[pos + 3] & 0xFF) << 24);
        pos += 4;
        return v;
    }

    private long i64() throws Exception {
        need(8);
        long v = 0;
        for (int i = 0; i < 8; i++) v |= ((long) (buf[pos + i] & 0xFF)) << (8 * i);
        pos += 8;
        return v;
    }

    private void need(int n) throws Exception {
        if (pos + n > buf.length) throw new Exception("NBT 越界");
    }

    private String readStringU16() throws Exception {
        need(2);
        int len = (buf[pos] & 0xFF) | ((buf[pos + 1] & 0xFF) << 8);
        pos += 2;
        need(len);
        String s = new String(buf, pos, len, "UTF-8");
        pos += len;
        return s;
    }

    private Map<String, Object> readCompoundBody() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            int type = u8();
            if (type == 0) break;
            String name = readStringU16();
            Object v = readPayload(type);
            if (name.isEmpty()) name = "(空)";
            map.put(name, v);
        }
        return map;
    }

    private Object readPayload(int type) throws Exception {
        switch (type) {
            case 1: {
                need(1);
                byte b = buf[pos++];
                return b;
            }
            case 2: {
                need(2);
                short s = (short) ((buf[pos] & 0xFF) | ((buf[pos + 1] & 0xFF) << 8));
                pos += 2;
                return s;
            }
            case 3:
                return i32();
            case 4:
                return i64();
            case 5: {
                need(4);
                float f = Float.intBitsToFloat(i32());
                return f;
            }
            case 6: {
                need(8);
                double d = Double.longBitsToDouble(i64());
                return d;
            }
            case 7: {
                int n = i32();
                if (n < 0 || n > (16 << 20)) throw new Exception("byte[] 长度异常");
                need(n);
                byte[] a = new byte[n];
                System.arraycopy(buf, pos, a, 0, n);
                pos += n;
                return a;
            }
            case 8:
                return readStringU16();
            case 9: {
                int elem = u8();
                int n = i32();
                if (n < 0 || n > 4000000) throw new Exception("list 长度异常");
                List<Object> list = new ArrayList<>(Math.min(n, 256));
                for (int i = 0; i < n; i++) list.add(readPayload(elem));
                return list;
            }
            case 10:
                return readCompoundBody();
            case 11: {
                int n = i32();
                if (n < 0 || n > 4000000) throw new Exception("int[] 长度异常");
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = i32();
                return a;
            }
            case 12: {
                int n = i32();
                if (n < 0 || n > 4000000) throw new Exception("long[] 长度异常");
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = i64();
                return a;
            }
            default:
                throw new Exception("未知 NBT 类型：" + type);
        }
    }

    // ------------------------------------------------------------ helpers

    static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? cast(o) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    static Double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return null;
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
