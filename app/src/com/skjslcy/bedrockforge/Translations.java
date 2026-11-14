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

    /** 切换语言后丢弃缓存，下次读取时按新语言重新加载。 */
    static synchronized void invalidate() {
        loaded = false;
        FIELDS.clear();
        ATTRS.clear();
        ITEMS.clear();
        EFFECTS.clear();
        ENCHANTS.clear();
    }

    static synchronized void ensureLoaded(Context c) {
        if (loaded) return;
        loaded = true;
        loadDir(c, "");
        if (!I18n.ZH_CN.equals(I18n.lang())) loadDir(c, I18n.lang() + "/");
    }

    /** 先读简体中文原文，再用当前语言目录覆盖；缺失词条回退原文。 */
    private static void loadDir(Context c, String dir) {
        try {
            JSONObject f = new JSONObject(readAsset(c, "i18n/" + dir + "fields.json"));
            Iterator<String> it = f.keys();
            while (it.hasNext()) {
                String k = it.next();
                FIELDS.put(k, f.optString(k));
            }
        } catch (Throwable ignored) {
        }
        try {
            JSONObject a = new JSONObject(readAsset(c, "i18n/" + dir + "attributes.json"));
            Iterator<String> it = a.keys();
            while (it.hasNext()) {
                String k = it.next();
                ATTRS.put(k, a.optString(k));
            }
        } catch (Throwable ignored) {
        }
        try {
            JSONObject e = new JSONObject(readAsset(c, "i18n/" + dir + "effects.json"));
            Iterator<String> it = e.keys();
            while (it.hasNext()) {
                String k = it.next();
                EFFECTS.put(Integer.parseInt(k), e.optString(k));
            }
        } catch (Throwable ignored) {
        }
        try {
            JSONObject e = new JSONObject(readAsset(c, "i18n/" + dir + "enchants.json"));
            Iterator<String> it = e.keys();
            while (it.hasNext()) {
                String k = it.next();
                ENCHANTS.put(Integer.parseInt(k), e.optString(k));
            }
        } catch (Throwable ignored) {
        }
        try {
            for (String line : readAsset(c, "i18n/" + dir + "item_names.tsv").split("\n")) {
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
        return v == null ? I18n.t("未知效果（ID ") + i + I18n.t("）") : v;
    }

    static String enchant(Object id) {
        Integer i = asInt(id);
        if (i == null) return String.valueOf(id);
        String v = ENCHANTS.get(i);
        return v == null ? I18n.t("未知附魔（ID ") + i + I18n.t("）") : v;
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
            case 0: return I18n.t("主世界（0）");
            case 1: return I18n.t("下界（1）");
            case 2: return I18n.t("末地（2）");
            case 3: return I18n.t("世界默认（3）");
            default: return String.valueOf(i);
        }
    }

    static String gamemode(Object v) {
        Integer i = asInt(v);
        if (i == null) return String.valueOf(v);
        switch (i) {
            case 0: return I18n.t("生存（0）");
            case 1: return I18n.t("创造（1）");
            case 2: return I18n.t("冒险（2）");
            case 5: return I18n.t("默认（5）");
            case 6: return I18n.t("旁观（6）");
            default: return String.valueOf(i);
        }
    }

    static String permission(Object v) {
        Integer i = asInt(v);
        if (i == null) return String.valueOf(v);
        switch (i) {
            case 0: return I18n.t("访客（0）");
            case 1: return I18n.t("成员（1）");
            case 2: return I18n.t("操作员（2）");
            case 3: return I18n.t("自定义（3）");
            default: return String.valueOf(i);
        }
    }

    static String yesNo(Object v) {
        if (v instanceof Boolean) return ((Boolean) v) ? I18n.t("是") : I18n.t("否");
        Integer i = asInt(v);
        if (i != null) return i != 0 ? I18n.t("是") : I18n.t("否");
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
