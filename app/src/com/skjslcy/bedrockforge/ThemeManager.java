package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.content.Context;

/** 主题管理：当前仅经典风格，预留后续主题 ID。 */
final class ThemeManager {

    static final String CLASSIC = "classic";

    static final String[] IDS = {CLASSIC};
    static final String[] NAMES = {"经典风格"};

    private ThemeManager() {
    }

    static String current(Context c) {
        String v = c.getSharedPreferences("ui", Context.MODE_PRIVATE)
                .getString("theme", CLASSIC);
        for (String id : IDS) {
            if (id.equals(v)) return v;
        }
        return CLASSIC;
    }

    static void set(Context c, String id) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
                .putString("theme", id).apply();
    }

    static String label(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return NAMES[i];
        }
        return NAMES[0];
    }

    static void apply(Activity a) {
        // 目前只有经典风格；后续主题在这里切换调色板与背景。
        a.getWindow().setStatusBarColor(0xFF1A120B);
        a.getWindow().setNavigationBarColor(0xFF1A120B);
    }
}
