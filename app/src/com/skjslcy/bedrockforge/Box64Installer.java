package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * App 内置工具链 + Box64 源码，在手机上完成 Box64 编译。
 * 不依赖 Termux 或在线 apt。
 */
public final class Box64Installer {

    private Box64Installer() {
    }

    public static void install(Context ctx, DeployManager.Progress p, int threads) throws Exception {
        if (!ShizukuGate.isRunning() || !ShizukuGate.isAuthorized()) {
            throw new IOException(I18n.t("需要 Shizuku 权限"));
        }
        int jobs = Math.max(1, Math.min(4, threads));
        String base = DeployManager.BOX64_BASE;
        String rootfs = DeployManager.BOX64_ROOTFS;

        ShizukuShell.sh("mkdir -p " + q(base) + " " + q(base + "/tmp")
                + "; rm -f " + q(DeployManager.BOX64_READY));
        installProot(ctx, p);

        p.update(3, I18n.t("解压内置 Ubuntu 工具链…"));
        ShizukuShell.sh("rm -rf " + q(rootfs) + "; mkdir -p " + q(rootfs));
        copyAsset(ctx, "toolchain-rootfs.tar.gz", base + "/toolchain.tar.gz", false);
        ShizukuShell.sh("tar -xzf " + q(base + "/toolchain.tar.gz") + " -C " + q(rootfs)
                + " 2>/dev/null || true");
        if (!"1".equals(ShizukuShell.sh("test -e " + q(rootfs + "/usr/bin/gcc")
                + " && test -e " + q(rootfs + "/usr/bin/cmake")
                + " && echo 1 || echo 0").trim())) {
            throw new IOException(I18n.t("内置工具链解压失败"));
        }
        ShizukuShell.sh("rm -f " + q(base + "/toolchain.tar.gz")
                + "; mkdir -p " + q(rootfs + "/dev") + " " + q(rootfs + "/proc")
                + " " + q(rootfs + "/sys") + " " + q(rootfs + "/tmp"));

        p.update(15, I18n.t("准备 Box64 源码…"));
        copyAsset(ctx, "box64-main.tar.gz", rootfs + "/root/box64-main.tar.gz", false);

        p.update(22, I18n.t("编译 Box64（-j") + jobs + "）…");
        String compile = "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; "
                + "cd /root && rm -rf box64-main && tar -xzf box64-main.tar.gz "
                + "&& cd box64-main && mkdir -p build && cd build "
                + "&& cmake .. -DARM64=ON -DBAD_SIGNAL=ON -DNOGIT=ON "
                + "-DCMAKE_BUILD_TYPE=RelWithDebInfo "
                + "&& make -j" + jobs + " && make install";
        try {
            runProotStreaming(base, rootfs, compile, p);
        } catch (Exception streamError) {
            p.update(22, I18n.t("流式编译失败，改用兼容模式重试…"));
            runProot(base, rootfs, compile);
        }

        p.update(96, I18n.t("校验 Box64…"));
        String v = runProot(base, rootfs,
                "/usr/local/bin/box64 --version 2>&1 | grep '^Box64' | head -n 1");
        if (v == null || v.trim().isEmpty()) throw new IOException(I18n.t("Box64 安装后无法运行"));
        ShizukuShell.sh("rm -f " + q(rootfs + "/root/box64-main.tar.gz")
                + "; echo ok > " + q(DeployManager.BOX64_READY)
                + "; chmod 644 " + q(DeployManager.BOX64_READY));
        p.update(100, v.trim());
    }

    private static void installProot(Context ctx, DeployManager.Progress p) throws Exception {
        p.update(1, I18n.t("准备 proot…"));
        String base = DeployManager.BASE + "/proot";
        ShizukuShell.sh("mkdir -p " + q(base) + " " + q(base + "/lib") + " " + q(base + "/libexec"));
        copyAsset(ctx, "proot/proot", base + "/proot", true);
        copyAsset(ctx, "proot/libexec/loader", base + "/libexec/loader", true);
        copyAsset(ctx, "proot/libexec/loader32", base + "/libexec/loader32", true);
        copyAsset(ctx, "proot/lib/libtalloc.so.2", base + "/lib/libtalloc.so.2", false);
        copyAsset(ctx, "proot/lib/libandroid-shmem.so", base + "/lib/libandroid-shmem.so", false);
    }

    private static void copyAsset(Context ctx, String asset, String remote, boolean exec) throws Exception {
        File tmp = new File(ctx.getCacheDir(), "asset-" + new File(asset).getName());
        AssetManager am = ctx.getAssets();
        InputStream in = am.open(asset);
        OutputStream out = new FileOutputStream(tmp);
        byte[] buf = new byte[65536];
        int n;
        try {
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
            try { out.close(); } catch (Throwable ignored) { }
        }
        ShizukuShell.copyIn(tmp, remote, null);
        tmp.delete();
        if (exec) ShizukuShell.sh("chmod 755 " + q(remote));
        else ShizukuShell.sh("chmod 644 " + q(remote));
    }

    private static String prootPrefix(String base, String rootfs) {
        return "cd " + q(base) + " && "
                + "PROOT_TMP_DIR=" + q(base + "/tmp")
                + " PROOT_LOADER=" + q(DeployManager.BASE + "/proot/libexec/loader")
                + " PROOT_LOADER_32=" + q(DeployManager.BASE + "/proot/libexec/loader32")
                + " LD_LIBRARY_PATH=" + q(DeployManager.BASE + "/proot/lib")
                + " " + q(DeployManager.BOX64_PROOT)
                + " --link2symlink -r " + q(rootfs) + " -0 -b /dev -b /proc -b /sys -w /root ";
    }

    private static void runProotStreaming(String base, String rootfs, String command,
                                          final DeployManager.Progress p) throws Exception {
        String full = prootPrefix(base, rootfs) + "/bin/bash -lc " + q(command);
        String[] env = {
                "PATH=/system/bin:/system/xbin",
                "HOME=/data/local/tmp",
                "TMPDIR=/data/local/tmp"
        };
        ShizukuProcess proc = ShizukuProcess.start(new String[]{"sh", "-c", full},
                env, "/data/local/tmp");
        try { proc.getOutputStream().close(); } catch (Throwable ignored) { }
        final StringBuilder err = new StringBuilder();
        Thread errThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(proc.getErrorStream(), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        synchronized (err) {
                            err.append(line).append('\n');
                            if (err.length() > 4000) err.delete(0, err.length() - 4000);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "box64-stderr");
        errThread.start();

        int lines = 0;
        int lastPercent = -1;
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                lines++;
                int percent = 22 + Math.min(65, lines / 12);
                if (percent != lastPercent) {
                    lastPercent = percent;
                    String msg = line.trim();
                    if (msg.length() > 80) msg = msg.substring(0, 80);
                    p.update(percent, I18n.t("编译：") + (msg.isEmpty() ? "…" : msg));
                }
            }
        } finally {
            int code = proc.waitFor();
            errThread.join(2000);
            if (code != 0) {
                String e;
                synchronized (err) { e = err.toString().trim(); }
                if (e.length() > 1200) e = e.substring(e.length() - 1200);
                throw new IOException(I18n.t("Box64 编译失败（退出码 ") + code + I18n.t("）")
                        + (e.isEmpty() ? "" : "：\n" + e));
            }
        }
        p.update(90, I18n.t("编译完成，安装 Box64…"));
    }

    private static String runProot(String base, String rootfs, String command) throws Exception {
        String c = "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; " + command;
        return ShizukuShell.sh(prootPrefix(base, rootfs) + "/bin/bash -lc " + q(c));
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
