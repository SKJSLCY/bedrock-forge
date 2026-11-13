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
    private volatile String lastError = "";
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
                lastError = "需要授予 Shizuku 权限";
                addLog("[Shizuku] 未授权，请允许 Shizuku 权限后重试");
                return false;
            }
            try {
                return startViaShizuku();
            } catch (Throwable t) {
                lastError = "Shizuku 启动失败：" + t.getMessage();
                addLog("[Shizuku] " + lastError);
                return false;
            }
        }

        lastError = "需要 Shizuku 才能启动服务端";
        addLog("[错误] " + lastError);
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
            addLog("[启动] Box64 + Ubuntu ARM64: " + hostServer);
            Process p = ShizukuProcess.start(cmd, env, "/data/local/tmp");
            if (p == null) throw new IllegalStateException("newProcess 返回 null");
            process = p;
            stdin = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), "UTF-8"));
            running = true;
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
            return true;
        }

        lastError = "未部署 Box64 运行环境";
        addLog("[错误] " + lastError);
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
        addLog("[进程结束] exit code " + code);
    }

    public synchronized void stop() {
        if (!running || stopping) return;
        stopping = true;
        addLog("[停止] 正在发送 stop ...");
        try {
            if (stdin != null) {
                stdin.write("stop\n");
                stdin.flush();
            }
        } catch (Throwable t) {
            addLog("[停止] 写入失败：" + t.getMessage());
        }
        final Process p = process;
        Thread killer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(12000);
                    if (running && p != null) {
                        addLog("[停止] 12 秒未退出，强制结束");
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
            addLog("[控制台] 发送失败：" + t.getMessage());
            return false;
        }
    }

    private void addLog(String line) {
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

    public boolean isRunning() {
        return running;
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
