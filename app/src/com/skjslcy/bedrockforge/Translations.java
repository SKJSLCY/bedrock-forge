package com.skjslcy.bedrockforge;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** 内置翻译表（assets/i18n），不依赖服务端语言文件。 */
final class Translations {

    private static boolean loaded;
    private static final Map<String, String> FIELDS = new HashMap<>();
    private static final Map<String, String> ATTRS = new HashMap<>();
    private static final Map<String, String> ITEMS = new HashMap<>();
    private static final Map<Integer, String> EFFECTS = new HashMap<>();
    private static final Map<Integer, String> ENCHANTS = new HashMap<>();

    private Translations() {
    }

    static synchronized void ensureLoaded(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject f = new JSONObject(readAsset(c, "i18n/fields.json"));
            Iterator<String> it = f.keys();
            while (it.hasNext()) {
                String k = it.next();
                FIELDS.put(k, f.optString(k));
            }
        } catch (Throwable ignored) {
        }
        try {
            JSONObject a = new JSONObject(readAsset(c, "i18n/attributes.json"));
            Iterator<String> it = a.keys();
            while (it.hasNext()) {
                String k = it.next();
                ATTRS.put(k, a.optString(k));
            }
        } catch (Throwable ignored) {
        }
        try {
            JSONObject e = new JSONObject(readAsset(c, "i18n/effects.json"));
            Iterator<String> it = e.keys();
            while (it.hasNext()) {
                String k = it.next();
                EFFECTS.put(Integer.parseInt(k), e.optString(k));
            }
        } catch (Throwable ignored) {
        }
        try {
            JSONObject e = new JSONObject(readAsset(c, "i18n/enchants.json"));
            Iterator<String> it = e.keys();
            while (it.hasNext()) {
                String k = it.next();
                ENCHANTS.put(Integer.parseInt(k), e.optString(k));
            }
        } catch (Throwable ignored) {
        }
        try {
            for (String line : readAsset(c, "i18n/item_names.tsv").split("\n")) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                ITEMS.put(line.substring(0, tab).trim(), line.substring(tab + 1).trim());
            }
        } catch (Throwable ignored) {
        }
    }

    private static String readAsset(Context c, String name) throws Exception {
        InputStream in = c.getAssets().open(name);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    static String field(String key) {
        return FIELDS.get(key);
    }

    static String item(String id) {
        return ITEMS.get(id);
    }

    static String attr(String name) {
        String v = ATTRS.get(name);
        return v == null ? name : v;
    }

    static String effect(Object id) {
        Integer i = asInt(id);
        if (i == null) return String.valueOf(id);
        String v = EFFECTS.get(i);
        return v == null ? "未知效果（ID " + i + "）" : v;
    }

    static String enchant(Object id) {
        Integer i = asInt(id);
        if (i == null) return String.valueOf(id);
        String v = ENCHANTS.get(i);
        return v == null ? "未知附魔（ID " + i + "）" : v;
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Throwable t) {
            return null;
        }
    }

    static String dim(Object v) {
        Integer i = asInt(v);
        if (i == null) return String.valueOf(v);
        switch (i) {
            case 0: return "主世界（0）";
            case 1: return "下界（1）";
            case 2: return "末地（2）";
            case 3: return "世界默认（3）";
            default: return String.valueOf(i);
        }
    }

    static String gamemode(Object v) {
        Integer i = asInt(v);
        if (i == null) return String.valueOf(v);
        switch (i) {
            case 0: return "生存（0）";
            case 1: return "创造（1）";
            case 2: return "冒险（2）";
            case 5: return "默认（5）";
            case 6: return "旁观（6）";
            default: return String.valueOf(i);
        }
    }

    static String permission(Object v) {
        Integer i = asInt(v);
        if (i == null) return String.valueOf(v);
        switch (i) {
            case 0: return "访客（0）";
            case 1: return "成员（1）";
            case 2: return "操作员（2）";
            case 3: return "自定义（3）";
            default: return String.valueOf(i);
        }
    }

    static String yesNo(Object v) {
        if (v instanceof Boolean) return ((Boolean) v) ? "是" : "否";
        Integer i = asInt(v);
        if (i != null) return i != 0 ? "是" : "否";
        return String.valueOf(v);
    }

    static String num(Object v) {
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (Math.abs(d - Math.rint(d)) < 0.0001) {
                return String.valueOf((long) Math.rint(d));
            }
            return String.format(Locale.US, "%.2f", d);
        }
        return String.valueOf(v);
    }
}
