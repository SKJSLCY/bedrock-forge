package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import rikka.shizuku.Shizuku;

/**
 * Singleton that owns the BDS child process.
 */
public final class ServerManager {

    private static final ServerManager INSTANCE = new ServerManager();
    private static final int MAX_LOGS = 2000;

    public static ServerManager get() {
        return INSTANCE;
    }

    private final List<String> logs = Collections.synchronizedList(new ArrayList<String>());
    private Process process;
    private BufferedWriter stdin;
    private volatile boolean running;
    private volatile boolean stopping;
    private volatile long startTime;
    /** 应用重启后遗留的 bedrock_server 进程（无法接管 stdin，只用于显示与停止）。 */
    private volatile int externalPid = 0;
    private volatile String lastError = "";
    /** 在线人数（来自 BDS stdout：There are X/Y / Player connected / Player disconnected）。 */
    private volatile int players = -1;
    private volatile int maxPlayers = -1;
    private volatile long playersAt = 0;

    /** 玩家加入/离开回调（供白名单等模块使用，回调在 stdout 线程上执行）。 */
    interface PlayerWatch {
        void onJoin(String name);
        /** 玩家真正进入世界（BDS 输出 "Player Spawned"）——此时 kick 才有效。 */
        void onSpawn(String name);
        void onLeave(String name);
    }

    private volatile PlayerWatch playerWatch;
    private final java.util.Set<String> onlineNames =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    public void setPlayerWatch(PlayerWatch w) {
        playerWatch = w;
    }

    public java.util.Set<String> getOnlineNames() {
        return onlineNames;
    }

    /** 通过控制台踢人；名字含空格会被加引号。 */
    public boolean kick(String name, String reason) {
        if (name == null) return false;
        String n = name.trim();
        if (n.isEmpty()) return false;
        // 防御：绝不把命令回显（"> list" 等）当作玩家名发给服务端
        if (n.startsWith(">") || n.startsWith("[") || n.contains("\"")) return false;
        String cmd = "kick \"" + n.replace("\"", "") + "\" " + (reason == null ? "" : reason);
        return send(cmd);
    }

    public int getPlayers() {
        return players;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public long getPlayersAt() {
        return playersAt;
    }
    private int port = 19134;
    private String serverName = "Phone12662";

    private ServerManager() {
    }

    public synchronized boolean start(Context context) {
        if (running) return true;
        lastError = "";
        stopping = false;

        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                lastError = I18n.t("需要授予 Shizuku 权限");
                addLog(I18n.t("[Shizuku] 未授权，请允许 Shizuku 权限后重试"));
                return false;
            }
            try {
                return startViaShizuku();
            } catch (Throwable t) {
                lastError = I18n.t("Shizuku 启动失败：") + t.getMessage();
                addLog("[Shizuku] " + lastError);
                return false;
            }
        }

