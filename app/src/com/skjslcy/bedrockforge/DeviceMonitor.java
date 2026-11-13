package com.skjslcy.bedrockforge;

import android.app.ActivityManager;
import android.content.Context;
import android.net.TrafficStats;

import java.io.BufferedReader;
import java.io.FileReader;

/** 设备级 CPU / 内存 / 网络采样，不依赖 root、ADB 或 Shizuku。 */
public final class DeviceMonitor {

    private static final DeviceMonitor INSTANCE = new DeviceMonitor();

    public static DeviceMonitor get() {
        return INSTANCE;
    }

    private long lastTotal = -1;
    private long lastIdle = -1;
    private long lastIdleSum = -1;
    private long lastIdleAt = 0;
    private boolean procStatAllowed = true;
    private final int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
    private long lastRx = -1;
    private long lastTx = -1;
    private long lastNetAt = 0;

    public volatile int cpuPercent = 0;
    public volatile long memoryUsed = 0;
    public volatile long memoryTotal = 0;
    public volatile double rxMbps = 0;
    public volatile double txMbps = 0;

    private DeviceMonitor() {
    }

    public void sample(Context context) {
        sampleCpu();
        sampleMemory(context);
        sampleNetwork();
    }

    private void sampleCpu() {
        if (procStatAllowed) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader("/proc/stat"));
                String line = r.readLine();
                if (line == null) return;
                String[] p = line.trim().split("\\s+");
                if (p.length < 5) return;
                long total = 0;
                for (int i = 1; i < p.length && i <= 8; i++) {
                    try { total += Long.parseLong(p[i]); } catch (Throwable ignored) { }
                }
                long idle = 0;
                try { idle = Long.parseLong(p[4]); } catch (Throwable ignored) { }
                try { idle += Long.parseLong(p[5]); } catch (Throwable ignored) { }
                if (lastTotal > 0 && total > lastTotal) {
                    long dt = total - lastTotal;
                    long di = Math.max(0, idle - lastIdle);
                    cpuPercent = (int) Math.max(0, Math.min(100, (dt - di) * 100 / dt));
                }
                lastTotal = total;
                lastIdle = idle;
                return;
            } catch (Throwable t) {
                // Android 新版本对普通 App 隐藏 /proc/stat，改用 cpuidle 计数器。
                procStatAllowed = false;
            } finally {
                try { if (r != null) r.close(); } catch (Throwable ignored) { }
            }
        }
        sampleCpuIdle();
    }

    /** 通过 cpuidle 的累计空闲时间估算整机 CPU 占用（普通 App 可读）。 */
    private void sampleCpuIdle() {
        long idleSum = 0;
        boolean any = false;
        for (int cpu = 0; cpu < cores; cpu++) {
            java.io.File dir = new java.io.File("/sys/devices/system/cpu/cpu" + cpu + "/cpuidle");
            java.io.File[] states = dir.listFiles();
            if (states == null) continue;
            for (java.io.File state : states) {
                long v = readLong(new java.io.File(state, "time"));
                if (v >= 0) {
                    idleSum += v;
                    any = true;
                }
            }
        }
        long now = System.currentTimeMillis();
        if (!any) return;
        if (lastIdleSum >= 0 && now > lastIdleAt) {
            double elapsedUs = (now - lastIdleAt) * 1000.0;
            double capacity = elapsedUs * cores;
            long delta = Math.max(0, idleSum - lastIdleSum);
            if (capacity > 0) {
                cpuPercent = (int) Math.max(0, Math.min(100,
                        Math.round(100.0 - delta * 100.0 / capacity)));
            }
        }
        lastIdleSum = idleSum;
        lastIdleAt = now;
    }

    private long readLong(java.io.File f) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String s = r.readLine();
            if (s == null) return -1;
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return -1;
        } finally {
            try { if (r != null) r.close(); } catch (Throwable ignored) { }
        }
    }

    private void sampleMemory(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            memoryTotal = mi.totalMem;
            memoryUsed = mi.totalMem - mi.availMem;
        } catch (Throwable ignored) {
        }
    }

    private void sampleNetwork() {
        long now = System.currentTimeMillis();
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx < 0) rx = 0;
        if (tx < 0) tx = 0;
        if (lastRx >= 0 && now > lastNetAt) {
            double sec = (now - lastNetAt) / 1000.0;
            if (sec > 0) {
                rxMbps = Math.max(0, (rx - lastRx) * 8.0 / 1000000.0 / sec);
                txMbps = Math.max(0, (tx - lastTx) * 8.0 / 1000000.0 / sec);
            }
        }
        lastRx = rx;
        lastTx = tx;
        lastNetAt = now;
    }
}
