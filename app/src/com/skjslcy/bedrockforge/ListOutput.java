package com.skjslcy.bedrockforge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析 BDS `list` 输出：表头在上一行，玩家名逐行列出。 */
final class ListOutput {

    private static final Pattern HEADER =
            Pattern.compile("(\\d+)\\s*/\\s*(\\d+)\\s+players online");

    private ListOutput() {
    }

    /** "There are 1/10 players online:" → 1；不是表头返回 -1。 */
    static int parseOnlineCount(String line) {
        Matcher m = HEADER.matcher(line == null ? "" : line);
        if (!m.find()) return -1;
        try {
            return Integer.parseInt(m.group(1));
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 表头之后的玩家名行：不含时间戳、是纯名字、且还没收够数量。
     * remain[0] 为剩余待读数量，命中时递减。
     */
    static boolean isNameLine(String line, int[] remain) {
        if (remain == null || remain[0] <= 0) return false;
        String s = line == null ? "" : line.trim();
        if (s.isEmpty() || s.startsWith("[")) return false;
        if (!s.matches("[A-Za-z0-9_]{1,16}")) return false;
        remain[0]--;
        return true;
    }
}
