package com.skjslcy.bedrockforge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 BDS 控制台日志记录玩家进服/离服时间。 */
public final class PlayerTracker {

    public static class Player {
        public String name;
        public String xuid;
        public long firstSeen;
        public long lastSeen;
        public long totalMs;
        public long sessionStart;
        public boolean online;
    }

    private static final Map<String, Player> players = new LinkedHashMap<>();
    private static final java.util.ArrayDeque<String> pendingBind =
            new java.util.ArrayDeque<>();
    private static final java.util.Set<String> listNames = new java.util.HashSet<>();
    private static int listRemain = -1;
    private static boolean loaded;
    private static String loadedPath = "";

    private PlayerTracker() {
    }

    private static String path() {
        return DeployManager.SERVER_DIR + "/players.json";
    }

    public static synchronized void ensureLoaded() {
        String p = path();
        if (loaded && p.equals(loadedPath)) return;
        loaded = true;
        loadedPath = p;
        players.clear();
        try {
            String raw = ShizukuShell.read(p);
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Player pl = new Player();
                pl.name = o.optString("name");
                pl.xuid = o.optString("xuid");
                pl.firstSeen = o.optLong("firstSeen");
                pl.lastSeen = o.optLong("lastSeen");
                pl.totalMs = o.optLong("totalMs");
                pl.online = false;
                pl.sessionStart = 0;
                if (!pl.name.isEmpty()) players.put(pl.name.toLowerCase(), pl);
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void handleLine(String line) {
        if (line == null) return;
        ensureLoaded();
        String t = line;
        int idx = t.indexOf("Player connected:");
        if (idx >= 0) {
            String rest = t.substring(idx + "Player connected:".length()).trim();
            String name = rest;
            String xuid = "";
            int comma = rest.indexOf(',');
            if (comma > 0) name = rest.substring(0, comma).trim();
            int xi = rest.indexOf("xuid:");
            if (xi >= 0) xuid = rest.substring(xi + 5).trim();
            applyJoin(name, xuid, System.currentTimeMillis());
            save();
            if (name != null && !name.trim().isEmpty()) pendingBind.add(name.trim());
            return;
        }
        idx = t.indexOf("Player disconnected:");
        if (idx >= 0) {
            String rest = t.substring(idx + "Player disconnected:".length()).trim();
            String name = rest;
            int comma = rest.indexOf(',');
            if (comma > 0) name = rest.substring(0, comma).trim();
            applyLeave(name, System.currentTimeMillis());
            save();
            return;
        }
        idx = t.indexOf("players online:");
        if (idx >= 0) {
            int online = ListOutput.parseOnlineCount(t);
            listNames.clear();
            if (online == 0) {
                listRemain = -1;
                reconcileOnline(listNames, System.currentTimeMillis());
                save();
            } else {
                listRemain = online;
            }
            return;
        }
        if (listRemain > 0) {
            int[] remain = {listRemain};
            if (ListOutput.isNameLine(t, remain)) {
                listNames.add(t.trim().toLowerCase());
                listRemain = remain[0];
                if (listRemain == 0) {
                    listRemain = -1;
                    reconcileOnline(listNames, System.currentTimeMillis());
                    save();
                    listNames.clear();
                }
                return;
            }
            // 出现别的日志行，按已收集到的名单收尾
            listRemain = -1;
            reconcileOnline(listNames, System.currentTimeMillis());
            save();
            listNames.clear();
        }
    }



    /** 用 list 的输出校正在线状态，修复崩溃/断线导致的脏状态。 */
    private static void reconcileOnline(java.util.Set<String> onlineNames, long now) {
        for (Player p : players.values()) {
            String key = p.name.toLowerCase();
            boolean shouldBeOnline = onlineNames.contains(key);
            if (shouldBeOnline && !p.online) {
                p.online = true;
                p.sessionStart = now;
                if (now > p.lastSeen) p.lastSeen = now;
            } else if (!shouldBeOnline && p.online) {
                if (p.sessionStart > 0) p.totalMs += Math.max(0, now - p.sessionStart);
                p.online = false;
                p.sessionStart = 0;
                if (now > p.lastSeen) p.lastSeen = now;
            }
        }
    }

    /** 重放日志时使用：按给定时间戳记录进服，不立即写盘。 */
    public static synchronized void applyJoin(String name, String xuid, long atMs) {
        if (name == null || name.trim().isEmpty()) return;
        String key = name.trim().toLowerCase();
        Player p = players.get(key);
        if (p == null) {
            p = new Player();
            p.name = name.trim();
            p.firstSeen = atMs;
            players.put(key, p);
        } else if (p.firstSeen <= 0 || atMs < p.firstSeen) {
            p.firstSeen = atMs;
        }
        if (!p.online) {
            p.online = true;
            p.sessionStart = atMs;
        }
        if (atMs > p.lastSeen) p.lastSeen = atMs;
        if (xuid != null && !xuid.isEmpty()) p.xuid = xuid;
    }

    /** 重放日志时使用：按给定时间戳结束会话。 */
    public static synchronized void applyLeave(String name, long atMs) {
        if (name == null || name.trim().isEmpty()) return;
        Player p = players.get(name.trim().toLowerCase());
        if (p == null) return;
        if (p.online && p.sessionStart > 0) {
            p.totalMs += Math.max(0, atMs - p.sessionStart);
        }
        p.online = false;
        p.sessionStart = 0;
        if (atMs > p.lastSeen) p.lastSeen = atMs;
    }

    public static synchronized void closeOpenSessionsAt(long atMs) {
        boolean changed = false;
        for (Player p : players.values()) {
            if (p.online) {
                if (p.sessionStart > 0 && atMs > p.sessionStart) {
                    p.totalMs += atMs - p.sessionStart;
                }
                p.online = false;
                p.sessionStart = 0;
                if (atMs > p.lastSeen) p.lastSeen = atMs;
                changed = true;
            }
        }
        if (changed) save();
    }

    public static synchronized String pollPendingBind() {
        return pendingBind.poll();
    }

    public static synchronized void reset() {
        players.clear();
        loaded = true;
        loadedPath = path();
    }

    public static synchronized void saveNow() {
        save();
    }

    public static synchronized void markServerStopped() {
        ensureLoaded();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Player p : players.values()) {
            if (p.online) {
                if (p.sessionStart > 0) p.totalMs += Math.max(0, now - p.sessionStart);
                p.online = false;
                p.sessionStart = 0;
                p.lastSeen = now;
                changed = true;
            }
        }
        if (changed) save();
    }

    public static synchronized void backfill(String[] lines) {
        if (lines == null) return;
        for (String line : lines) handleLine(line);
    }

    public static synchronized List<Player> list() {
        ensureLoaded();
        List<Player> out = new ArrayList<>(players.values());
        Collections.sort(out, new Comparator<Player>() {
            @Override public int compare(Player a, Player b) {
                if (a.online != b.online) return a.online ? -1 : 1;
                return Long.compare(b.lastSeen, a.lastSeen);
            }
        });
        return out;
    }

    public static long sessionMs(Player p) {
        if (p == null || !p.online || p.sessionStart <= 0) return 0;
        return Math.max(0, System.currentTimeMillis() - p.sessionStart);
    }

    /** 把时间戳变成「多久以前」。 */
    public static String ago(long atMs) {
        if (atMs <= 0) return "未知";
        long d = System.currentTimeMillis() - atMs;
        if (d < 0) d = 0;
        long min = d / 60000;
        if (min < 1) return "刚刚";
        if (min < 60) return min + " 分钟前";
        long hour = min / 60;
        if (hour < 24) return hour + " 小时前";
        long day = hour / 24;
        if (day < 30) return day + " 天前";
        return (day / 30) + " 个月前";
    }

    private static void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Player p : players.values()) {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                o.put("xuid", p.xuid == null ? "" : p.xuid);
                o.put("firstSeen", p.firstSeen);
                o.put("lastSeen", p.lastSeen);
                o.put("totalMs", p.totalMs);
                arr.put(o);
            }
            ShizukuShell.write(path(), arr.toString(2));
        } catch (Throwable ignored) {
        }
    }
}
