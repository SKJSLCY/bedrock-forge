package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;

/** 服务器备份页：只做本实例服务端备份和元数据显示。 */
public class BackupSectionView extends ScrollView {

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout col;
    private final TextView status;
    private boolean busy;
    private String filter = "all";
    private final DeploySectionView.Host host;

    public BackupSectionView(Activity activity, DeploySectionView.Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        setFillViewport(false);
        col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(8));
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = MinecraftUi.panel(activity, "备份管理");
        head.addView(MinecraftUi.text(activity,
                "这里只管理已有备份。新建备份请到“服务端 → 实例 → 备份实例”。",
                9, MinecraftUi.PANEL_MUTED, false));
        status = MinecraftUi.text(activity, "就绪", 8.5f, MinecraftUi.PANEL_MUTED, false);
        status.setPadding(0, dp(6), 0, 0);
        head.addView(status);
        HorizontalScrollView hs = new HorizontalScrollView(activity);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(activity);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        String[] keys = {"all", "server", "world", "addons", "players"};
        String[] names = {"全部", "服务端", "存档", "Addons", "玩家"};
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            Button b = new MinecraftUi.McButton(activity, names[i]);
            b.setTextSize(9);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(70), dp(34));
            lp.rightMargin = dp(4);
            b.setOnClickListener(v -> {
                filter = key;
                refreshList();
            });
            filters.addView(b, lp);
        }
        hs.addView(filters);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        hlp.topMargin = dp(6);
        head.addView(hs, hlp);
        Button imp = new MinecraftUi.McButton(activity, "导入备份文件");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        ilp.topMargin = dp(6);
        head.addView(imp, ilp);
        imp.setOnClickListener(v -> importBackup());
        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        refreshList();
    }

    private static String kindFromName(String name) {
        if (name.startsWith("server-")) return "server";
        if (name.startsWith("world-")) return "world";
        if (name.startsWith("plugins-")) return "plugins";
        if (name.startsWith("addons-")) return "addons";
        if (name.startsWith("players-")) return "players";
        return "";
    }

    private void importBackup() {
        if (busy) return;
        host.pickFile(new DeploySectionView.PickCallback() {
            @Override public void onPicked(java.io.File file) {
                String raw = MainActivity.lastPickedName;
                if (raw == null || raw.isEmpty()) raw = file.getName();
                if (!raw.endsWith(".tar.gz") && !raw.endsWith(".tgz")) {
                    toast("只支持 .tar.gz / .tgz");
                    return;
                }
                String clean = raw.replaceAll("^import_\\d+_", "")
                        .replaceAll("[^A-Za-z0-9._-]", "_");
                String kind = kindFromName(clean);
                if (kind.isEmpty()) {
                    chooseImportType(file, clean);
                } else {
                    startImport(file, clean, kind);
                }
            }

            @Override public void onCancel() {
            }
        });
    }

    /** 文件名不带类型前缀时，让用户手动选择备份类型。 */
    private void chooseImportType(final java.io.File file, final String original) {
        final android.app.Dialog d = new android.app.Dialog(activity);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "选择备份类型");
        panel.addView(MinecraftUi.text(activity,
                "文件名里没有类型前缀，无法判断这是哪种备份。\n请选择它是什么：",
                9.5f, MinecraftUi.PANEL_TEXT, false));
        String[] kinds = {"server", "world", "addons", "players"};
        String[] labels = {"服务端实例", "存档", "Addons", "玩家数据"};
        for (int i = 0; i < kinds.length; i++) {
            final String kind = kinds[i];
            Button b = new MinecraftUi.McButton(activity, labels[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            lp.topMargin = i == 0 ? dp(6) : dp(4);
            panel.addView(b, lp);
            b.setOnClickListener(v -> {
                d.dismiss();
                String safe = original.replaceAll("\\.tar\\.gz$", "")
                        .replaceAll("\\.tgz$", "");
                startImport(file, kind + "-imported-" + System.currentTimeMillis()
                        + "-" + safe + ".tar.gz", kind);
            });
        }
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(6);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void startImport(final java.io.File file, final String rawName, final String kind) {
        if (busy) return;
        busy = true;
        status.setText("正在导入备份…");
        final String name0 = rawName;
        new Thread(() -> {
            try {
                String name = name0;
                int dup = 1;
                while ("1".equals(ShizukuShell.sh(
                        "test -f '" + DeployManager.BACKUPS_DIR + "/" + name
                                + "' && echo 1 || echo 0").trim())) {
                    name = name0.replace(".tar.gz", "-" + (dup++) + ".tar.gz");
                }
                ShizukuShell.sh("mkdir -p '" + DeployManager.BACKUPS_DIR + "'");
                ShizukuShell.copyIn(file, DeployManager.BACKUPS_DIR + "/" + name, null);
                try {
                    org.json.JSONObject meta = new org.json.JSONObject();
                    meta.put("kind", kind);
                    meta.put("instanceId", "imported");
                    meta.put("createdAt", System.currentTimeMillis());
                    meta.put("file", name);
                    meta.put("imported", true);
                    ShizukuShell.write(DeployManager.BACKUPS_DIR + "/" + name + ".json",
                            meta.toString(2));
                } catch (Throwable ignored) {
                }
                AppLog.add("[备份] 导入 " + name);
                final String done = name;
                handler.post(() -> {
                    busy = false;
                    status.setText("已导入 " + done);
                    refreshList();
                });
            } catch (Throwable e) {
                handler.post(() -> {
                    busy = false;
                    status.setText("导入失败：" + e.getMessage());
                });
            }
        }, "backup-import").start();
    }

    private interface IntCb { void run(int i); }

    private void rollbackPlayer(final String name) {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端再回滚");
            return;
        }
        new Thread(() -> {
            try {
                org.json.JSONObject meta = new org.json.JSONObject(OfflinePlayerData.backupMeta(name));
                final String ver = meta.optString("version");
                final String world = meta.optString("world");
                final String pname = meta.optString("name");
                String found = null;
                for (String[] inst : OfflinePlayerData.instances()) {
                    if (!inst[1].equals(ver)) continue;
                    String dir = OfflinePlayerData.SERVER_BASE + "/" + inst[0];
                    if (OfflinePlayerData.worldsOf(dir).contains(world)) {
                        found = dir;
                        break;
                    }
                }
                final String inst = found;
                handler.post(() -> {
                    if (inst == null) {
                        toast("原世界 " + world + " 已删除，无法回滚");
                        return;
                    }
                    confirm("回滚玩家数据",
                            "将恢复 " + pname + " 到世界 " + world + "（" + ver + "）。\n"
                                    + "会覆盖该世界现有数据。",
                            () -> doApplyPlayer(name, inst, world, true), true);
                });
            } catch (Throwable e) {
                handler.post(() -> toast("读取备份失败：" + e.getMessage()));
            }
        }, "player-rollback").start();
    }

    private void showImportPlayer(final String name) {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端再导入");
            return;
        }
        new Thread(() -> {
            try {
                org.json.JSONObject meta = new org.json.JSONObject(OfflinePlayerData.backupMeta(name));
                final String backupVer = meta.optString("version");
                final java.util.List<String[]> insts = OfflinePlayerData.instances();
                if (insts.isEmpty()) {
                    handler.post(() -> toast("没有可用的服务端实例"));
                    return;
                }
                java.util.List<String> labels = new java.util.ArrayList<>();
                final boolean[] enabled = new boolean[insts.size()];
                for (int i = 0; i < insts.size(); i++) {
                    String ver = insts.get(i)[1];
                    enabled[i] = OfflinePlayerData.compareVersion(ver, backupVer) >= 0;
                    labels.add(insts.get(i)[0] + "（" + ver + "）"
                            + (enabled[i] ? "" : " · 低于备份版本"));
                }
                handler.post(() -> chooseList("选择实例（备份版本 " + backupVer + "）", labels, enabled,
                        idx -> {
                            if (!enabled[idx]) {
                                toast("实例版本低于备份版本，已禁用");
                                return;
                            }
                            chooseImportWorld(name,
                                    OfflinePlayerData.SERVER_BASE + "/" + insts.get(idx)[0],
                                    insts.get(idx)[1]);
                        }));
            } catch (Throwable e) {
                handler.post(() -> toast("读取备份版本失败：" + e.getMessage()));
            }
        }, "player-import-meta").start();
    }

    private void chooseImportWorld(final String name, final String instDir, final String ver) {
        final java.util.List<String> worlds = OfflinePlayerData.worldsOf(instDir);
        if (worlds.isEmpty()) {
            toast("该实例没有世界");
            return;
        }
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (String w : worlds) labels.add(w + " · 版本 " + ver);
        chooseList("选择世界（版本 " + ver + "）", labels, idx -> {
            final String world = worlds.get(idx);
            new Thread(() -> {
                try {
                    org.json.JSONObject meta = new org.json.JSONObject(
                            OfflinePlayerData.backupMeta(name));
                    final String backupVer = meta.optString("version");
                    if (OfflinePlayerData.compareVersion(backupVer, ver) > 0) {
                        handler.post(() -> toast("备份版本 " + backupVer
                                + " 高于目标世界版本 " + ver + "，禁止导入"));
                        return;
                    }
                    final boolean exists = OfflinePlayerData.hasPlayer(activity, instDir, world,
                            meta.optString("msaId"));
                    handler.post(() -> {
                        if (exists) {
                            confirm("覆盖玩家数据",
                                    "目标世界已存在该玩家，导入将覆盖并清理旧数据。\n" + world,
                                    () -> doApplyPlayer(name, instDir, world, false), true);
                        } else {
                            doApplyPlayer(name, instDir, world, false);
                        }
                    });
                } catch (Throwable e) {
                    handler.post(() -> toast("导入检查失败：" + e.getMessage()));
                }
            }, "player-import-check").start();
        });
    }

    private void chooseList(String title, java.util.List<String> items, final IntCb cb) {
        chooseList(title, items, null, cb);
    }

    private void chooseList(String title, java.util.List<String> items,
                            final boolean[] enabled, final IntCb cb) {
        final android.app.Dialog d = new android.app.Dialog(activity);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, title);
        ScrollView sv = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean on = enabled == null || enabled[i];
            Button b = new MinecraftUi.McButton(activity, items.get(i));
            b.setTextSize(9);
            b.setEnabled(on);
            b.setAlpha(on ? 1f : 0.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            lp.topMargin = i == 0 ? 0 : dp(4);
            list.addView(b, lp);
            b.setOnClickListener(v -> {
                if (enabled != null && !enabled[idx]) {
                    toast("实例版本低于备份版本，已禁用");
                    return;
                }
                d.dismiss();
                cb.run(idx);
            });
        }
        sv.addView(list);
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(6);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.7f));
        }
    }

    private void doApplyPlayer(final String name, final String instDir, final String world,
                               final boolean withPosition) {
        new Thread(() -> {
            try {
                org.json.JSONObject meta = new org.json.JSONObject(OfflinePlayerData.backupMeta(name));
                String msa = meta.optString("msaId");
                String sid = meta.optString("serverId");
                byte[] data = OfflinePlayerData.backupMember(activity, name, "data.nbt");
                byte[] stub = OfflinePlayerData.backupMember(activity, name, "stub.bin");
                byte[] reduced = OfflinePlayerData.filtered(data, withPosition);
                OfflinePlayerData.applyPlayer(activity, instDir, world, msa, sid, stub, reduced);
                handler.post(() -> {
                    toast("导入完成：" + world);
                    refreshList();
                });
            } catch (Throwable e) {
                handler.post(() -> toast("导入失败：" + e.getMessage()));
            }
        }, "player-apply").start();
    }

    private void confirmRestore(final String name) {
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端再回滚");
            return;
        }
        String warn = "";
        try {
            String raw = ShizukuShell.read(DeployManager.BACKUPS_DIR + "/" + name + ".json");
            org.json.JSONObject o = new org.json.JSONObject(raw);
            String v = o.optString("bdsVersion", "");
            String active = DeployManager.activeVersion();
            if (!v.isEmpty() && !active.isEmpty() && !v.equals(active)) {
                warn = "注意：备份版本 " + v + "，当前实例 " + active + "。\n"
                        + "跨版本回滚可能导致世界无法加载。\n\n";
            }
        } catch (Throwable ignored) {
        }
        confirm("回滚备份", warn + "将用此备份覆盖当前实例，操作不可撤销。\n" + name,
                () -> doRestore(name));
    }

    private void confirm(String title, String message, final Runnable ok) {
        confirm(title, message, ok, false);
    }

    private void confirm(String title, String message, final Runnable ok, boolean danger) {
        final android.app.Dialog d = new android.app.Dialog(activity);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, title);
        panel.addView(MinecraftUi.text(activity, message, 9.5f, MinecraftUi.PANEL_TEXT, false));
        Button yes = new MinecraftUi.McButton(activity, "确定");
        if (danger && yes instanceof MinecraftUi.McButton) {
            ((MinecraftUi.McButton) yes).setDangerStyle();
            yes.setTextColor(0xFFFFF2F2);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(8);
        panel.addView(yes, lp);
        yes.setOnClickListener(v -> { d.dismiss(); ok.run(); });
        Button no = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp2.topMargin = dp(5);
        panel.addView(no, lp2);
        no.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void doRestore(final String name) {
        if (busy) return;
        busy = true;
        status.setText("正在回滚…");
        new Thread(() -> {
            try {
                if (name.startsWith("server-")) {
                    DeployManager.restoreBackup(activity, DeployManager.TYPE_SERVER, name,
                            (pct, msg) -> handler.post(() -> status.setText(msg)));
                } else {
                    DeployManager.restorePart(activity, name,
                            (pct, msg) -> handler.post(() -> status.setText(msg)));
                }
                handler.post(() -> {
                    busy = false;
                    status.setText("回滚完成");
                    refreshList();
                });
            } catch (Throwable e) {
                handler.post(() -> {
                    busy = false;
                    status.setText("回滚失败：" + e.getMessage());
                });
            }
        }, "restore-backup").start();
    }

    private void confirmDelete(final String name) {
        confirm("删除备份", "永久删除此备份文件？\n" + name, () -> doDelete(name), true);
    }

    private void doDelete(final String name) {
        try {
            String path = DeployManager.BACKUPS_DIR + "/" + name;
            ShizukuShell.sh("rm -f '" + path.replace("'", "'\\''")
                    + "' '" + (path + ".json").replace("'", "'\\''") + "'");
            toast("已删除");
            refreshList();
        } catch (Throwable e) {
            toast("删除失败：" + e.getMessage());
        }
    }

    private void exportBackup(final String name) {
        new Thread(() -> {
            try {
                String dir = "/sdcard/Download/MCBDS";
                ShizukuShell.sh("mkdir -p " + dir + " && cp '"
                        + (DeployManager.BACKUPS_DIR + "/" + name).replace("'", "'\\''")
                        + "' " + dir + "/");
                AppLog.add("[备份] 导出 " + name);
                handler.post(() -> toast("已导出到 Download/MCBDS/" + name));
            } catch (Throwable e) {
                handler.post(() -> toast("导出失败：" + e.getMessage()));
            }
        }, "export-backup").start();
    }

    private static String kindLabel(String kind) {
        if ("players".equals(kind)) return "玩家数据";
        if ("world".equals(kind)) return "存档";
        if ("plugins".equals(kind)) return "旧插件备份";
        if ("addons".equals(kind)) return "Addons";
        if ("server".equals(kind)) return "服务端";
        return kind;
    }

    private String readMeta(String name) {
        try {
            String raw = ShizukuShell.read(DeployManager.BACKUPS_DIR + "/" + name + ".json");
            JSONObject o = new JSONObject(raw);
            long ts = o.optLong("createdAt", 0);
            String time = ts <= 0 ? "时间未知" : new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(new java.util.Date(ts));
            return "类型：" + kindLabel(o.optString("kind", "server"))
                    + " · 实例：" + o.optString("instanceId", "未绑定")
                    + " · 版本：" + o.optString("bdsVersion", "未知")

                    + " · " + time;
        } catch (Throwable t) {
            return "旧备份，无实例元数据";
        }
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return MinecraftUi.dp(activity, v);
    }

    private void doBackup() {
        if (busy) return;
        if (ServerManager.get().isRunning()) {
            toast("请先停止服务端再备份");
            return;
        }
        busy = true;
        status.setText("正在备份…");
        new Thread(() -> {
            try {
                DeployManager.backup(activity, DeployManager.TYPE_SERVER,
                        (percent, message) -> handler.post(() -> status.setText(message)));
                handler.post(() -> {
                    busy = false;
                    status.setText("备份完成");
                    refreshList();
                });
            } catch (Throwable e) {
                handler.post(() -> {
                    busy = false;
                    status.setText("备份失败：" + e.getMessage());
                });
            }
        }, "server-backup").start();
    }

    private void refreshList() {
        new Thread(() -> {
            LinearLayout list = new LinearLayout(activity);
            list.setOrientation(LinearLayout.VERTICAL);
            try {
                List<String> backups = DeployManager.listAllBackups();
                if (backups.isEmpty()) {
                    list.addView(MinecraftUi.text(activity, "还没有服务端备份。",
                            9, MinecraftUi.PANEL_MUTED, false));
                } else {
                    for (String name : backups) {
                        if (!"all".equals(filter) && !name.startsWith(filter + "-")) continue;
                        LinearLayout row = MinecraftUi.slot(activity);
                        row.setOrientation(LinearLayout.VERTICAL);
                        row.setPadding(dp(9), dp(7), dp(9), dp(7));
                        TextView title = MinecraftUi.text(activity, name, 8.5f,
                                MinecraftUi.PANEL_TEXT, true);
                        row.addView(title);
                        TextView meta = MinecraftUi.text(activity, readMeta(name), 8,
                                MinecraftUi.PANEL_MUTED, false);
                        row.addView(meta);
                        boolean canRestore = name.startsWith("server-") || name.startsWith("world-")
                                || name.startsWith("plugins-") || name.startsWith("addons-")
                                || name.startsWith("players-");
                        LinearLayout actions = new LinearLayout(activity);
                        actions.setOrientation(LinearLayout.VERTICAL);
                        boolean isPlayerBackup = name.startsWith("players-");
                        if (isPlayerBackup) {
                            LinearLayout rowA = new LinearLayout(activity);
                            rowA.setOrientation(LinearLayout.HORIZONTAL);
                            Button restore = new MinecraftUi.McButton(activity, "回滚");
                            rowA.addView(restore, new LinearLayout.LayoutParams(0, dp(38), 1f));
                            Button imp = new MinecraftUi.McButton(activity, "导入");
                            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(38), 1f);
                            ilp.leftMargin = dp(4);
                            rowA.addView(imp, ilp);
                            restore.setOnClickListener(v -> rollbackPlayer(name));
                            imp.setOnClickListener(v -> showImportPlayer(name));
                            actions.addView(rowA, new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
                            LinearLayout rowB = new LinearLayout(activity);
                            rowB.setOrientation(LinearLayout.HORIZONTAL);
                            Button del = new MinecraftUi.McButton(activity, "删除");
                            if (del instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) del).setDangerStyle();
                            del.setTextColor(0xFFFFF2F2);
                            rowB.addView(del, new LinearLayout.LayoutParams(0, dp(38), 1f));
                            Button export = new MinecraftUi.McButton(activity, "导出");
                            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, dp(38), 1f);
                            elp.leftMargin = dp(4);
                            rowB.addView(export, elp);
                            del.setOnClickListener(v -> confirmDelete(name));
                            export.setOnClickListener(v -> exportBackup(name));
                            LinearLayout.LayoutParams rbp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
                            rbp.topMargin = dp(4);
                            actions.addView(rowB, rbp);
                            LinearLayout.LayoutParams alp2 = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            alp2.topMargin = dp(5);
                            row.addView(actions, alp2);
                        } else {
                            actions.setOrientation(LinearLayout.HORIZONTAL);
                            Button restore = new MinecraftUi.McButton(activity, "回滚");
                            restore.setEnabled(canRestore);
                            restore.setAlpha(canRestore ? 1f : 0.55f);
                            actions.addView(restore, new LinearLayout.LayoutParams(0, dp(38), 1f));
                            restore.setOnClickListener(v -> confirmRestore(name));
                            Button del = new MinecraftUi.McButton(activity, "删除");
                            if (del instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) del).setDangerStyle();
                            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(38), 1f);
                            dlp.leftMargin = dp(4);
                            actions.addView(del, dlp);
                            del.setOnClickListener(v -> confirmDelete(name));
                            Button export = new MinecraftUi.McButton(activity, "导出");
                            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, dp(38), 1f);
                            elp.leftMargin = dp(4);
                            actions.addView(export, elp);
                            export.setOnClickListener(v -> exportBackup(name));
                            LinearLayout.LayoutParams alp2 = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
                            alp2.topMargin = dp(5);
                            row.addView(actions, alp2);
                        }
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.topMargin = dp(5);
                        list.addView(row, lp);
                    }
                }
            } catch (Throwable e) {
                list.addView(MinecraftUi.text(activity, "读取备份失败：" + e.getMessage(),
                        9, MinecraftUi.RED, false));
            }
            handler.post(() -> {
                while (col.getChildCount() > 1) col.removeViewAt(1);
                LinearLayout panel = MinecraftUi.panel(activity, "备份记录");
                panel.addView(list);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(8);
                col.addView(panel, lp);
            });
        }, "backup-list").start();
    }
}
