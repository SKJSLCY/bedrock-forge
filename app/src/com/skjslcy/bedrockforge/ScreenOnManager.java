package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.WindowManager;

/** 屏幕常亮策略：关闭 / 仅开服时 / 始终保持。 */
final class ScreenOnManager {

    static final int OFF = 0;
    static final int WHILE_SERVER = 1;
    static final int ALWAYS = 2;

    private ScreenOnManager() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("screen_on", Context.MODE_PRIVATE);
    }

    static int mode(Context c) {
        return prefs(c).getInt("mode", OFF);
    }

    static void setMode(Context c, int mode) {
        prefs(c).edit().putInt("mode", mode).apply();
    }

    static String label(int mode) {
        switch (mode) {
            case WHILE_SERVER: return "仅开服时保持";
            case ALWAYS: return "始终保持";
            default: return "关闭";
        }
    }

    static boolean shouldKeep(Context c) {
        int m = mode(c);
        return m == ALWAYS || (m == WHILE_SERVER && ServerManager.get().isRunning());
    }

    static void apply(Activity a) {
        if (a == null) return;
        try {
            if (shouldKeep(a)) {
                a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Throwable ignored) {
        }
    }
}