        lastError = I18n.t("需要 Shizuku 才能启动服务端");
        addLog(I18n.t("[错误] ") + lastError);
        return false;
    }

    private synchronized boolean startViaShizuku() throws Exception {
        final String base = "/data/local/tmp/mcbds";
        final String server = base + "/server";

        final String boxRoot = DeployManager.BOX64_ROOTFS;
        final String boxBin = DeployManager.BOX64_BIN;
        String boxState = ShizukuShell.sh("test -r " + boxBin
                + " && test -d " + boxRoot + "/etc && echo 1 || echo 0").trim();
        if ("1".equals(boxState)) {
            String hostServer = ShizukuShell.sh("readlink -f " + server + " 2>/dev/null || true").trim();
            if (hostServer.isEmpty()) hostServer = server;
            ShizukuShell.sh("mkdir -p " + DeployManager.BOX64_BASE + "/tmp " + hostServer);
            // 先把软限制提升到硬限制，避免 BDS 启动时 setrlimit 报
            // "Failed to up file descriptor limit: Operation not permitted"。
            String hardLimit = "32768";
            try {
                String h = ShizukuShell.sh("ulimit -Hn 2>/dev/null || echo 32768").trim();
                if (h.matches("\\d+")) hardLimit = h;
            } catch (Throwable ignored) {
            }
            String launch = "ulimit -n " + hardLimit + " 2>/dev/null; exec "
                    + shq(DeployManager.BOX64_PROOT)
                    + " --link2symlink -r " + shq(boxRoot)
                    + " -0 -b /dev -b /proc -b /sys"
                    + " -b " + shq(hostServer + ":/root/bds")
                    + " -w /root/bds"
                    + " /usr/local/bin/box64 /root/bds/bedrock_server";
            String[] cmd = new String[]{"sh", "-c", launch};
            String[] env = new String[]{
                    "PROOT_TMP_DIR=" + DeployManager.BOX64_BASE + "/tmp",
                    "PROOT_LOADER=" + base + "/proot/libexec/loader",
                    "PROOT_LOADER_32=" + base + "/proot/libexec/loader32",
                    "LD_LIBRARY_PATH=" + base + "/proot/lib",
                    "HOME=/root",
                    "TMPDIR=/root",
                    "TERM=xterm",
                    "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            };
            addLog(I18n.t("[启动] Box64 + Ubuntu ARM64: ") + hostServer);
            if (externalPid > 0) {
                lastError = I18n.t("检测到已在运行的服务端进程，请先停止");
                addLog(I18n.t("[启动] ") + lastError);
                return false;
            }
            Process p = ShizukuProcess.start(cmd, env, "/data/local/tmp");
            if (p == null) throw new IllegalStateException(I18n.t("newProcess 返回 null"));
            process = p;
            stdin = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), "UTF-8"));
            running = true;
            players = -1;
            maxPlayers = -1;
            startTime = System.currentTimeMillis();
            Thread reader = new Thread(new Runnable() {
                @Override public void run() { readLoop(); }
            }, "bds-stdout");
            reader.setDaemon(true);
            reader.start();
            Thread waiter = new Thread(new Runnable() {
                @Override public void run() { waitLoop(); }
            }, "bds-wait");
            waiter.setDaemon(true);
            waiter.start();
            final int appPid = android.os.Process.myPid();
            Thread watcherThread = new Thread(new Runnable() {
                @Override public void run() { startWatcher(appPid); }
            }, "bds-watcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
            return true;
        }

        lastError = I18n.t("未部署 Box64 运行环境");
        addLog(I18n.t("[错误] ") + lastError);
        return false;
    }

    private static String shq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private void readLoop() {
        Process p = process;
        if (p == null) return;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                addLog(line);
            }
        } catch (Throwable ignored) {
        }
    }

    private void waitLoop() {
        Process p = process;
        if (p == null) return;
        int code = -1;
        try {
            code = p.waitFor();
        } catch (InterruptedException ignored) {
        }
        running = false;
        stopping = false;
        PlayerTracker.markServerStopped();
        addLog(I18n.t("[进程结束] exit code ") + code);
    }

    public synchronized void stop() {
        if (!running && externalPid > 0) {
            stopExternal();
            return;
        }
        if (!running || stopping) return;
        stopping = true;
        addLog(I18n.t("[停止] 正在发送 stop ..."));
        try {
            if (stdin != null) {
                stdin.write("stop\n");
                stdin.flush();
            }
        } catch (Throwable t) {
            addLog(I18n.t("[停止] 写入失败：") + t.getMessage());
        }
        final Process p = process;
        Thread killer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(12000);
                    if (running && p != null) {
                        addLog(I18n.t("[停止] 12 秒未退出，强制结束"));
                        p.destroy();
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }, "bds-killer");
        killer.setDaemon(true);
        killer.start();
    }

    public synchronized boolean send(String command) {
        if (!running || stdin == null) return false;
        try {
            stdin.write(command);
            stdin.write("\n");
            stdin.flush();
            addLog("> " + command);
            return true;
        } catch (Throwable t) {
            addLog(I18n.t("[控制台] 发送失败：") + t.getMessage());
            return false;
        }
    }

    private void addLog(String line) {
        parsePlayers(line);
        if (line == null) return;
        PlayerTracker.handleLine(line);
        logs.add(line);
        synchronized (logs) {
            while (logs.size() > MAX_LOGS) {
                logs.remove(0);
            }
        }
    }

    /** 供部署/运维流程写入控制台日志。 */
    public void log(String line) {
        addLog(line);
    }

    public String[] getLogs() {
        synchronized (logs) {
            return logs.toArray(new String[0]);
        }
    }

    public int getLogCount() {
        return logs.size();
    }

    /** 从 BDS 输出解析在线人数：There are X/Y / Player connected / Player disconnected。 */
    private void parsePlayers(String line) {
        if (line == null) return;
        try {
            if (line.contains("players online")) {
                java.util.regex.Matcher mt = java.util.regex.Pattern
                        .compile("There are (\\d+)/(\\d+) players online").matcher(line);
                if (mt.find()) {
                    players = Integer.parseInt(mt.group(1));
                    maxPlayers = Integer.parseInt(mt.group(2));
                    playersAt = System.currentTimeMillis();
                    return;
                }
            }
            int ci = line.indexOf("Player connected: ");
            if (ci >= 0) {
                String rest = line.substring(ci + "Player connected: ".length());
                int comma = rest.indexOf(',');
                String nm = (comma > 0 ? rest.substring(0, comma) : rest).trim();
                if (!nm.isEmpty()) {
                    onlineNames.add(nm);
                    players = Math.max(players, onlineNames.size());
                    playersAt = System.currentTimeMillis();
                    PlayerWatch w = playerWatch;
                    if (w != null) {
                        try { w.onJoin(nm); } catch (Throwable ignored) { }
                    }
                }
                return;
            }
            int si = line.indexOf("Player Spawned: ");
            if (si >= 0) {
                String rest = line.substring(si + "Player Spawned: ".length());
                int xi = rest.indexOf(" xuid:");
                String nm = (xi > 0 ? rest.substring(0, xi) : rest).trim();
                if (!nm.isEmpty()) {
                    PlayerWatch w = playerWatch;
                    if (w != null) {
                        try { w.onSpawn(nm); } catch (Throwable ignored) { }
                    }
                }
                return;
            }
            int di = line.indexOf("Player disconnected: ");
            if (di >= 0) {
                String rest = line.substring(di + "Player disconnected: ".length());
                int comma = rest.indexOf(',');
                String nm = (comma > 0 ? rest.substring(0, comma) : rest).trim();
                if (!nm.isEmpty()) {
                    onlineNames.remove(nm);
                    players = Math.max(0, onlineNames.size());
                    playersAt = System.currentTimeMillis();
                    PlayerWatch w = playerWatch;
                    if (w != null) {
                        try { w.onLeave(nm); } catch (Throwable ignored) { }
                    }
                }
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 优雅停止并等待服务端退出（BDS 收到 stop 会保存世界后退出）。
     * @param timeoutSec 最长等待秒数，超时后强杀
     */
    public void stopAndWait(int timeoutSec) {
        if (!running && externalPid > 0) {
            stopExternal();
            return;
        }
        if (!running) return;
        stopping = true;
        try {
            if (stdin != null) {
                stdin.write("stop\n");
                stdin.flush();
            }
        } catch (Throwable ignored) {
        }
        Process p = process;
        if (p == null) return;
        try {
            p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
        if (running) {
            try {
                p.destroy();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 看门狗：应用进程消失后（含被强杀 / 划掉任务），向 BDS 发送 SIGINT，
     * 让服务端保存世界后退出，避免存档损坏。守护脚本挂在 shizuku shell 上，不依赖应用存活。
     */
    public void startWatcher(int appPid) {
        if (appPid <= 0) return;
        try {
            String cmd = "( while ps -A -o PID | grep -qw " + appPid + "; do sleep 2; done; "
                    + "P=$(pidof bedrock_server); [ -n \"$P\" ] && kill -INT $P ) >/dev/null 2>&1 &";
            ShizukuShell.sh(cmd);
            addLog(I18n.t("[守护] 已启用：应用退出时自动停服"));
        } catch (Throwable t) {
            addLog(I18n.t("[守护] 启用失败：") + t.getMessage());
        }
    }

    public boolean isRunning() {
        return running || externalPid > 0;
    }

    /** 是否为外部遗留进程（应用重启后检测到，但没有 stdin 管道）。 */
    public boolean isExternal() {
        return !running && externalPid > 0;
    }

    public int getExternalPid() {
        return externalPid;
    }

    /** 检测残留的 bedrock_server 进程（proot 包装下 pgrep 可命中子进程）。 */
    public synchronized void detectExternal() {
        if (running) {
            externalPid = 0;
            return;
        }
        int pid = 0;
        try {
            // 只认精确进程名 bedrock_server（proot 包装的 cmdline 也含该词，不能匹配）
            String out = ShizukuShell.sh("pidof bedrock_server 2>/dev/null").trim();
            if (out.isEmpty()) {
                out = ShizukuShell.sh("ps -A -o PID,NAME | awk '$2==\"bedrock_server\"{print $1; exit}'"
                ).trim();
            }
            if (!out.isEmpty()) {
                String first = out.split("\\s+")[0];
                pid = Integer.parseInt(first);
                // 二次确认：进程真的存在
                String alive = ShizukuShell.sh("kill -0 " + pid + " 2>/dev/null && echo yes").trim();
                if (!"yes".equals(alive)) pid = 0;
            }
        } catch (Throwable ignored) {
        }
        externalPid = pid;
    }

    /** 停止外部遗留进程（TERM → 2 秒后 KILL）。 */
    public synchronized boolean stopExternal() {
        if (externalPid <= 0) return false;
        try {
            String pid = String.valueOf(externalPid);
            ShizukuShell.sh("kill -TERM " + pid + " 2>/dev/null; sleep 2; kill -9 " + pid
                    + " 2>/dev/null");
            String alive = ShizukuShell.sh("kill -0 " + pid + " 2>/dev/null && echo yes").trim();
            addLog(I18n.t("[停止] 已结束外部进程 ") + pid + ("yes".equals(alive) ? " (仍在运行)" : ""));
        } catch (Throwable ignored) {
        }
        externalPid = 0;
        return true;
    }

    public boolean isStopping() {
        return stopping;
    }

    public long getStartTime() {
        return startTime;
    }

    public String getLastError() {
        return lastError;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
