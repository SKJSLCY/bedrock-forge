package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增量读取 BDS 持久日志 Dedicated_Server.txt，补齐玩家进出记录。
 * 日志时间是 UTC，展示时按本机时区换算。
 */
final class ServerLogTail {

    static final class Result {
        long newOffset;
        long fileSize;
        int lines;
        int joins;
        int leaves;
        String file = "";
        String error;
    }

    private static final Pattern LINE = Pattern.compile(
            "^\\[(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2}):(\\d{3})\\s+[A-Z]+\\]\\s+"
                    + "Player (connected|disconnected):\\s*(.*)$");

    private static final int CHUNK = 4 * 1024 * 1024;

    private ServerLogTail() {
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("mcbds", Context.MODE_PRIVATE);
    }

    private static String logPath() throws Exception {
        String dir = DeployManager.SERVER_DIR;
        String out = ShizukuShell.sh("ls -1t " + q(dir) + "/Dedicated_Server*.txt* 2>/dev/null"
                + " | head -n 1").trim();
        return out;
    }

    private static long sizeOf(String path) {
        try {
            String s = ShizukuShell.sh("stat -c %s " + q(path) + " 2>/dev/null || echo 0").trim();
            return Long.parseLong(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    static Result tail(Context ctx) {
        Result r = new Result();
        try {
            if (ServerManager.get().isRunning()) {
                throw new Exception("服务端运行中，请停止后再补录（运行中日志被缓冲）");
            }
            String path = logPath();
            if (path.isEmpty()) throw new Exception("实例目录里没有 Dedicated_Server.txt");
            r.file = path.substring(path.lastIndexOf('/') + 1);
            long size = sizeOf(path);
            r.fileSize = size;
            String key = "serverlog_" + DeployManager.activeVersion();
            long offset = prefs(ctx).getLong(key + "_offset", 0);
            String savedFile = prefs(ctx).getString(key + "_file", "");
            if (!r.file.equals(savedFile) || offset > size) {
                offset = 0;
            }
            if (size == 0) {
                r.newOffset = 0;
                return r;
            }
            boolean resetDone = offset != 0;

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

            long pos = offset;
            long lastEventAt = 0;
            int guard = 0;
            while (pos < size && guard++ < 200) {
                int want = (int) Math.min(CHUNK, size - pos);
                String chunk = ShizukuShell.sh("tail -c +" + (pos + 1) + " " + q(path)
                        + " | head -c " + want);
                if (chunk.isEmpty()) break;
                int end = chunk.lastIndexOf('\n');
                if (end < 0) break;
                String complete = chunk.substring(0, end + 1);
                int consumed = complete.getBytes("UTF-8").length;
                for (String line : complete.split("\n")) {
                    if (line.isEmpty()) continue;
                    r.lines++;
                    Matcher m = LINE.matcher(line.trim());
                    if (!m.matches()) continue;
                    long at;
                    try {
                        at = fmt.parse(m.group(1) + " " + m.group(2) + ":" + m.group(3)).getTime();
                    } catch (Throwable t) {
                        continue;
                    }
                    String rest = m.group(5).trim();
                    String name = rest;
                    String xuid = "";
                    int comma = rest.indexOf(',');
                    if (comma > 0) name = rest.substring(0, comma).trim();
                    int xi = rest.indexOf("xuid:");
                    if (xi >= 0) {
                        String tail = rest.substring(xi + 5).trim();
                        int sp = tail.indexOf(' ');
                        xuid = (sp > 0 ? tail.substring(0, sp) : tail).trim();
                        int end2 = xuid.indexOf(',');
                        if (end2 > 0) xuid = xuid.substring(0, end2).trim();
                    }
                    name = name.trim();
                    if (offset == 0 && !resetDone && "connected".equals(m.group(4))) {
                        PlayerTracker.reset();
                        resetDone = true;
                    }
                    if ("connected".equals(m.group(4))) {
                        PlayerTracker.applyJoin(name, xuid, at);
                        r.joins++;
                    } else {
                        PlayerTracker.applyLeave(name, at);
                        r.leaves++;
                    }
                    if (at > lastEventAt) lastEventAt = at;
                }
                pos += consumed;
            }
            if (!ServerManager.get().isRunning() && lastEventAt > 0) {
                PlayerTracker.closeOpenSessionsAt(lastEventAt);
            }
            PlayerTracker.saveNow();
            prefs(ctx).edit().putLong(key + "_offset", pos).putString(key + "_file", r.file)
                    .apply();
            r.newOffset = pos;
        } catch (Throwable t) {
            r.error = t.getMessage() == null ? t.toString() : t.getMessage();
        }
        return r;
    }

    static String describe(Context ctx) {
        try {
            String path = logPath();
            if (path.isEmpty()) return "未找到日志文件";
            String name = path.substring(path.lastIndexOf('/') + 1);
            long size = sizeOf(path);
            long offset = prefs(ctx).getLong(
                    "serverlog_" + DeployManager.activeVersion() + "_offset", 0);
            if (offset > size) offset = 0;
            return name + " · 已读 " + kb(offset) + " / " + kb(size);
        } catch (Throwable t) {
            return "日志状态未知";
        }
    }

    private static String kb(long v) {
        if (v < 1024) return v + " B";
        if (v < 1024 * 1024) return String.format(Locale.US, "%.1f KB", v / 1024.0);
        return String.format(Locale.US, "%.1f MB", v / (1024.0 * 1024.0));
    }
}
