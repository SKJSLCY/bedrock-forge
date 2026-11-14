package com.skjslcy.bedrockforge;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 服务端实例清单。
 * 现有 /data/local/tmp/mcbds/server 软链接仍作为“当前实例”，
 * 不改变 ServerManager / DeployManager 的启动路径。
 */
public final class InstanceStore {

    public static final class Instance {
        public String id;
        public String name;
        public String bdsVersion;
        public String path;
        public boolean active;
        public boolean hasWorld;
        public String loader;
    }

    private InstanceStore() {
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** 扫描实例目录；只为已有目录补写 instance.json，不移动任何文件。 */
    public static List<Instance> list() throws Exception {
        ShizukuShell.sh("mkdir -p " + q(DeployManager.SERVERS_DIR));
        String out = ShizukuShell.sh("find " + q(DeployManager.SERVERS_DIR)
                + " -mindepth 1 -maxdepth 1 -type d -printf '%f\\n' 2>/dev/null | sort").trim();
        String activePath = ShizukuShell.sh("readlink -f " + q(DeployManager.SERVER_LINK)
                + " 2>/dev/null || true").trim();
        List<Instance> list = new ArrayList<>();
        if (!out.isEmpty()) {
            for (String id : out.split("\n")) {
                id = id.trim();
                if (id.isEmpty() || id.startsWith(".")) continue;
                Instance ins = read(id);
                ins.active = activePath.endsWith("/servers/" + id);
                list.add(ins);
            }
        }
        Collections.sort(list, new Comparator<Instance>() {
            @Override public int compare(Instance a, Instance b) {
                if (a.active != b.active) return a.active ? -1 : 1;
                return a.id.compareTo(b.id);
            }
        });
        return list;
    }

    public static Instance read(String id) throws Exception {
        Instance ins = new Instance();
        ins.id = id;
        ins.path = DeployManager.SERVERS_DIR + "/" + id;
        String raw = "";
        try {
            raw = ShizukuShell.read(ins.path + "/instance.json");
        } catch (Throwable ignored) {
        }
        JSONObject o = null;
        try {
            if (raw != null && !raw.trim().isEmpty()) o = new JSONObject(raw);
        } catch (Throwable ignored) {
        }
        if (o == null) {
            o = new JSONObject();
            o.put("id", id);
            o.put("name", id);
            o.put("bdsVersion", detectVersion(ins.path));
            o.put("createdAt", System.currentTimeMillis());
            ShizukuShell.write(ins.path + "/instance.json", o.toString(2));
        }
        ins.name = o.optString("name", id);
        ins.bdsVersion = o.optString("bdsVersion", detectVersion(ins.path));
        if (ins.bdsVersion == null || ins.bdsVersion.isEmpty() || "未知".equals(ins.bdsVersion)) {
            String repaired = DeployManager.detectImportedVersion(ins.path, id);
            if (repaired != null && !repaired.isEmpty() && !"未知".equals(repaired)) {
                ins.bdsVersion = repaired;
                try {
                    o.put("bdsVersion", repaired);
                    ShizukuShell.write(ins.path + "/instance.json", o.toString(2));
                    ShizukuShell.write(ins.path + "/.mcbds_version", repaired);
                } catch (Throwable ignored) {
                }
            }
        }
        ins.loader = o.optString("loader", "");
        ins.hasWorld = ShizukuShell.sh("test -d " + q(ins.path + "/worlds") + " && echo 1 || echo 0")
                .trim().equals("1");
        return ins;
    }

    private static String detectVersion(String path) {
        try {
            String v = ShizukuShell.sh("cat " + q(path + "/.mcbds_version")
                    + " 2>/dev/null || true").trim();
            return v.isEmpty() ? "未知" : v;
        } catch (Throwable t) {
            return "未知";
        }
    }

    /** 创建实例空目录和元数据；BDS 文件由部署流程随后写入。 */
    public static Instance create(String name, String bdsVersion) throws Exception {
        String base = bdsVersion == null || bdsVersion.isEmpty() ? "server" : bdsVersion;
        String id = base + "-" + (System.currentTimeMillis() / 1000);
        String path = DeployManager.SERVERS_DIR + "/" + id;
        ShizukuShell.sh("mkdir -p " + q(path));
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name == null || name.trim().isEmpty() ? id : name.trim());
        o.put("bdsVersion", bdsVersion == null ? "" : bdsVersion);
        o.put("createdAt", System.currentTimeMillis());
        ShizukuShell.write(path + "/instance.json", o.toString(2));
        ShizukuShell.write(path + "/.mcbds_version", bdsVersion == null ? "" : bdsVersion);
        return read(id);
    }

    /** 修改实例显示名；名称只允许字母和数字。 */
    public static void rename(String id, String name) throws Exception {
        if (name == null || !name.matches("[A-Za-z0-9]{1,32}")) {
            throw new Exception(I18n.t("实例名只支持字母和数字，最长 32 个字符"));
        }
        String path = DeployManager.SERVERS_DIR + "/" + id;
        String active = ShizukuShell.sh("readlink -f " + q(DeployManager.SERVER_LINK)
                + " 2>/dev/null || true").trim();
        if (active.endsWith("/servers/" + id) && ServerManager.get().isRunning()) {
            throw new Exception(I18n.t("服务端运行中，不能重命名当前实例"));
        }
        JSONObject o = new JSONObject();
        try {
            o = new JSONObject(ShizukuShell.read(path + "/instance.json"));
        } catch (Throwable ignored) {
        }
        o.put("id", id);
        o.put("name", name);
        if (!o.has("bdsVersion")) o.put("bdsVersion", detectVersion(path));
        o.put("updatedAt", System.currentTimeMillis());
        ShizukuShell.write(path + "/instance.json", o.toString(2));
    }

    /** 记录当前实例的加载器版本。 */
    /** 只切换软链接；调用方必须先确认服务端已停止。 */
    public static void switchTo(String id) throws Exception {
        String path = DeployManager.SERVERS_DIR + "/" + id;
        if (!ShizukuShell.sh("test -d " + q(path) + " && echo 1 || echo 0").trim().equals("1")) {
            throw new Exception(I18n.t("实例不存在：") + id);
        }
        ShizukuShell.sh("rm -f " + q(DeployManager.SERVER_LINK)
                + " && ln -s " + q("servers/" + id) + " " + q(DeployManager.SERVER_LINK));
    }

    public static void delete(String id) throws Exception {
        if (id == null || id.isEmpty() || id.contains("/") || id.contains("..")) {
            throw new Exception(I18n.t("非法实例 ID"));
        }
        String path = DeployManager.SERVERS_DIR + "/" + id;
        String active = ShizukuShell.sh("readlink -f " + q(DeployManager.SERVER_LINK)
                + " 2>/dev/null || true").trim();
        if (active.endsWith("/servers/" + id)) throw new Exception(I18n.t("当前实例不能删除"));
        ShizukuShell.sh("rm -rf " + q(path));
    }
}
