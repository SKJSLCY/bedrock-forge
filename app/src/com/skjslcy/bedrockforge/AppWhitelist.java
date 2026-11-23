package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 守护系统：名字白名单 / 黑名单（应用侧自查，完全不动服务端 allowlist.json）。
 *
 * - 白名单与黑名单各自独立存储，互不同步；
 * - 只按玩家名匹配（忽略大小写），离线模式无法验证身份；
 * - 名单与提示语缓存在内存，进服热路径只做一次 Set.contains。
 */
final class AppWhitelist {

    static final int OFF = 0;
    static final int WHITELIST = 1;
    static final int BLACKLIST = 2;

    private static final String PREFS = "app_whitelist";
    private static final String K_MODE = "mode";
    private static final String K_WHITE = "white";
    private static final String K_BLACK = "black";
    private static final String K_MSG_WHITE = "msg_white";
    private static final String K_MSG_BLACK = "msg_black";
    private static final String K_LOGS = "logs";
    /** 旧版本共用名单键，仅用于首次迁移。 */
    private static final String K_LEGACY_NAMES = "names";

    private static final int MAX_LOGS = 200;

    private static volatile boolean cacheLoaded;
    private static volatile int modeCache = OFF;
    private static volatile java.util.Set<String> whiteCache = java.util.Collections.emptySet();
    private static volatile java.util.Set<String> blackCache = java.util.Collections.emptySet();
    private static volatile String msgWhiteCache = "";
    private static volatile String msgBlackCache = "";

    private AppWhitelist() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- 缓存

