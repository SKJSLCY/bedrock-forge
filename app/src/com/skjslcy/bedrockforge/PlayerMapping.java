package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * 名字 ↔ MSA 账号 ID 映射。
 * 原版 BDS 存档不含名字，只能在玩家进服时比对世界数据库里新增的记录来学习。
 */
final class PlayerMapping {

    private PlayerMapping() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("mcbds", Context.MODE_PRIVATE);
    }

    static synchronized String msaForName(Context c, String name) {
        try {
            JSONObject o = new JSONObject(prefs(c).getString("msa_map", "{}"));
            JSONObject e = o.optJSONObject(name.toLowerCase());
            return e == null ? "" : e.optString("msaId", "");
        } catch (Throwable t) {
            return "";
        }
    }

    static synchronized String nameForMsa(Context c, String msa) {
        try {
            JSONObject o = new JSONObject(prefs(c).getString("msa_map", "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject e = o.optJSONObject(k);
                if (e != null && msa.equalsIgnoreCase(e.optString("msaId"))) {
                    return e.optString("name", k);
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    static synchronized void put(Context c, String name, String msaId, String source) {
        try {
            JSONObject o = new JSONObject(prefs(c).getString("msa_map", "{}"));
            JSONObject e = new JSONObject();
            e.put("name", name);
            e.put("msaId", msaId);
            e.put("source", source);
            e.put("at", System.currentTimeMillis());
            o.put(name.toLowerCase(), e);
            prefs(c).edit().putString("msa_map", o.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 记录当前数据库里的玩家账号集合，作为“进服后新增”的基线。 */
    static void refreshBaseline(Context c) {
        try {
            Set<String> keys = currentKeys(c);
            if (keys == null) return;
            JSONArray arr = new JSONArray();
            for (String k : keys) arr.put(k);
            prefs(c).edit().putString("msa_baseline", arr.toString())
                    .putString("msa_baseline_world", currentWorld()).apply();
        } catch (Throwable ignored) {
        }
    }

    private static String currentWorld() {
        try {
            String props = ShizukuShell.read(DeployManager.SERVER_DIR + "/server.properties");
            for (String line : props.split("\n")) {
                String s = line.trim();
                if (s.startsWith("level-name=")) return s.substring("level-name=".length()).trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 玩家进服后调用：与基线比对，恰好新增一条记录时绑定名字。
     * 返回绑定结果说明，失败返回 null。
     */
    static String learn(Context c, String name) {
        try {
            Set<String> now = null;
            for (int attempt = 0; attempt < 4; attempt++) {
                Thread.sleep(2000);
                now = currentKeys(c);
                if (now != null && !now.isEmpty()) break;
            }
            if (now == null) return null;
            Set<String> base = new HashSet<>();
            String baseWorld = prefs(c).getString("msa_baseline_world", "");
            String world = currentWorld();
            if (world.isEmpty() || world.equals(baseWorld)) {
                String raw = prefs(c).getString("msa_baseline", "[]");
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) base.add(arr.optString(i));
            }
            Set<String> added = new HashSet<>(now);
            added.removeAll(base);
            // 更新基线，避免下次重复
            JSONArray na = new JSONArray();
            for (String k : now) na.put(k);
            prefs(c).edit().putString("msa_baseline", na.toString()).apply();
            String msa = MappingCore.pickSingle(base, now);
            if (msa == null && now.size() == 1) {
                msa = now.iterator().next();
            }
            if (msa != null) {
                put(c, name, msa, "auto");
                return name + " → " + msa;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Set<String> currentKeys(Context c) {
        try {
            OfflinePlayerData.Snapshot s = OfflinePlayerData.load(c);
            if (s.error != null) return null;
            Set<String> set = new HashSet<>();
            for (OfflinePlayerData.Record r : s.records) {
                if (r.msaId != null && !r.msaId.isEmpty()) set.add(r.msaId);
            }
            return set;
        } catch (Throwable t) {
            return null;
        }
    }
}
