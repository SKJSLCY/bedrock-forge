package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.content.Context;

/** 主题管理：经典风格 / 简约风格（浅色、深色）。 */
final class ThemeManager {

    static final String CLASSIC = "classic";
    static final String MINIMAL = "minimal";
    static final String ORE = "ore";

    static final String MODE_LIGHT = "light";
    static final String MODE_DARK = "dark";

    private ThemeManager() {
    }

    static String style(Context c) {
        String v = c.getSharedPreferences("ui", Context.MODE_PRIVATE)
                .getString("theme", CLASSIC);
        if (MINIMAL.equals(v)) return MINIMAL;
        if (ORE.equals(v)) return ORE;
        return CLASSIC;
    }

    static String mode(Context c) {
        String v = c.getSharedPreferences("ui", Context.MODE_PRIVATE)
                .getString("theme_mode", MODE_LIGHT);
        return MODE_DARK.equals(v) ? MODE_DARK : MODE_LIGHT;
    }

    static boolean isMinimal(Context c) {
        return MINIMAL.equals(style(c));
    }

    static boolean isOre(Context c) {
        return ORE.equals(style(c));
    }

    static boolean isDark(Context c) {
        return MODE_DARK.equals(mode(c));
    }

    static void setStyle(Context c, String style) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
                .putString("theme", style).apply();
    }

    static void setMode(Context c, String mode) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
                .putString("theme_mode", mode).apply();
    }

    static void apply(Context c) {
        MinecraftUi.applyTheme(isMinimal(c), isOre(c), isMinimal(c) && isDark(c));
    }

    static void apply(Activity a) {
        boolean minimal = isMinimal(a);
        boolean ore = isOre(a);
        MinecraftUi.applyTheme(minimal, ore, minimal && isDark(a));
        boolean dark = minimal && isDark(a);
        int statusColor = ore ? 0xFFE8E8E8
                : (minimal ? (dark ? 0xFF000000 : 0xFFEEECE6) : 0xFF1A120B);
        int navColor = ore ? 0xFF383838
                : (minimal ? (dark ? 0xFF000000 : 0xFFEEECE6) : 0xFF1A120B);
        a.getWindow().setStatusBarColor(statusColor);
        a.getWindow().setNavigationBarColor(navColor);
        if (minimal || ore) {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    (minimal && dark) ? 0 : android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /** 把经典配色映射到简约主题的等价颜色。 */
    static int mapTextColor(int color) {
        if (!MinecraftUi.MINIMAL && !MinecraftUi.ORE) return color;
        switch (color) {
            case 0xFFFFFFFF:
            case 0xFF1B1B1B:
            case 0xFF111111:
            case 0xFF3A3A3A:
            case 0xFF151515:
                return MinecraftUi.PANEL_TEXT;
            case 0xFF4A4A4A:
            case 0xFF8A8A8A:
            case 0xFFD8C7AE:
            case 0xFFCBBBA4:
            case 0xFFB0B0B0:
            case 0xFF9A9A9A:
            case 0xFF6A8A6A:
            case 0xFF79C0FF:
            case 0xFFE8E8E8:
            case 0xFFD0D0D0:
            case 0xFFC0C0C0:
                return MinecraftUi.PANEL_MUTED;
            case 0xFFEAFFDF:
            case 0xFFB8FFB0:
            case 0xFFE8FFE0:
            case 0xFF7EE787:
            case 0xFF5BC236:
            case 0xFF2F7D1F:
                return MinecraftUi.GREEN;
            case 0xFFFF6B6B:
            case 0xFFFF7062:
            case 0xFFB3261E:
            case 0xFFFFE0E0:
                return MinecraftUi.RED;
            case 0xFFFFD166:
            case 0xFFFFD180:
            case 0xFFFFB74D:
            case 0xFFB26A00:
                return MinecraftUi.AMBER;
            case 0xFFFFF2F2:
                return 0xFFFFFFFF;
            default:
                return color;
        }
    }
}