    private static java.util.Set<String> toSet(String json) {
        java.util.Set<String> set = new java.util.HashSet<>();
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < arr.length(); i++) {
                String n = arr.optString(i, "").trim();
                if (!n.isEmpty()) set.add(n.toLowerCase(java.util.Locale.ROOT));
            }
        } catch (Throwable ignored) {
        }
        return set;
    }

    private static void ensureCache(Context c) {
        if (cacheLoaded) return;
        synchronized (AppWhitelist.class) {
            if (cacheLoaded) return;
            SharedPreferences p = prefs(c);
            modeCache = p.getInt(K_MODE, OFF);
            String white = p.getString(K_WHITE, null);
            String black = p.getString(K_BLACK, null);
            if (white == null && black == null) {
                // 从旧版共用名单迁移到白名单
                white = p.getString(K_LEGACY_NAMES, "[]");
            }
            whiteCache = toSet(white);
            blackCache = toSet(black);
            msgWhiteCache = p.getString(K_MSG_WHITE, "");
            msgBlackCache = p.getString(K_MSG_BLACK, "");
            cacheLoaded = true;
        }
    }

    // ---------------------------------------------------------------- 模式

    static int mode(Context c) {
        ensureCache(c);
        return modeCache;
    }

    static void setMode(Context c, int mode) {
        modeCache = mode;
        cacheLoaded = true;
        prefs(c).edit().putInt(K_MODE, mode).apply();
    }

    static String modeLabel(Context c) {
        switch (mode(c)) {
            case WHITELIST: return I18n.t("白名单（仅名单内可进）");
            case BLACKLIST: return I18n.t("黑名单（名单内禁止进入）");
            default: return I18n.t("关闭");
        }
    }

    // ---------------------------------------------------------------- 名单

    /** 当前模式对应的名单（关闭时返回白名单，便于界面展示）。 */
    static List<String> names(Context c) {
        ensureCache(c);
        String json = prefs(c).getString(modeCache == BLACKLIST ? K_BLACK : K_WHITE, "[]");
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String n = arr.optString(i, "").trim();
                if (!n.isEmpty()) out.add(n);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static List<String> namesOf(Context c, int mode) {
        ensureCache(c);
        String json = prefs(c).getString(mode == BLACKLIST ? K_BLACK : K_WHITE, "[]");
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String n = arr.optString(i, "").trim();
                if (!n.isEmpty()) out.add(n);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void saveNames(Context c, int mode, List<String> names) {
        JSONArray arr = new JSONArray();
        java.util.Set<String> set = new java.util.HashSet<>();
        for (String n : names) {
            arr.put(n);
            set.add(n.toLowerCase(java.util.Locale.ROOT));
        }
        if (mode == BLACKLIST) blackCache = set; else whiteCache = set;
        cacheLoaded = true;
        prefs(c).edit().putString(mode == BLACKLIST ? K_BLACK : K_WHITE, arr.toString()).apply();
    }

    /** 添加到当前模式的名单。 */
    static boolean add(Context c, String name) {
        ensureCache(c);
        return addTo(c, modeCache == BLACKLIST ? BLACKLIST : WHITELIST, name);
    }

    static boolean addTo(Context c, int mode, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return false;
        List<String> list = namesOf(c, mode);
        for (String e : list) {
            if (e.equalsIgnoreCase(n)) return false;
        }
        list.add(n);
        saveNames(c, mode, list);
        return true;
    }

    static boolean remove(Context c, String name) {
        ensureCache(c);
        return removeFrom(c, modeCache == BLACKLIST ? BLACKLIST : WHITELIST, name);
    }

    static boolean removeFrom(Context c, int mode, String name) {
        String n = name == null ? "" : name.trim();
        List<String> list = namesOf(c, mode);
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(n)) {
                list.remove(i);
                removed = true;
            }
        }
        if (removed) saveNames(c, mode, list);
        return removed;
    }

    static boolean contains(Context c, String name) {
        ensureCache(c);
        return name != null && whiteCache.contains(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    static boolean isAllowed(Context c, String name) {
        ensureCache(c);
        int m = modeCache;
        if (m == OFF) return true;
        String key = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
        boolean inWhite = whiteCache.contains(key);
        boolean inBlack = blackCache.contains(key);
        return m == WHITELIST ? inWhite : !inBlack;
    }

    // ---------------------------------------------------------------- 提示语

    static String message(Context c, int mode) {
        ensureCache(c);
        String custom = mode == BLACKLIST ? msgBlackCache : msgWhiteCache;
        if (custom != null && !custom.trim().isEmpty()) return custom;
        return mode == BLACKLIST ? I18n.t("已在黑名单中") : I18n.t("未在白名单中");
    }

    static void setMessage(Context c, int mode, String text) {
        String t = text == null ? "" : text;
        if (mode == BLACKLIST) msgBlackCache = t; else msgWhiteCache = t;
        cacheLoaded = true;
        prefs(c).edit().putString(mode == BLACKLIST ? K_MSG_BLACK : K_MSG_WHITE, t).apply();
    }

    static String kickReason(Context c, String name) {
        return message(c, mode(c));
    }

    // ---------------------------------------------------------------- 拦截记录

    static void logKick(Context c, String name, String reason, int mode) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(K_LOGS, "[]"));
            JSONObject o = new JSONObject();
            o.put("n", name == null ? "" : name);
            o.put("t", System.currentTimeMillis());
            o.put("r", reason == null ? "" : reason);
            o.put("m", mode);
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < arr.length() && out.length() < MAX_LOGS; i++) out.put(arr.get(i));
            prefs(c).edit().putString(K_LOGS, out.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    static void logKick(Context c, String name) {
        logKick(c, name, message(c, mode(c)), mode(c));
    }

    /** 导入一条记录（去重：同名字+同时间戳）。 */
    static void importLog(Context c, String name, long ts, String reason, int mode) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(K_LOGS, "[]"));
            JSONArray out = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("n", name == null ? "" : name);
            o.put("t", ts);
            o.put("r", reason == null ? "" : reason);
            o.put("m", mode);
            out.put(o);
            for (int i = 0; i < arr.length() && out.length() < MAX_LOGS; i++) {
                JSONObject old = arr.optJSONObject(i);
                if (old != null && old.optLong("t", 0) == ts
                        && name != null && name.equals(old.optString("n", ""))) continue;
                out.put(old);
            }
            prefs(c).edit().putString(K_LOGS, out.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 每条：{名字, 时间戳(ms), 理由, 模式}。 */
    static List<String[]> logs(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(K_LOGS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new String[]{
                        o.optString("n", ""),
                        String.valueOf(o.optLong("t", 0)),
                        o.optString("r", ""),
                        String.valueOf(o.optInt("m", 0))
                });
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static void clearLogs(Context c) {
        prefs(c).edit().remove(K_LOGS).apply();
    }

    // ---------------------------------------------------------------- 颜色符号

    /** Minecraft 颜色符号 §0-§f（§r 重置）。 */
    static final String[] COLOR_CODES = {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f", "§r"
    };
}
