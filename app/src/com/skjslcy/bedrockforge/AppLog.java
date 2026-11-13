package com.skjslcy.bedrockforge;

import java.util.ArrayList;
import java.util.List;

/** App 侧部署/下载/修复等操作日志。 */
public final class AppLog {
    private static final List<String> LINES = new ArrayList<>();

    private AppLog() {
    }

    public static synchronized void add(String line) {
        if (line == null || line.trim().isEmpty()) return;
        LINES.add(line);
        while (LINES.size() > 3000) LINES.remove(0);
    }

    public static synchronized String text() {
        StringBuilder sb = new StringBuilder();
        for (String s : LINES) sb.append(s).append('\n');
        return sb.toString();
    }
}
