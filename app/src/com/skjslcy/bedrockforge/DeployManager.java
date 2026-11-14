package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 / 运行环境的部署、检测与删除。
 * 所有实际文件操作都通过 Shizuku shell 完成。
 */
public final class DeployManager {

    private static final JSONObject OFFICIAL_URLS = new JSONObject();
    private static volatile boolean repairingRuntime;
    private static final java.util.Set<String> MCAPKS_VERSIONS = new java.util.HashSet<>();
    public static final int TYPE_SERVER = 0;
    public static final int TYPE_RUNTIME = 1;

    public static final String BASE = "/data/local/tmp/mcbds";
    public static final String SERVERS_DIR = BASE + "/servers";
    public static final String SERVER_LINK = BASE + "/server";
    public static final String SERVER_DIR = SERVER_LINK;
    public static final String TMP = BASE + "/tmp";
    public static final String BACKUPS_DIR = BASE + "/backups";
    public static final String BOX64_BASE = "/data/local/tmp/box64";
    public static final String BOX64_ROOTFS = BOX64_BASE + "/rootfs";
    public static final String BOX64_BIN = BOX64_ROOTFS + "/usr/local/bin/box64";
    public static final String BOX64_READY = BOX64_BASE + "/.mcbds_ready";
    public static final String BOX64_PROOT = BASE + "/proot/proot";
    public static final String BOX64_UBUNTU_URL =
            "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz";
    public static final String BOX64_SOURCE_URL =
            "https://ghfast.top/https://github.com/ptitSeb/box64/archive/refs/heads/main.zip";
    public static final String BDS_12663_URL =
            "https://www.minecraft.net/bedrockdedicatedserver/bin-linux/bedrock-server-1.26.63.1.zip";


    public static final String UBUNTU_URL =
            "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.3-base-amd64.tar.gz";
    public static final String UBUNTU_22_URL =
            "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-amd64.tar.gz";
    public static final String UBUNTU_20_URL =
            "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/20.04/release/ubuntu-base-20.04.5-base-amd64.tar.gz";

    public interface Progress {
        void update(int percent, String message);
    }

    public static final class Status {
        public boolean installed;
        public String name = "";
        public String version = "";
        public String path = "";
        public String source = "";
        public String note = "";
        public String activeVersion = "";
        public List<String> versions = new ArrayList<>();
        public boolean box64;
    public String box64Version = "";
    }

