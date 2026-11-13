package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** addons 管理：行为包 / 资源包的导入、启用、禁用、删除与脚本包识别。 */
public class AddonsSectionView extends ScrollView {

    private static final String[] BEHAVIOR_DIRS = {
            "behavior_packs", "development_behavior_packs"
    };
    private static final String[] RESOURCE_DIRS = {
            "resource_packs", "development_resource_packs"
    };

    private final Activity activity;
    private final DeploySectionView.Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout col;
    private final TextView status;
    private boolean busy;

    private static final class Pack {
        String dir;
        String name;
        String uuid = "";
        String version = "";
        boolean script;
        boolean enabled;
        boolean resource;
        String description = "";
    }

    public AddonsSectionView(Activity activity, DeploySectionView.Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        setFillViewport(false);
        col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(8));
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = MinecraftUi.panel(activity, "addons 管理");
        head.addView(MinecraftUi.text(activity,
                "行为包、资源包；脚本包（Script API）会单独标记。\n"
                        + "启用/禁用会写入当前世界的 world_behavior_packs.json / world_resource_packs.json。",
                9, MinecraftUi.PANEL_MUTED, false));
        status = MinecraftUi.text(activity, "正在读取…", 8.5f, MinecraftUi.PANEL_MUTED, false);
        status.setPadding(0, dp(6), 0, 0);
        head.addView(status);
        Button imp = new MinecraftUi.McButton(activity, "导入 addon（.mcpack / .zip）");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        ilp.topMargin = dp(6);
        head.addView(imp, ilp);
        imp.setOnClickListener(v -> importAddon());
        Button refresh = new MinecraftUi.McButton(activity, "刷新列表");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        rlp.topMargin = dp(5);
        head.addView(refresh, rlp);
        refresh.setOnClickListener(v -> refresh());
        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refresh();
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String levelName() {
        try {
            String props = ShizukuShell.read(DeployManager.SERVER_DIR + "/server.properties");
            for (String line : props.split("\n")) {
                String t = line.trim();
                if (t.startsWith("level-name=")) {
                    String v = t.substring("level-name=".length()).trim();
                    if (!v.isEmpty()) return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return "Bedrock level";
    }

    private void refresh() {
        if (busy) return;
        busy = true;
        status.setText("正在读取 addons…");
        new Thread(() -> {
            try {
                final String world = levelName();
                final List<Pack> packs = scanPacks(world);
                handler.post(() -> {
                    busy = false;
                    render(world, packs);
                });
            } catch (Throwable e) {
                handler.post(() -> {
                    busy = false;
                    status.setText("读取失败：" + e.getMessage());
                });
            }
        }, "addons-scan").start();
    }

    private List<Pack> scanPacks(String world) {
        List<Pack> out = new ArrayList<>();
        Map<String, Boolean> behaviorEnabled = enabledMap(world, "world_behavior_packs.json");
        Map<String, Boolean> resourceEnabled = enabledMap(world, "world_resource_packs.json");
        for (String dir : BEHAVIOR_DIRS) addPacks(out, dir, false, behaviorEnabled);
        for (String dir : RESOURCE_DIRS) addPacks(out, dir, true, resourceEnabled);
        return out;
    }

    private void addPacks(List<Pack> out, String base, boolean resource,
                          Map<String, Boolean> enabled) {
        String root = DeployManager.SERVER_DIR + "/" + base;
        String list = "";
        try {
            list = ShizukuShell.sh("ls -1 " + q(root) + " 2>/dev/null || true");
        } catch (Throwable ignored) {
        }
        for (String line : list.split("\n")) {
            String name = line.trim();
            if (name.isEmpty()) continue;
            if (isBuiltinPack(name)) continue;
            String dir = root + "/" + name;
            if (!"1".equals(sh("test -d " + q(dir) + " && echo 1 || echo 0").trim())) continue;
            String manifest = sh("cat " + q(dir + "/manifest.json") + " 2>/dev/null || true");
            Pack p = new Pack();
            p.dir = dir;
            p.name = name;
            p.resource = resource;
            try {
                JSONObject m = new JSONObject(manifest);
                JSONObject header = m.optJSONObject("header");
                if (header != null) {
                    String hn = header.optString("name", "");
                    if (!hn.isEmpty()) p.name = hn;
                    p.uuid = header.optString("uuid", "");
                    p.description = header.optString("description", "");
                    p.version = versionString(header.optJSONArray("version"));
                }
                JSONArray modules = m.optJSONArray("modules");
                if (modules != null) {
                    for (int i = 0; i < modules.length(); i++) {
                        JSONObject mod = modules.optJSONObject(i);
                        if (mod == null) continue;
                        String type = mod.optString("type", "");
                        if ("script".equalsIgnoreCase(type) || "javascript".equalsIgnoreCase(type)
                                || "client_data".equalsIgnoreCase(type)) {
                            p.script = true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            if (p.name.isEmpty() || p.name.contains("pack.name")
                    || p.name.matches("[A-Za-z0-9_]+\\.[A-Za-z0-9_.]+")) {
                p.name = name;
            }
            if (p.uuid.isEmpty()) {
                p.uuid = sh("cat " + q(dir + "/uuid.txt") + " 2>/dev/null || true").trim();
            }
            Boolean on = enabled.get(p.uuid.toLowerCase());
            p.enabled = on != null && on;
            out.add(p);
        }
    }

    private static boolean isBuiltinPack(String name) {
        String n = name.toLowerCase();
        if (n.equals("vanilla") || n.startsWith("vanilla_")) return true;
        if (n.equals("chemistry") || n.startsWith("chemistry_")) return true;
        if (n.equals("editor") || n.startsWith("editor_")) return true;
        if (n.startsWith("experimental_")) return true;
        if (n.startsWith("server_") || n.endsWith("_library")) return true;
        if (n.equals("persona") || n.startsWith("persona_")) return true;
        return false;
    }

    private static String versionString(JSONArray ver) {
        if (ver == null || ver.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ver.length(); i++) {
            if (i > 0) sb.append('.');
            sb.append(ver.optInt(i));
        }
        return sb.toString();
    }

    private Map<String, Boolean> enabledMap(String world, String file) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        String path = DeployManager.SERVER_DIR + "/worlds/" + world + "/" + file;
        try {
            String raw = ShizukuShell.read(path);
            if (raw == null || raw.trim().isEmpty()) return map;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("pack_id", "");
                if (!id.isEmpty()) map.put(id.toLowerCase(), true);
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    private void render(String world, List<Pack> packs) {
        col.removeViews(1, Math.max(0, col.getChildCount() - 1));
        status.setText("世界：" + world + "　共 " + packs.size() + " 个 addon");
        if (packs.isEmpty()) {
            LinearLayout panel = MinecraftUi.panel(activity, "addon 列表");
            panel.addView(MinecraftUi.text(activity,
                    "还没有导入任何行为包或资源包。\n点上方按钮导入 .mcpack / .zip。",
                    9, MinecraftUi.PANEL_MUTED, false));
            col.addView(panel);
            return;
        }
        LinearLayout panel = MinecraftUi.panel(activity, "addon 列表");
        for (final Pack p : packs) {
            LinearLayout box = new LinearLayout(activity);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(MinecraftUi.slotDrawable(activity));
            box.setPadding(dp(9), dp(7), dp(9), dp(7));

            String title = p.name + (p.resource ? "（资源包）" : "（行为包）")
                    + (p.script ? " · 脚本" : "");
            TextView t = MinecraftUi.text(activity, title, 11, MinecraftUi.PANEL_TEXT, true);
            box.addView(t);
            String info = "ID：" + (p.uuid.isEmpty() ? "未知" : p.uuid)
                    + "\n版本：" + (p.version.isEmpty() ? "未知" : p.version)
                    + "\n状态：" + (p.enabled ? "已启用" : "未启用")
                    + "\n目录：" + p.dir.substring(p.dir.lastIndexOf('/') + 1);
            box.addView(MinecraftUi.text(activity, info, 8.5f,
                    p.enabled ? 0xFFB8FFB0 : MinecraftUi.PANEL_MUTED, false));

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button toggle = new MinecraftUi.McButton(activity,
                    p.enabled ? "禁用" : (p.script ? "启用脚本包" : "启用"));
            toggle.setTextSize(10);
            row.addView(toggle, new LinearLayout.LayoutParams(0, dp(38), 1f));
            toggle.setOnClickListener(v -> togglePack(p));
            Button del = new MinecraftUi.McButton(activity, "删除");
            if (del instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) del).setDangerStyle();
            del.setTextSize(10);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            dlp.leftMargin = dp(4);
            row.addView(del, dlp);
            del.setOnClickListener(v -> confirmDelete(p));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(5);
            box.addView(row, rlp);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            panel.addView(box, lp);
        }
        col.addView(panel);
    }

    private void togglePack(final Pack p) {
        if (busy) return;
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端再修改 addons");
            return;
        }
        busy = true;
        status.setText("正在写入世界配置…");
        new Thread(() -> {
            try {
                String world = levelName();
                String file = p.resource ? "world_resource_packs.json"
                        : "world_behavior_packs.json";
                String path = DeployManager.SERVER_DIR + "/worlds/" + world + "/" + file;
                JSONArray arr = new JSONArray();
                try {
                    String raw = ShizukuShell.read(path);
                    if (raw != null && !raw.trim().isEmpty()) arr = new JSONArray(raw);
                } catch (Throwable ignored) {
                }
                JSONArray next = new JSONArray();
                boolean removed = false;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    if (o.optString("pack_id", "").equalsIgnoreCase(p.uuid)) {
                        removed = true;
                        continue;
                    }
                    next.put(o);
                }
                if (!removed && !p.uuid.isEmpty()) {
                    JSONObject o = new JSONObject();
                    o.put("pack_id", p.uuid);
                    JSONArray ver = new JSONArray();
                    for (String s : p.version.split("\\.")) {
                        try {
                            ver.put(Integer.parseInt(s));
                        } catch (Throwable ignored) {
                        }
                    }
                    if (ver.length() == 0) {
                        ver.put(1);
                        ver.put(0);
                        ver.put(0);
                    }
                    o.put("version", ver);
                    next.put(o);
                }
                final boolean wasRemoved = removed;
                ShizukuShell.write(path, next.toString(2));
                handler.post(() -> {
                    busy = false;
                    toast(wasRemoved ? "已禁用 " + p.name : "已启用 " + p.name);
                    refresh();
                });
            } catch (Throwable e) {
                handler.post(() -> {
                    busy = false;
                    status.setText("写入失败：" + e.getMessage());
                });
            }
        }, "addons-toggle").start();
    }

    private void confirmDelete(final Pack p) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "删除 addon");
        panel.addView(MinecraftUi.text(activity,
                "删除 " + p.name + "？\n会从实例目录移除该包，不影响世界存档。",
                9.5f, MinecraftUi.PANEL_TEXT, false));
        Button ok = new MinecraftUi.McButton(activity, "删除");
        if (ok instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) ok).setDangerStyle();
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        olp.topMargin = dp(8);
        panel.addView(ok, olp);
        ok.setOnClickListener(v -> {
            d.dismiss();
            new Thread(() -> {
                try {
                    ShizukuShell.sh("rm -rf " + q(p.dir));
                } catch (Throwable ignored) {
                }
                handler.post(() -> {
                    toast("已删除 " + p.name);
                    refresh();
                });
            }, "addons-delete").start();
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void importAddon() {
        if (busy) return;
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端再导入 addons");
            return;
        }
        host.pickFile(new DeploySectionView.PickCallback() {
            @Override public void onPicked(File file) {
                doImport(file);
            }

            @Override public void onCancel() {
            }
        });
    }

    private void doImport(final File file) {
        if (busy) return;
        busy = true;
        status.setText("正在导入 " + file.getName() + " …");
        new Thread(() -> {
            try {
                String remote = "/data/local/tmp/mcbds/addon-import.zip";
                ShizukuShell.copyIn(file, remote, null);
                String stage = DeployManager.BASE + "/tmp/addon-stage";
                ShizukuShell.sh("rm -rf " + q(stage) + "; mkdir -p " + q(stage)
                        + "; unzip -q -o " + q(remote) + " -d " + q(stage));
                String find = ShizukuShell.sh("find " + q(stage) + " -maxdepth 3 -name manifest.json"
                        + " | head -n 1").trim();
                if (find.isEmpty()) throw new Exception("压缩包里没有 manifest.json");
                String root = find.substring(0, find.lastIndexOf('/'));
                String manifest = ShizukuShell.read(find);
                JSONObject m = new JSONObject(manifest);
                JSONObject header = m.optJSONObject("header");
                String uuid = header == null ? "" : header.optString("uuid", "");
                String packName = header == null ? "" : header.optString("name", "");
                if (packName.isEmpty()) packName = file.getName().replaceAll("\\.[^.]+$", "");
                JSONArray modules = m.optJSONArray("modules");
                boolean resource = false;
                boolean script = false;
                if (modules != null) {
                    for (int i = 0; i < modules.length(); i++) {
                        JSONObject mod = modules.optJSONObject(i);
                        if (mod == null) continue;
                        String type = mod.optString("type", "");
                        if ("resources".equalsIgnoreCase(type)) resource = true;
                        if ("script".equalsIgnoreCase(type) || "javascript".equalsIgnoreCase(type)) {
                            script = true;
                        }
                    }
                }
                if (!resource && !script) {
                    // 没有 resources 模块时按行为包处理；纯皮肤包不支持
                }
                String targetBase = resource ? "resource_packs" : "behavior_packs";
                String safe = packName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (safe.isEmpty()) safe = "addon-" + System.currentTimeMillis();
                String dest = DeployManager.SERVER_DIR + "/" + targetBase + "/" + safe;
                ShizukuShell.sh("rm -rf " + q(dest) + "; mkdir -p " + q(dest)
                        + "; cp -a " + q(root) + "/. " + q(dest) + "/");
                ShizukuShell.sh("rm -f " + q(remote) + "; rm -rf " + q(stage));
                final String msg = "已导入 " + packName
                        + (resource ? "（资源包）" : (script ? "（脚本行为包）" : "（行为包）"))
                        + "\nUUID：" + uuid;
                AppLog.add("[addons] 导入 " + packName + " -> " + targetBase);
                handler.post(() -> {
                    busy = false;
                    toast(msg);
                    refresh();
                });
            } catch (Throwable e) {
                handler.post(() -> {
                    busy = false;
                    status.setText("导入失败：" + e.getMessage());
                });
            }
        }, "addons-import").start();
    }

    private String sh(String cmd) {
        try {
            return ShizukuShell.sh(cmd);
        } catch (Throwable t) {
            return "";
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(activity, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return MinecraftUi.dp(activity, v);
    }
}
