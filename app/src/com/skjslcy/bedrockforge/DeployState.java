package com.skjslcy.bedrockforge;

/** 部署进度共享状态：页面重建后仍能恢复进度显示。 */
public final class DeployState {

    public static final class Snapshot {
        public final boolean busy;
        public final int type;
        public final int percent;
        public final String label;
        public final String message;
        public final int completedVersion;
        public final boolean success;

        Snapshot(boolean busy, int type, int percent, String label, String message,
                 int completedVersion, boolean success) {
            this.busy = busy;
            this.type = type;
            this.percent = percent;
            this.label = label;
            this.message = message;
            this.completedVersion = completedVersion;
            this.success = success;
        }
    }

    private static boolean busy = false;
    private static int type = -1;
    private static int percent = 0;
    private static String label = "";
    private static String message = "";
    private static int completedVersion = 0;
    private static boolean success = true;

    private DeployState() {
    }

    public static synchronized void start(int t, String l) {
        busy = true;
        type = t;
        percent = 0;
        label = l == null ? "" : l;
        message = "准备中…";
        success = true;
    }

    public static synchronized void update(int t, int p, String m) {
        if (type != t) return;
        busy = true;
        percent = Math.max(0, Math.min(100, p));
        if (m != null) message = m;
    }

    public static synchronized void finish(int t, String m, boolean ok) {
        if (type != t) return;
        busy = false;
        percent = 100;
        if (m != null) message = m;
        success = ok;
        completedVersion++;
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(busy, type, percent, label, message, completedVersion, success);
    }

    public static synchronized int completionVersion() {
        return completedVersion;
    }
}
