package com.skjslcy.bedrockforge;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 界面语言：简体中文 / 繁體中文 / English。
 *
 * 词条以简体中文原文作为键，翻译表放在 assets/i18n/ui/lang_&lt;code&gt;.json。
 * 简体中文直接返回原文，其余语言查表；查不到时回退原文，保证界面不会出现空白。
 */
final class I18n {

    static final String ZH_CN = "zh_CN";
    static final String ZH_TW = "zh_TW";
    static final String EN = "en";

    /** 语言选择对话框中的展示名（各自使用本语言书写）。 */
    static final String[][] LANGUAGES = {
            {ZH_CN, "简体中文"},
            {ZH_TW, "繁體中文"},
            {EN, "English"}
    };

    private static final Map<String, String> map = new HashMap<>();
    private static Context appContext;
    private static String lang = ZH_CN;
    private static boolean loaded;

    private I18n() {
    }

    static void init(Context c) {
        if (c != null) appContext = c.getApplicationContext();
        String l = prefs(c).getString("language", ZH_CN);
        lang = normalize(l);
        loaded = false;
        map.clear();
        load();
        Translations.invalidate();
    }

    /** 兜底：万一某个静态路径早于 {@link #init(Context)} 调用，也能拿到译文。 */
    static void ensure(Context c) {
        if (c != null && appContext == null) appContext = c.getApplicationContext();
        if (!loaded) load();
    }

    private static void load() {
        loaded = true;
        if (appContext == null || ZH_CN.equals(lang)) return;
        JSONObject o = readJson(assetFile(lang));
        if (o == null) return;
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            String v = o.optString(k, null);
            // 允许空译文：用于在英文中消去中文量词等冗余片段。
            if (v != null) map.put(k, v);
        }
    }

    private static android.content.SharedPreferences prefs(Context c) {
        if (c == null) return null;
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE);
    }

    static String normalize(String l) {
        if (ZH_TW.equals(l) || "zh_TW".equals(l) || "zh-Hant".equals(l)
                || "zh_HK".equals(l) || "zh_TW_".equals(l)) return ZH_TW;
        if (EN.equals(l) || "en_US".equals(l) || "en_GB".equals(l)) return EN;
        return ZH_CN;
    }

    static String lang(Context c) {
        android.content.SharedPreferences p = prefs(c);
        return p == null ? lang : normalize(p.getString("language", ZH_CN));
    }

    static String lang() {
        return lang;
    }

    static void setLang(Context c, String l) {
        String old = lang;
        lang = normalize(l);
        android.content.SharedPreferences p = prefs(c);
        // 用 commit 同步落盘：apply 是异步的，切换后立刻重建/被杀进程时可能丢失写入。
        if (p != null && !p.edit().putString("language", lang).commit()) {
            lang = old;
        }
        loaded = false;
        map.clear();
        load();
        Translations.invalidate();
    }

    /** 当前语言的展示名（简体中文 / 繁體中文 / English）。 */
    static String label() {
        for (String[] l : LANGUAGES) {
            if (l[0].equals(lang)) return l[1];
        }
        return LANGUAGES[0][1];
    }

    static boolean isChinese() {
        return ZH_CN.equals(lang) || ZH_TW.equals(lang);
    }

    /** 资源文件名后缀：简体中文为原文文件，其余语言带后缀副本。 */
    static String suffix() {
        if (ZH_TW.equals(lang)) return "_zh_TW";
        if (EN.equals(lang)) return "_en";
        return "";
    }

    /**
     * 把 assets 中的中文文件映射到当前语言副本，例如
     * legal/user_agreement.txt → legal/user_agreement_en.txt。
     */
    static String asset(String path) {
        String s = suffix();
        if (s.isEmpty() || path == null) return path;
        int dot = path.lastIndexOf('.');
        if (dot <= 0 || dot < path.lastIndexOf('/')) return path + s;
        return path.substring(0, dot) + s + path.substring(dot);
    }

    /** 翻译一段界面文字；简体中文原样返回。 */
    static String t(String s) {
        if (s == null || s.isEmpty()) return s;
        if (!loaded) load();
        if (map.isEmpty()) return s;
        String v = map.get(s);
        return v == null ? s : v;
    }

    /** 拉丁文字之间需要空格，中文之间不需要；拼接型文案用它连接。 */
    static String sp() {
        return EN.equals(lang) ? " " : "";
    }

    /**
     * 多行文本逐行翻译（用于内置 AI 提示词这类长段文本）。
     * 词条可能带一个或两个换行，因此按“整段 → 行+\n\n → 行+\n → 行”依次匹配。
     */
    static String tl(String block) {
        if (block == null || block.isEmpty()) return block;
        if (!loaded) load();
        if (map.isEmpty()) return block;
        String whole = map.get(block);
        if (whole != null) return whole;
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder(block.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean last = i == lines.length - 1;
            if (line.isEmpty()) {
                if (!last) sb.append('\n');
                continue;
            }
            if (i + 1 < lines.length && lines[i + 1].isEmpty()) {
                String two = map.get(line + "\n\n");
                if (two != null) {
                    sb.append(two);
                    i++;
                    continue;
                }
            }
            String one = map.get(line + "\n");
            if (one != null) {
                sb.append(one);
                if (!last && !one.endsWith("\n")) sb.append('\n');
                continue;
            }
            String bare = map.get(line);
            sb.append(bare != null ? bare : line);
            if (!last) sb.append('\n');
        }
        return sb.toString();
    }

    /** 单数/复数选择：中文两个键值相同，英文分开。 */
    static String n(int count, String oneKey, String manyKey) {
        return t(count == 1 ? oneKey : manyKey);
    }

    /** 带参数的翻译，原文中使用 %s / %1$s 占位。 */
    static String f(String s, Object... args) {
        String v = t(s);
        if (args == null || args.length == 0) return v;
        try {
            return String.format(Locale.ROOT, v, args);
        } catch (Throwable e) {
            return v;
        }
    }

    // ------------------------------------------------------------ assets

    private static String assetFile(String code) {
        return "i18n/ui/lang_" + code + ".json";
    }

    private static JSONObject readJson(String name) {
        String text = readAsset(name);
        if (text == null) return null;
        try {
            return new JSONObject(text);
        } catch (Throwable e) {
            return null;
        }
    }

    static String readAsset(String name) {
        try {
            InputStream in = appContext.getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable e) {
            return null;
        }
    }
}