    private DeployManager() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("deploy_meta", Context.MODE_PRIVATE);
    }

    public static void setRepairingRuntime(boolean v) {
        repairingRuntime = v;
    }

    public static boolean isRepairingRuntime() {
        return repairingRuntime;
    }

    public static Status detect(Context context, int type) throws Exception {
        SharedPreferences sp = prefs(context);
        ensureLayout();
        Status s = new Status();
        if (type == TYPE_SERVER) {
            if (exists(SERVER_DIR + "/bedrock_server")) {
                s.installed = true;
                s.name = "Minecraft Bedrock Dedicated Server";
                s.path = SERVER_DIR;
                s.version = detectServerVersion(context);
                String markerSource = "";
                try {
                    markerSource = ShizukuShell.sh("cat " + q(SERVER_DIR + "/.mcbds_source")
                            + " 2>/dev/null || true").trim();
                } catch (Throwable ignored) {
                }
                s.source = markerSource.isEmpty() ? sp.getString("server_source", "本地部署") : markerSource;
                s.activeVersion = activeVersion();
                s.versions = listServerVersions();
            }
        } else if (type == TYPE_RUNTIME) {
            if (repairingRuntime) {
                // 修复过程中旧环境已删除，不能继续显示旧版本。
                s.installed = false;
                s.name = "Box64 + Ubuntu ARM64";
                s.version = I18n.t("未部署");
                s.note = I18n.t("正在重建运行环境…");
                return s;
            }
            boolean boxOs = exists(BOX64_ROOTFS + "/etc/os-release");
            boolean boxBin = exists(BOX64_BIN);
            if (boxOs && boxBin) {
                String marker = "";
                try {
                    marker = ShizukuShell.sh("cat " + q(BOX64_READY) + " 2>/dev/null || true").trim();
                } catch (Throwable ignored) {
                }
                if (marker.startsWith("Box64")) {
                    s.installed = true;
                    s.box64 = true;
                    s.name = "Box64 + Ubuntu ARM64";
                    s.path = BOX64_ROOTFS;
                    s.version = detectUbuntuName();
                    s.box64Version = marker;
                    s.source = sp.getString("box64_source", "本地部署");
                    s.note = "proot: " + BOX64_PROOT;
                    return s;
                }
                long now = System.currentTimeMillis();
                long last = sp.getLong("runtime_check_at", 0);
                if (now - last > 60000) {
                    sp.edit().putLong("runtime_check_at", now).apply();
                    String bx = detectBox64Version();
                    if (!bx.isEmpty()) {
                        try {
                            ShizukuShell.sh("printf '%s' " + q(bx) + " > " + q(BOX64_READY)
                                    + "; chmod 644 " + q(BOX64_READY));
                        } catch (Throwable ignored) {
                        }
                        s.installed = true;
                        s.box64 = true;
                        s.name = "Box64 + Ubuntu ARM64";
                        s.path = BOX64_ROOTFS;
                        s.version = detectUbuntuName();
                        s.box64Version = bx;
                        s.source = sp.getString("box64_source", "本地部署");
                        s.note = "proot: " + BOX64_PROOT;
                        return s;
                    }
                }
                s.installed = false;
                s.box64 = false;
                s.name = "Box64 + Ubuntu ARM64";
                s.version = I18n.t("未部署");
                s.note = I18n.t("Box64 未编译或运行环境不完整，请点“修复运行环境”");
                return s;
            }
        }
        return s;
    }

    private static String detectServerVersion(Context context) {
        try {
            String marker = ShizukuShell.sh("cat " + q(SERVER_DIR + "/.mcbds_version")
                    + " 2>/dev/null || true").trim();
            if (!marker.isEmpty()) {
                prefs(context).edit().putString("server_version", marker).apply();
                return marker;
            }
        } catch (Throwable ignored) {
        }
        try {
            String out = ShizukuShell.sh("grep -a 'Version:' " + q(SERVER_DIR + "/Dedicated_Server.txt")
                    + " 2>/dev/null | tail -n 1 || true").trim();
            int i = out.lastIndexOf("Version:");
            if (i >= 0) {
                String v = out.substring(i + "Version:".length()).trim();
                if (!v.isEmpty()) {
                    prefs(context).edit().putString("server_version", v).apply();
                    return v;
                }
            }
        } catch (Throwable ignored) {
        }
        String saved = prefs(context).getString("server_version", "");
        return saved == null || saved.isEmpty() ? I18n.t("未知") : saved;
    }

    private static String detectUbuntuName() {
        String os = "Ubuntu ARM64";
        try {
            String out = ShizukuShell.sh("grep '^PRETTY_NAME=' " + q(BOX64_ROOTFS + "/etc/os-release")
                    + " 2>/dev/null | head -n 1 || true").trim();
            int i = out.indexOf('=');
            if (i > 0) os = out.substring(i + 1).trim().replace("\"", "");
            if (os.contains("proot") || os.contains("warning")) os = "Ubuntu ARM64";
        } catch (Throwable ignored) {
        }
        return os;
    }

    private static String detectBox64Version() {
        try {
            String cmd = "cd " + q(BOX64_BASE) + " && "
                    + "PROOT_TMP_DIR=" + q(BOX64_BASE + "/tmp")
                    + " PROOT_LOADER=" + q(BASE + "/proot/libexec/loader")
                    + " PROOT_LOADER_32=" + q(BASE + "/proot/libexec/loader32")
                    + " LD_LIBRARY_PATH=" + q(BASE + "/proot/lib")
                    + " " + q(BOX64_PROOT) + " --link2symlink -r " + q(BOX64_ROOTFS) + " -0"
                    + " /usr/local/bin/box64 --version 2>&1"
                    + " | grep '^Box64' | head -n 1 || true";
            String bx = ShizukuShell.sh(cmd).trim();
            int cut = bx.indexOf(" with ");
            if (cut > 0) bx = bx.substring(0, cut).trim();
            return bx.startsWith("Box64") ? bx : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String detectBox64RuntimeVersion() {
        String bx = detectBox64Version();
        String os = detectUbuntuName();
        return os + (bx.isEmpty() ? "" : " · " + bx);
    }

    private static void ensureLayout() throws Exception {
        ShizukuShell.sh("mkdir -p " + q(SERVERS_DIR));
        ShizukuShell.sh("if [ -d " + q(BASE + "/server12662") + " ] && [ ! -e "
                + q(SERVERS_DIR + "/1.26.62.1") + " ]; then mv " + q(BASE + "/server12662")
                + " " + q(SERVERS_DIR + "/1.26.62.1") + "; fi");
        ShizukuShell.sh("if [ ! -e " + q(SERVER_LINK) + " ] && [ -d "
                + q(SERVERS_DIR + "/1.26.62.1") + " ]; then rm -f " + q(SERVER_LINK)
                + "; ln -s servers/1.26.62.1 " + q(SERVER_LINK) + "; fi");
    }

    public static List<String> listServerVersions() throws Exception {
        String out = ShizukuShell.sh("for d in " + q(SERVERS_DIR) + "/*; do [ -d \"$d\" ] && basename \"$d\"; done 2>/dev/null || true");
        List<String> list = new ArrayList<>();
        for (String line : out.split("\n")) {
            String v = line.trim();
            if (!v.isEmpty()) list.add(v);
        }
        java.util.Collections.sort(list, java.util.Collections.reverseOrder());
        return list;
    }

    public static String activeVersion() throws Exception {
        String path = ShizukuShell.sh("readlink -f " + q(SERVER_LINK) + " 2>/dev/null || true").trim();
        if (!path.isEmpty() && path.contains("/")) {
            return path.substring(path.lastIndexOf('/') + 1);
        }
        return "";
    }

    public static void switchServer(Context context, String version) throws Exception {
        if (ServerManager.get().isRunning()) {
            throw new IOException(I18n.t("请先停止服务端，再切换实例"));
        }
        if (!exists(SERVERS_DIR + "/" + version + "/bedrock_server")) {
            throw new IOException(I18n.t("版本目录不存在：") + version);
        }
        ShizukuShell.sh("rm -f " + q(SERVER_LINK) + "; ln -s " + q("servers/" + version)
                + " " + q(SERVER_LINK));
        AppLog.add(I18n.t("[实例] 切换到 ") + version);
        SharedPreferences sp = prefs(context);
        String source = "";
        try {
            source = ShizukuShell.sh("cat " + q(SERVERS_DIR + "/" + version + "/.mcbds_source")
                    + " 2>/dev/null || true").trim();
        } catch (Throwable ignored) {
        }
        if (source.isEmpty()) source = sp.getString("server_source", "本地部署");
        sp.edit().putString("server_version", version)
                .putString("server_source", source).apply();
    }

    public static void deleteServerVersion(Context context, String version) throws Exception {
        String active = activeVersion();
        ShizukuShell.sh("rm -rf " + q(SERVERS_DIR + "/" + version));
        SharedPreferences.Editor e = prefs(context).edit();
        e.remove("server_source_" + version);
        e.apply();
        if (version.equals(active)) {
            List<String> left = listServerVersions();
            if (left.isEmpty()) {
                ShizukuShell.sh("rm -f " + q(SERVER_LINK));
                e.remove("server_version").remove("server_source").apply();
            } else {
                switchServer(context, left.get(0));
            }
        }
    }

    public static void delete(Context context, int type) throws Exception {
        SharedPreferences sp = prefs(context);
        if (type == TYPE_SERVER) {
            String active = activeVersion();
            if (!active.isEmpty()) {
                deleteServerVersion(context, active);
            } else {
                ShizukuShell.sh("rm -f " + q(SERVER_LINK));
                sp.edit().remove("server_source").remove("server_version").apply();
            }
        }
    }

    public static void backupInstance(Context context, String id, Progress p) throws Exception {
        if (!exists(SERVERS_DIR + "/" + id + "/bedrock_server")) {
            throw new IOException(I18n.t("实例不存在：") + id);
        }
        ShizukuShell.sh("mkdir -p " + q(BACKUPS_DIR));
        long ts = System.currentTimeMillis();
        String name = "server-" + id + "-" + ts + ".tar.gz";
        p.update(20, I18n.t("打包实例备份…"));
        ShizukuShell.sh("tar -czf " + q(BACKUPS_DIR + "/" + name) + " -C " + q(SERVERS_DIR)
                + " " + q(id) + " 2>/dev/null || true");
        if (!exists(BACKUPS_DIR + "/" + name)) throw new IOException(I18n.t("备份文件未生成"));
        String version = ShizukuShell.sh("cat " + q(SERVERS_DIR + "/" + id + "/.mcbds_version")
                + " 2>/dev/null || true").trim();
        try {
            org.json.JSONObject meta = new org.json.JSONObject();
            meta.put("instanceId", id);
            meta.put("bdsVersion", version);
            meta.put("createdAt", ts);
            meta.put("kind", "server");
            meta.put("file", name);
            ShizukuShell.write(BACKUPS_DIR + "/" + name + ".json", meta.toString(2));
        } catch (Throwable ignored) {
        }
        p.update(100, I18n.t("实例备份完成"));
        AppLog.add(I18n.t("[备份] 实例 ") + id);
    }

    public static void backupPart(Context context, String kind, Progress p) throws Exception {
        String active = activeVersion();
        if (active.isEmpty()) throw new IOException(I18n.t("没有可备份的实例"));
        ShizukuShell.sh("mkdir -p " + q(BACKUPS_DIR));
        long ts = System.currentTimeMillis();
        String name;
        String cmd;
        String extra = "";
        if ("world".equals(kind)) {
            String world = "Bedrock level";
            try {
                String props = ShizukuShell.read(SERVER_DIR + "/server.properties");
                for (String line : props.split("\n")) {
                    if (line.trim().startsWith("level-name=")) {
                        world = line.trim().substring("level-name=".length()).trim();
                    }
                }
            } catch (Throwable ignored) {
            }
            name = "world-" + active + "-" + ts + ".tar.gz";
            cmd = "tar -czf " + q(BACKUPS_DIR + "/" + name) + " -C "
                    + q(SERVER_DIR + "/worlds") + " " + q(world) + " 2>/dev/null || true";
            extra = world;
        } else if ("addons".equals(kind)) {
            name = "addons-" + active + "-" + ts + ".tar.gz";
            cmd = "tar -czf " + q(BACKUPS_DIR + "/" + name) + " -C " + q(SERVER_DIR)
                    + " behavior_packs resource_packs development_behavior_packs "
                    + "development_resource_packs config addons 2>/dev/null || true";
        } else if ("players".equals(kind)) {
            name = "players-" + active + "-" + ts + ".tar.gz";
            cmd = "tar -czf " + q(BACKUPS_DIR + "/" + name) + " -C " + q(SERVER_DIR)
                    + " players.json permissions.json allowlist.json 2>/dev/null || true";
        } else {
            throw new IOException(I18n.t("未知备份类型"));
        }
        p.update(20, I18n.t("打包 ") + kind + I18n.t(" 备份…"));
        ShizukuShell.sh(cmd);
        if (!exists(BACKUPS_DIR + "/" + name)) throw new IOException(I18n.t("备份文件未生成"));
        try {
            org.json.JSONObject meta = new org.json.JSONObject();
            meta.put("instanceId", active);
            meta.put("bdsVersion", active);
            meta.put("createdAt", ts);
            meta.put("kind", kind);
            meta.put("file", name);
            if (!extra.isEmpty()) meta.put("world", extra);
            ShizukuShell.write(BACKUPS_DIR + "/" + name + ".json", meta.toString(2));
        } catch (Throwable ignored) {
        }
        p.update(100, kind + I18n.t(" 备份完成"));
        AppLog.add(I18n.t("[备份] ") + kind + " " + active);
    }

    public static File backup(Context context, int type, Progress p) throws Exception {
        ShizukuShell.sh("mkdir -p " + q(BACKUPS_DIR));
        long ts = System.currentTimeMillis();
        String name;
        String cmd;
        if (type == TYPE_SERVER) {
            String active = activeVersion();
            if (active.isEmpty()) throw new IOException(I18n.t("没有可备份的服务端版本"));
            name = "server-" + active + "-" + ts + ".tar.gz";
            cmd = "tar -czf " + q(BACKUPS_DIR + "/" + name) + " -C " + q(SERVERS_DIR)
                    + " " + q(active) + " 2>/dev/null || true";
        } else {
            throw new IOException(I18n.t("不支持的备份类型"));
        }
        p.update(20, I18n.t("打包备份…"));
        ShizukuShell.sh(cmd);
        if (!exists(BACKUPS_DIR + "/" + name)) throw new IOException(I18n.t("备份文件未生成"));
        try {
            if (type != TYPE_RUNTIME) {
                String active = activeVersion();
                org.json.JSONObject meta = new org.json.JSONObject();
                meta.put("instanceId", active);
                meta.put("bdsVersion", active);
                meta.put("createdAt", ts);
                meta.put("kind", type == TYPE_SERVER ? "server" : "runtime");
                meta.put("file", name);
                ShizukuShell.write(BACKUPS_DIR + "/" + name + ".json", meta.toString(2));
            }
        } catch (Throwable ignored) {
        }
        p.update(100, I18n.t("备份完成：") + name);
        return new File(BACKUPS_DIR + "/" + name);
    }

    public static List<String> listBackups(int type) throws Exception {
        String prefix = type == TYPE_SERVER ? "server-" : "runtime-";
        String out = ShizukuShell.sh("ls -1t " + q(BACKUPS_DIR) + "/" + prefix
                + "*.tar.gz 2>/dev/null || true");
        List<String> list = new ArrayList<>();
        for (String line : out.split("\n")) {
            String v = line.trim();
            if (!v.isEmpty()) list.add(v.substring(v.lastIndexOf('/') + 1));
        }
        return list;
    }

    public static List<String> listAllBackups() throws Exception {
        String out = ShizukuShell.sh("ls -1t " + q(BACKUPS_DIR) + "/*.tar.gz 2>/dev/null || true");
        List<String> list = new ArrayList<>();
        for (String line : out.split("\n")) {
            String v = line.trim();
            if (!v.isEmpty()) list.add(v.substring(v.lastIndexOf('/') + 1));
        }
        return list;
    }

    public static List<String> listBackupsForInstance(String instanceId) throws Exception {
        List<String> out = new ArrayList<>();
        for (String name : listBackups(TYPE_SERVER)) {
            try {
                String raw = ShizukuShell.read(BACKUPS_DIR + "/" + name + ".json");
                org.json.JSONObject o = new org.json.JSONObject(raw);
                if (instanceId.equals(o.optString("instanceId", ""))) out.add(name);
            } catch (Throwable ignored) {
                // 旧备份没有元数据，先不归入任何实例，避免回滚错实例。
            }
        }
        return out;
    }

    public static void restorePart(Context context, String backupName, Progress p) throws Exception {
        if (ServerManager.get().isRunning()) throw new IOException(I18n.t("请先停止服务端"));
        String backup = BACKUPS_DIR + "/" + backupName;
        if (!exists(backup)) throw new IOException(I18n.t("备份不存在"));
        String kind = backupName.startsWith("world-") ? "world"
                : (backupName.startsWith("plugins-") ? "plugins"
                : (backupName.startsWith("addons-") ? "addons"
                : (backupName.startsWith("players-") ? "players" : "")));
        if (kind.isEmpty()) throw new IOException(I18n.t("不是分类备份"));
        String stage = TMP + "/restore-part";
        ShizukuShell.sh("rm -rf " + q(stage) + "; mkdir -p " + q(stage));
        p.update(20, I18n.t("解包分类备份…"));
        ShizukuShell.sh("tar -xzof " + q(backup) + " -C " + q(stage) + " 2>/dev/null || true");
        if ("world".equals(kind)) {
            String level = ShizukuShell.sh("find " + q(stage) + " -name level.dat -type f | head -n 1").trim();
            if (level.isEmpty()) throw new IOException(I18n.t("备份里没有 level.dat"));
            String dir = level.substring(0, level.lastIndexOf('/'));
            String world = dir.substring(dir.lastIndexOf('/') + 1);
            String dest = SERVER_DIR + "/worlds/" + world;
            p.update(70, I18n.t("恢复存档 ") + world + "…");
            ShizukuShell.sh("mkdir -p " + q(SERVER_DIR + "/worlds") + "; rm -rf " + q(dest)
                    + "; cp -a " + q(dir) + " " + q(dest));
        } else {
            p.update(70, I18n.t("恢复 ") + kind + "…");
            ShizukuShell.sh("cp -a " + q(stage) + "/. " + q(SERVER_DIR) + "/");
        }
        ShizukuShell.sh("rm -rf " + q(stage));
        p.update(100, I18n.t("分类备份恢复完成"));
        AppLog.add(I18n.t("[恢复] ") + backupName);
    }

    /** 修复运行环境前清空：服务端实例与保存状态。 */
    public static void wipeServersAndState(Context context, Progress p) throws Exception {
        if (ServerManager.get().isRunning()) {
            throw new IOException(I18n.t("请先停止服务端"));
        }
        p.update(5, I18n.t("删除服务端实例…"));
        ShizukuShell.sh("rm -rf " + q(SERVERS_DIR) + "/* 2>/dev/null || true");
        ShizukuShell.sh("rm -rf " + q(SERVERS_DIR + "/.*") + " 2>/dev/null || true");
        ShizukuShell.sh("rm -f " + q(SERVER_LINK));
        p.update(15, I18n.t("删除旧部署残留…"));
        ShizukuShell.sh("rm -rf " + q(BASE + "/server12662") + " " + q(BASE + "/server"));
        try {
            prefs(context).edit()
                    .remove("server_version").remove("server_source")
                    .apply();
        } catch (Throwable ignored) {
        }
        p.update(100, I18n.t("旧数据已清空"));
    }

    public static void restoreBackup(Context context, int type, String name, Progress p) throws Exception {
        String backup = BACKUPS_DIR + "/" + name;
        if (!exists(backup)) throw new IOException(I18n.t("备份不存在"));
        p.update(10, I18n.t("解包备份…"));
        if (type == TYPE_SERVER) {
            String stage = TMP + "/restore-server";
            ShizukuShell.sh("rm -rf " + q(stage) + "; mkdir -p " + q(stage));
            ShizukuShell.sh("tar -xzof " + q(backup) + " -C " + q(stage) + " 2>/dev/null || true");
            String b = ShizukuShell.sh("find " + q(stage) + " -name bedrock_server -type f | head -n 1 || true").trim();
            if (b.isEmpty()) throw new IOException(I18n.t("备份里没有 bedrock_server"));
            String dir = b.substring(0, b.lastIndexOf('/'));
            String version = dir.substring(dir.lastIndexOf('/') + 1);
            String target = SERVERS_DIR + "/" + version;
            ShizukuShell.sh("rm -rf " + q(target) + " && mv " + q(dir) + " " + q(target));
            ShizukuShell.sh("rm -f " + q(SERVER_LINK) + "; ln -s " + q("servers/" + version)
                    + " " + q(SERVER_LINK));
            prefs(context).edit().putString("server_version", version).apply();
        } else {
            throw new IOException(I18n.t("不支持的备份类型"));
        }
        p.update(100, I18n.t("回滚完成"));
    }
    public static String detectImportedVersion(String dir, String archiveName) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)")
                    .matcher(archiveName == null ? "" : archiveName);
            if (m.find()) return m.group(1);
            String v = ShizukuShell.sh("cat " + q(dir + "/version.txt")
                    + " 2>/dev/null || true").trim();
            if (!v.isEmpty()) return v;
            v = ShizukuShell.sh("cat " + q(dir + "/.mcbds_version")
                    + " 2>/dev/null || true").trim();
            if (!v.isEmpty() && !"未知".equals(v)) return v;
            return "未知";
        } catch (Throwable t) {
            return "未知";
        }
    }

    public static void installServer(Context context, File archive, String source, String version,
                                     Progress p) throws Exception {
        installServer(context, archive, source, version, false, null, p);
    }

    public static void installServer(Context context, File archive, String source, String version,
                                     boolean freshInstance, Progress p) throws Exception {
        installServer(context, archive, source, version, freshInstance, null, p);
    }

    public static void installServer(Context context, File archive, String source, String version,
                                     boolean freshInstance, String instanceName,
                                     Progress p) throws Exception {
        installServer(context, archive, source, version, freshInstance, instanceName, null, p);
    }

    public static void installServer(Context context, File archive, String source, String version,
                                     boolean freshInstance, String instanceName,
                                     String sourceInstanceId, Progress p) throws Exception {
        boolean box64 = exists(BOX64_ROOTFS + "/etc/os-release") && exists(BOX64_BIN);
        if (!box64) {
            throw new IOException(I18n.t("运行环境未安装，请先部署 Box64 运行环境"));
        }
        ensureLayout();
        p.update(1, I18n.t("上传部署包…"));
        String remote = copyArchive(archive, p);
        p.update(12, I18n.t("清理临时目录…"));
        ShizukuShell.sh("rm -rf " + q(BASE + "/tmp/server-stage") + "; mkdir -p " + q(BASE + "/tmp/server-stage"));

        p.update(15, I18n.t("解压服务端包…"));
        extract(remote, BASE + "/tmp/server-stage");
        String stage = BASE + "/tmp/server-stage";

        p.update(55, I18n.t("定位 bedrock_server…"));
        String b = ShizukuShell.sh("find " + q(stage) + " -name bedrock_server -type f 2>/dev/null | head -n 1 || true").trim();
        if (b.isEmpty()) throw new IOException(I18n.t("压缩包内没有 bedrock_server"));
        String dir = b.substring(0, b.lastIndexOf('/'));
        if (!dir.equals(stage)) {
            ShizukuShell.sh("cp -a " + q(dir) + "/. " + q(stage) + "/");
        }
        if (!exists(stage + "/bedrock_server")) {
            throw new IOException(I18n.t("解压后缺少 bedrock_server"));
        }

        p.update(70, freshInstance ? I18n.t("新建独立实例（不复制世界）…") : I18n.t("保留世界与配置…"));
        if (!freshInstance) {
            String sourceDir = SERVER_DIR;
            if (sourceInstanceId != null && !sourceInstanceId.isEmpty()) {
                String candidate = SERVERS_DIR + "/" + sourceInstanceId;
                if (exists(candidate + "/bedrock_server")) sourceDir = candidate;
            }
            String[] keep = {"worlds", "server.properties", "allowlist.json", "permissions.json", "keys"};
            for (String k : keep) {
                ShizukuShell.sh("if [ -e " + q(sourceDir + "/" + k) + "] && [ ! -e " + q(stage + "/" + k)
                        + " ]; then cp -a " + q(sourceDir + "/" + k) + " " + q(stage + "/" + k) + "; fi");
            }
        }
        ShizukuShell.sh("chmod 755 " + q(stage + "/bedrock_server"));

        p.update(85, I18n.t("切换到新服务端版本…"));
        String id = version == null ? "" : version.trim();
        if (id.isEmpty()) id = "import-" + System.currentTimeMillis();
        id = id.replaceAll("[^A-Za-z0-9._-]", "_");
        String target = SERVERS_DIR + "/" + id;
        // 同版本目录已存在时创建独立实例，绝不覆盖旧实例。
        if (exists(target)) {
            id = id + "-" + (System.currentTimeMillis() / 1000);
            target = SERVERS_DIR + "/" + id;
        }
        ShizukuShell.sh("mv " + q(stage) + " " + q(target));
        ShizukuShell.sh("rm -f " + q(SERVER_LINK) + "; ln -s " + q("servers/" + id)
                + " " + q(SERVER_LINK));
        ShizukuShell.sh("rm -f " + q(remote));
        String known = id.matches("\\d.*") ? id : "未知";
        if (known.isEmpty() || "未知".equals(known)) {
            known = detectImportedVersion(target, archive.getName());
        }
        ShizukuShell.sh("printf '%s' " + q(known) + " > " + q(target + "/.mcbds_version")
                + "; printf '%s' " + q(source) + " > " + q(target + "/.mcbds_source"));
        InstanceStore.read(id);
        if (instanceName != null && !instanceName.trim().isEmpty()) {
            InstanceStore.rename(id, instanceName.trim());
        }
        prefs(context).edit()
                .putString("server_version", known)
                .putString("server_source", source)
                .putString("server_source_" + id, source)
                .apply();
        String shown = detectServerVersion(context);
        p.update(100, I18n.t("服务端部署完成") + (shown.equals("未知") || shown.equals(I18n.t("未知")) ? "" : "：" + shown)
                + I18n.t("（已保留旧版本）"));
    }

    public static String currentServerVersion() {
        try {
            String v = ShizukuShell.sh("cat " + q(SERVER_DIR + "/.mcbds_version")
                    + " 2>/dev/null || true").trim();
            return v.isEmpty() ? "未知" : v;
        } catch (Throwable t) {
            return "未知";
        }
    }
    private static String copyArchive(File archive, Progress p) throws Exception {
        ShizukuShell.sh("mkdir -p " + q(TMP));
        String name = archive.getName().toLowerCase();
        String suffix = name.endsWith(".tar.gz") ? ".tar.gz" : (name.endsWith(".tgz") ? ".tgz" : ".zip");
        String remote = TMP + "/upload-" + System.currentTimeMillis() + suffix;
        final int[] last = {-1};
        ShizukuShell.copyIn(archive, remote, new ShizukuShell.CopyProgress() {
            @Override
            public void onProgress(long copied, long total) {
                int percent = total > 0 ? (int) (copied * 9 / total) : 0;
                if (percent != last[0]) {
                    last[0] = percent;
                    p.update(2 + percent, I18n.t("上传部署包 ") + percent + "%");
                }
            }
        });
        return remote;
    }

    private static void extract(String remote, String dest) throws Exception {
        String name = remote.toLowerCase();
        if (name.endsWith(".zip")) {
            ShizukuShell.sh("unzip -q -o " + q(remote) + " -d " + q(dest));
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            // -o: 不恢复 root 属主；Android /data 下 hardlink 被 SELinux 禁止，
            // tar 对 hardlink 条目会报错，但目标文件通常已解出，忽略后由调用方校验。
            ShizukuShell.sh("tar -xzof " + q(remote) + " -C " + q(dest) + " 2>/dev/null || true");
        } else {
            throw new IOException(I18n.t("只支持 .zip / .tar.gz 文件"));
        }
    }

    public static File download(Context context, String url, String fileName, Progress p) throws Exception {
        AppLog.add(I18n.t("[下载] ") + url);
        File out = new File(context.getCacheDir(), fileName);
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            long existing = out.exists() ? out.length() : 0;
            HttpURLConnection c = null;
            InputStream in = null;
            java.io.RandomAccessFile raf = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(60000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/120.0.0.0 Safari/537.36");
                c.setRequestProperty("Accept", "*/*");
                c.setRequestProperty("Referer", "https://www.minecraft.net/en-us/download/server/bedrock");
                c.setRequestProperty("Accept-Encoding", "identity");
                if (existing > 0) c.setRequestProperty("Range", "bytes=" + existing + "-");
                int code = c.getResponseCode();
                if (code == 416) {
                    out.delete();
                    existing = 0;
                    continue;
                }
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " · " + url);
                boolean append = code == 206;
                if (!append) existing = 0;
                long contentLen = c.getContentLengthLong();
                long total = contentLen > 0 ? existing + contentLen : -1;

                in = new BufferedInputStream(c.getInputStream());
                raf = new java.io.RandomAccessFile(out, "rw");
                if (append) raf.seek(existing);
                else raf.setLength(0);

                byte[] buf = new byte[65536];
                long copied = existing;
                int lastPercent = -1;
                int n;
                while ((n = in.read(buf)) > 0) {
                    raf.write(buf, 0, n);
                    copied += n;
                    int percent = total > 0 ? (int) (copied * 100 / total) : 0;
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        p.update(Math.min(70, percent * 70 / 100), I18n.t("下载中 ") + percent + "%");
                    }
                }
                if (total > 0 && copied < total) throw new IOException(I18n.t("连接提前结束"));
                p.update(72, I18n.t("下载完成"));
                return out;
            } catch (IOException t) {
                last = t;
                if (attempt < 3) {
                    p.update(2, I18n.t("连接中断，正在重试 ") + (attempt + 1) + "/4…");
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
                }
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) { }
                try { if (raf != null) raf.close(); } catch (Throwable ignored) { }
                if (c != null) c.disconnect();
            }
        }
        throw last == null ? new IOException(I18n.t("下载失败")) : last;
    }

    public static List<String> serverVersions() throws Exception {
        List<String> out = new ArrayList<>();
        loadOfficialUrls();
        java.util.Iterator<String> it = OFFICIAL_URLS.keys();
        while (it.hasNext()) {
            String v = it.next();
            if (!out.contains(v)) out.add(v);
        }
        loadMcapksVersions(out);
        java.util.Collections.sort(out, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) {
                return compareVersion(b, a);
            }
        });
        if (out.isEmpty()) throw new IOException(I18n.t("版本源没有返回可用版本"));
        return out;
    }

    private static void loadMcapksVersions(List<String> out) throws Exception {
        String body = httpGet("https://mcapks.net/api/get-vslist.php");
        JSONObject root = new JSONObject(body);
        if (!root.optBoolean("success")) return;
        JSONObject data = root.optJSONObject("data");
        if (data == null) return;
        JSONArray arr = data.optJSONArray("versions");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || o.optBoolean("beta")) continue;
            String v = normalizeVersion(o.optString("version", ""));
            if (v.isEmpty()) continue;
            MCAPKS_VERSIONS.add(v);
            if (!out.contains(v)) out.add(v);
        }
    }

    private static String normalizeVersion(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        if (v.startsWith("26.")) v = "1." + v;
        String[] p = v.split("\\.");
        if (p.length == 3) v = v + ".0";
        else if (p.length == 2) v = v + ".0.0";
        return v.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") ? v : "";
    }

    private static int compareVersion(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? parseIntSafe(x[i]) : 0;
            int yi = i < y.length ? parseIntSafe(y[i]) : 0;
            if (xi != yi) return xi < yi ? -1 : 1;
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Throwable t) { return 0; }
    }

    private static void loadOfficialUrls() throws Exception {
        String body = httpGet("https://net-secondary.web.minecraft-services.net/api/v1.0/download/links");
        JSONObject root = new JSONObject(body);
        JSONObject result = root.optJSONObject("result");
        if (result == null) return;
        JSONArray links = result.optJSONArray("links");
        if (links == null) return;
        for (int i = 0; i < links.length(); i++) {
            JSONObject o = links.optJSONObject(i);
            if (o == null) continue;
            if (!"serverBedrockLinux".equals(o.optString("downloadType"))) continue;
            String url = o.optString("downloadUrl", "");
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("bedrock-server-(\\d+\\.\\d+\\.\\d+\\.\\d+)\\.zip").matcher(url);
            if (m.find()) OFFICIAL_URLS.put(m.group(1), url);
        }
    }

    public static String resolveServerUrl(String version) throws Exception {
        if (OFFICIAL_URLS.has(version)) return OFFICIAL_URLS.getString(version);
        // 所有实例版本统一走官方下载地址。
        return "https://www.minecraft.net/bedrockdedicatedserver/bin-linux/bedrock-server-"
                + version + ".zip";
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "MCBDSApp/0.1");
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static boolean exists(String path) throws Exception {
        return "1".equals(ShizukuShell.sh("test -e " + q(path) + " && echo 1 || echo 0").trim());
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
