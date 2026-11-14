package com.skjslcy.bedrockforge;

import android.content.pm.PackageManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import rikka.shizuku.Shizuku;

/** 通过 Shizuku 以 shell 身份读写实际运行目录。 */
public final class ShizukuShell {

    public static final String SERVER_DIR = "/data/local/tmp/mcbds/server";
    public static final String PROPERTIES = SERVER_DIR + "/server.properties";
    /** shell 进程的 cwd 必须是已存在目录；新设备上 server 目录可能还不存在。 */
    public static final String WORK_DIR = "/data/local/tmp";

    private ShizukuShell() {
    }

    public static String read(String path) throws Exception {
        return exec(new String[]{"sh", "-c", "cat " + quote(path)}, env(), WORK_DIR, null);
    }

    public static void write(String path, String content) throws Exception {
        exec(new String[]{"sh", "-c", "cat > " + quote(path)}, env(), WORK_DIR, content);
    }

    public static String sh(String command) throws Exception {
        return exec(new String[]{"sh", "-c", command}, env(), WORK_DIR, null);
    }

    public interface CopyProgress {
        void onProgress(long copied, long total);
    }

    /** 把 App 侧文件通过 stdin 写入 /data/local/tmp（绕过 SELinux 与 /sdcard 限制）。 */
    public static void copyIn(java.io.File local, String remote, CopyProgress cb) throws Exception {
        if (!Shizuku.pingBinder()) throw new IOException(I18n.t("Shizuku 未运行"));
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw new IOException(I18n.t("Shizuku 未授权"));
        }
        Process p = ShizukuProcess.start(new String[]{"sh", "-c", "cat > " + quote(remote)},
                env(), WORK_DIR);
        java.io.InputStream in = new java.io.FileInputStream(local);
        OutputStream out = p.getOutputStream();
        byte[] buf = new byte[65536];
        long total = local.length();
        long copied = 0;
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                copied += n;
                if (cb != null) cb.onProgress(copied, total);
            }
            out.flush();
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
            try { out.close(); } catch (Throwable ignored) { }
        }
        String err = readAll(p.getErrorStream());
        int code = p.waitFor();
        if (code != 0) throw new IOException(err.trim().isEmpty() ? (I18n.t("退出码 ") + code) : err.trim());
    }

    /** 从 shell 侧读取文件到 App 缓存。 */
    public static void copyOut(String remote, java.io.File local, CopyProgress cb) throws Exception {
        if (!Shizuku.pingBinder()) throw new IOException(I18n.t("Shizuku 未运行"));
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw new IOException(I18n.t("Shizuku 未授权"));
        }
        Process p = ShizukuProcess.start(new String[]{"sh", "-c", "cat " + quote(remote)},
                env(), WORK_DIR);
        InputStream in = p.getInputStream();
        java.io.FileOutputStream out = new java.io.FileOutputStream(local);
        byte[] buf = new byte[65536];
        long total = 0;
        long copied = 0;
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                copied += n;
                if (cb != null) cb.onProgress(copied, total);
            }
            out.flush();
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
            try { out.close(); } catch (Throwable ignored) { }
        }
        String err = readAll(p.getErrorStream());
        int code = p.waitFor();
        if (code != 0) throw new IOException(err.trim().isEmpty() ? (I18n.t("退出码 ") + code) : err.trim());
    }

    public static String exec(String[] cmd, String[] env, String dir, String stdin) throws Exception {        if (!Shizuku.pingBinder()) throw new IOException(I18n.t("Shizuku 未运行"));
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw new IOException(I18n.t("Shizuku 未授权"));
        }
        Process p = ShizukuProcess.start(cmd, env, dir);
        if (stdin != null) {
            OutputStream os = p.getOutputStream();
            os.write(stdin.getBytes("UTF-8"));
            os.flush();
            os.close();
        }
        String out = readAll(p.getInputStream());
        String err = readAll(p.getErrorStream());
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException(err.trim().isEmpty() ? (I18n.t("退出码 ") + code) : err.trim());
        }
        return out;
    }

    private static String[] env() {
        return new String[]{
                "PATH=/system/bin:/system/xbin",
                "HOME=/data/local/tmp",
                "TMPDIR=/data/local/tmp"
        };
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
