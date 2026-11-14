package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 存档管理：列出 worlds、切换 level-name、新建和导入存档。 */
public class WorldSectionView extends ScrollView {

    private final Activity activity;
    private final DeploySectionView.Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout col;
    private final TextView status;
    private boolean busy;

    public WorldSectionView(Activity activity, DeploySectionView.Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        setFillViewport(false);
        col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(8));
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = MinecraftUi.panel(activity, "存档管理");
        head.addView(MinecraftUi.text(activity,
                I18n.t("只有停止服务端后才能切换或新建存档。新建的世界会在下次启动时生成。"),
                9, MinecraftUi.PANEL_MUTED, false));
        status = MinecraftUi.text(activity, "正在读取…", 8.5f, MinecraftUi.PANEL_MUTED, false);
        status.setPadding(0, dp(6), 0, 0);
        head.addView(status);
        Button create = new MinecraftUi.McButton(activity, "新建世界");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(8);
        head.addView(create, clp);
        create.setOnClickListener(v -> newWorld());
        Button imp = new MinecraftUi.McButton(activity, "导入存档 (.zip / .mcworld)");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        ilp.topMargin = dp(5);
        head.addView(imp, ilp);
        imp.setOnClickListener(v -> importWorld());
        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        refresh();
    }

    private void refresh() {
        new Thread(() -> {
            List<String> worlds = new ArrayList<>();
            String active = "";
            String error = null;
            try {
                String out = ShizukuShell.sh("for d in " + q(DeployManager.SERVER_DIR + "/worlds")
                        + "/*; do [ -d \"$d\" ] && basename \"$d\"; done 2>/dev/null || true").trim();
                for (String s : out.split("\n")) if (!s.trim().isEmpty()) worlds.add(s.trim());
                String props = ShizukuShell.read(DeployManager.SERVER_DIR + "/server.properties");
                for (String line : props.split("\n")) {
                    if (line.trim().startsWith("level-name=")) {
                        active = line.trim().substring("level-name=".length());
                    }
                }
            } catch (Throwable t) {
                error = t.getMessage();
            }
            final List<String> list = worlds;
            final String act = active;
            final String err = error;
            handler.post(() -> render(list, act, err));
        }, "worlds-load").start();
    }

    private void render(List<String> worlds, String active, String error) {
        while (col.getChildCount() > 1) col.removeViewAt(1);
        if (error != null) status.setText(I18n.t("读取失败：") + error);
        else status.setText(I18n.t("当前存档：") + (active.isEmpty() ? I18n.t("未设置") : active));

        LinearLayout panel = MinecraftUi.panel(activity, "世界列表");
        if (worlds.isEmpty()) {
            panel.addView(MinecraftUi.text(activity, I18n.t("还没有世界。请新建或导入。"),
                    9, MinecraftUi.PANEL_MUTED, false));
        }
        for (String w : worlds) {
            LinearLayout row = MinecraftUi.slot(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(9), dp(7), dp(9), dp(7));
            TextView name = MinecraftUi.text(activity, w,
                    10, MinecraftUi.PANEL_TEXT, true);
            name.setSelected(w.equals(active));
            row.addView(name);
            row.addView(MinecraftUi.text(activity, meta(w), 8.5f,
                    MinecraftUi.PANEL_MUTED, false));
            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            if (!w.equals(active)) {
                Button sw = new MinecraftUi.McButton(activity, "切换");
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(36), 1f);
                slp.leftMargin = dp(4);
                actions.addView(sw, slp);
                sw.setOnClickListener(v -> switchWorld(w));
            }
            Button exp = new MinecraftUi.McButton(activity, "导出");
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            elp.leftMargin = dp(4);
            actions.addView(exp, elp);
            exp.setOnClickListener(v -> exportWorld(w));
            Button ren = new MinecraftUi.McButton(activity, "重命名");
            LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(0, dp(36), 1f);
            rlp2.leftMargin = dp(4);
            actions.addView(ren, rlp2);
            ren.setOnClickListener(v -> renameWorld(w));
            Button del = new MinecraftUi.McButton(activity, "删除");
            if (del instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) del).setDangerStyle();
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            dlp.leftMargin = dp(4);
            actions.addView(del, dlp);
            del.setOnClickListener(v -> deleteWorld(w, active));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = dp(5);
            row.addView(actions, alp);
            if (!w.equals(active)) {
                // 切换按钮已经放在操作行里
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(5);
            panel.addView(row, lp);
        }
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        col.addView(panel, plp);
    }

    private String meta(String name) {
        try {
            String size = ShizukuShell.sh("du -sk " + q(DeployManager.SERVER_DIR + "/worlds/" + name)
                    + " 2>/dev/null | awk '{print $1}'").trim();
            String ts = ShizukuShell.sh("stat -c %Y " + q(DeployManager.SERVER_DIR + "/worlds/" + name)
                    + " 2>/dev/null || true").trim();
            long kb = 0;
            try { kb = Long.parseLong(size); } catch (Throwable ignored) {}
            String t = I18n.t("时间未知");
            try {
                long sec = Long.parseLong(ts);
                t = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                        .format(new java.util.Date(sec * 1000));
            } catch (Throwable ignored) {}
            return I18n.t("大小：") + (kb >= 1024 ? (kb / 1024) + " MB" : kb + " KB") + I18n.t(" · 修改：") + t;
        } catch (Throwable t) {
            return I18n.t("大小未知");
        }
    }

    private void loadIcon(String name, ImageView view) {
        new Thread(() -> {
            try {
                String[] candidates = {"world_icon.jpeg", "world_icon.jpg", "world_icon.png"};
                for (String f : candidates) {
                    String src = DeployManager.SERVER_DIR + "/worlds/" + name + "/" + f;
                    if (!ShizukuShell.sh("test -f " + q(src) + " && echo 1 || echo 0").trim().equals("1")) continue;
                    File out = new File(activity.getCacheDir(), "icon_" + Math.abs(name.hashCode()) + ".img");
                    ShizukuShell.copyOut(src, out, null);
                    final android.graphics.Bitmap bmp = BitmapFactory.decodeFile(out.getAbsolutePath());
                    if (bmp != null) {
                        handler.post(() -> view.setImageBitmap(bmp));
                    }
                    return;
                }
            } catch (Throwable ignored) {
            }
        }, "world-icon").start();
    }

    private void exportWorld(final String name) {
        new Thread(() -> {
            try {
                String tar = DeployManager.BASE + "/tmp/world-export.tar";
                ShizukuShell.sh("rm -f " + q(tar) + "; tar -cf " + q(tar) + " -C "
                        + q(DeployManager.SERVER_DIR + "/worlds/" + name) + " .");
                File localTar = new File(activity.getCacheDir(), "world-export.tar");
                ShizukuShell.copyOut(tar, localTar, null);
                File zip = new File(activity.getCacheDir(), name + ".mcworld");
                tarToZip(localTar, zip);
                ShizukuShell.sh("mkdir -p /sdcard/Download/MCBDS");
                ShizukuShell.copyIn(zip, "/sdcard/Download/MCBDS/" + name + ".mcworld", null);
                ShizukuShell.sh("rm -f " + q(tar));
                AppLog.add(I18n.t("[存档] 导出 ") + name);
                handler.post(() -> toast(I18n.t("已导出到 Download/MCBDS/") + name + ".mcworld"));
            } catch (Throwable e) {
                handler.post(() -> toast(I18n.t("导出失败：") + e.getMessage()));
            }
        }, "world-export").start();
    }

    private void tarToZip(File tar, File zip) throws Exception {
        try (FileInputStream in = new FileInputStream(tar);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            byte[] header = new byte[512];
            while (true) {
                int read = in.read(header);
                if (read < 512) break;
                boolean empty = true;
                for (byte b : header) if (b != 0) { empty = false; break; }
                if (empty) break;
                String name = new String(header, 0, 100, "UTF-8").replace("\0", "").trim();
                if (name.isEmpty()) break;
                long size = 0;
                try {
                    String s = new String(header, 124, 12, "UTF-8").replace("\0", "").trim();
                    size = Long.parseLong(s, 8);
                } catch (Throwable ignored) {}
                int type = header[156];
                if (name.startsWith("./")) name = name.substring(2);
                if (type == '5' || name.endsWith("/")) {
                    if (!name.isEmpty()) out.putNextEntry(new ZipEntry(name.endsWith("/") ? name : name + "/"));
                    out.closeEntry();
                } else {
                    if (!name.isEmpty()) {
                        out.putNextEntry(new ZipEntry(name));
                        long left = size;
                        byte[] buf = new byte[65536];
                        while (left > 0) {
                            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                            if (n <= 0) break;
                            out.write(buf, 0, n);
                            left -= n;
                        }
                        out.closeEntry();
                    }
                }
                long pad = (512 - (size % 512)) % 512;
                if (pad > 0) in.skip(pad);
            }
        }
    }

    private interface NameCallback { void onName(String name); }

    private void renameWorld(final String name) {
        if (!ensureStopped()) return;
        showInputDialog(I18n.t("重命名世界"), I18n.t("只支持字母、数字、下划线、横线。"), name, null, I18n.t("重命名"), nn -> {
            final String n2 = nn.trim();
            if (!n2.matches("[A-Za-z0-9_-]{1,32}")) {
                toast(I18n.t("名称不合法"));
                return;
            }
            if (n2.equals(name)) return;
            new Thread(() -> {
                try {
                    String base = DeployManager.SERVER_DIR + "/worlds/";
                    if (ShizukuShell.sh("test -e " + q(base + n2) + " && echo 1 || echo 0").trim().equals("1")) {
                        handler.post(() -> toast(I18n.t("目标世界已存在")));
                        return;
                    }
                    ShizukuShell.sh("mv " + q(base + name) + " " + q(base + n2));
                    String props = ShizukuShell.read(DeployManager.SERVER_DIR + "/server.properties");
                    boolean active = props.contains("level-name=" + name);
                    if (active) setLevelName(n2);
                    AppLog.add(I18n.t("[存档] 重命名 ") + name + " -> " + n2);
                    handler.post(() -> {
                        toast(I18n.t("已重命名为 ") + n2);
                        refresh();
                    });
                } catch (Throwable e) {
                    handler.post(() -> toast(I18n.t("重命名失败：") + e.getMessage()));
                }
            }, "world-rename").start();
        });
    }

    private void deleteWorld(final String name, String active) {
        if (!ensureStopped()) return;
        if (name.equals(active)) {
            toast(I18n.t("当前存档不能删除，请先切换到其他存档"));
            return;
        }
        showConfirmDialog(I18n.t("删除世界"), I18n.t("永久删除 world/") + name + "？", I18n.t("删除"), true, () ->
                new Thread(() -> {
                    try {
                        ShizukuShell.sh("rm -rf " + q(DeployManager.SERVER_DIR + "/worlds/" + name));
                        handler.post(() -> {
                            AppLog.add(I18n.t("[存档] 删除 ") + name);
                            toast(I18n.t("已删除 ") + name);
                            refresh();
                        });
                    } catch (Throwable e) {
                        handler.post(() -> toast(I18n.t("删除失败：") + e.getMessage()));
                    }
                }, "world-delete").start());
    }

    private void newWorld() {
        if (!ensureStopped()) return;
        showInputDialog(I18n.t("新建世界"), I18n.t("只支持字母、数字、下划线和横线。"), "", I18n.t("例如 World2"), I18n.t("创建"), name -> {
            final String n = name.trim();
            if (!n.matches("[A-Za-z0-9_-]{1,32}")) {
                toast(I18n.t("世界名只支持字母、数字、下划线、横线"));
                return;
            }
            createWorld(n);
        });
    }

    private void createWorld(final String name) {
        new Thread(() -> {
            try {
                ShizukuShell.sh("mkdir -p " + q(DeployManager.SERVER_DIR + "/worlds/" + name));
                setLevelName(name);
                handler.post(() -> {
                    AppLog.add(I18n.t("[存档] 新建 ") + name);
                toast(I18n.t("已创建世界 ") + name + I18n.t("，下次启动生成"));
                    refresh();
                });
            } catch (Throwable e) {
                handler.post(() -> toast(I18n.t("创建失败：") + e.getMessage()));
            }
        }, "world-create").start();
    }

    private void switchWorld(final String name) {
        if (!ensureStopped()) return;
        showConfirmDialog(I18n.t("切换存档"), I18n.t("下次启动使用世界 ") + name + "？", I18n.t("切换"), false, () ->
                new Thread(() -> {
                    try {
                        setLevelName(name);
                        handler.post(() -> {
                            AppLog.add(I18n.t("[存档] 切换到 ") + name);
                            toast(I18n.t("已切换到 ") + name);
                            refresh();
                        });
                    } catch (Throwable e) {
                        handler.post(() -> toast(I18n.t("切换失败：") + e.getMessage()));
                    }
                }, "world-switch").start());
    }

    private void showInputDialog(String title, String message, String initial, String hint,
                                 String positive, final NameCallback cb) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, title);
        TextView msg = MinecraftUi.text(activity, message, 9f, MinecraftUi.PANEL_MUTED, false);
        msg.setPadding(0, dp(2), 0, dp(8));
        panel.addView(msg);
        EditText input = new EditText(activity);
        MinecraftUi.styleInput(input);
        input.setSingleLine(true);
        if (initial != null) input.setText(initial);
        if (hint != null) input.setHint(hint);
        panel.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        Button ok = new MinecraftUi.McButton(activity, positive);
        if (MinecraftUi.ORE || MinecraftUi.MINIMAL) ((MinecraftUi.McButton) ok).setPrimaryStyle();
        panel.addView(ok, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(6);
        panel.addView(cancel, clp);
        ok.setOnClickListener(v -> {
            d.dismiss();
            cb.onName(input.getText().toString());
        });
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showConfirmDialog(String title, String message, String positive,
                                   final boolean danger, final Runnable action) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, title);
        TextView msg = MinecraftUi.text(activity, message, 9.5f, MinecraftUi.PANEL_TEXT, false);
        msg.setPadding(0, dp(2), 0, dp(10));
        panel.addView(msg);
        Button ok = new MinecraftUi.McButton(activity, positive);
        if (danger) ((MinecraftUi.McButton) ok).setDangerStyle();
        else if (MinecraftUi.ORE || MinecraftUi.MINIMAL) ((MinecraftUi.McButton) ok).setPrimaryStyle();
        panel.addView(ok, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(6);
        panel.addView(cancel, clp);
        ok.setOnClickListener(v -> { d.dismiss(); action.run(); });
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void importWorld() {
        if (!ensureStopped()) return;
        host.pickFile(new DeploySectionView.PickCallback() {
            @Override public void onPicked(File file) {
                String name = MainActivity.lastPickedName;
                if (name == null || name.isEmpty()) name = file.getName();
                int dot = name.lastIndexOf('.');
                if (dot > 0) name = name.substring(0, dot);
                name = name.replaceAll("^import_\\d+_", "");                name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (name.isEmpty()) name = "Imported World";
                if (name.length() > 32) name = name.substring(0, 32);
                final String world = name;
                String dest = DeployManager.SERVER_DIR + "/worlds/" + world;
                boolean exists = false;
                try {
                    exists = ShizukuShell.sh("test -e " + q(dest) + " && echo 1 || echo 0").trim().equals("1");
                } catch (Throwable ignored) {
                }
                if (exists) {
                    confirmImport(file, world);
                } else {
                    doImport(file, world);
                }
            }

            @Override public void onCancel() {
                toast(I18n.t("已取消导入"));
            }
        });
    }

    private void confirmImport(final File file, final String world) {
        final android.app.Dialog d = new android.app.Dialog(activity);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "覆盖存档");
        panel.addView(MinecraftUi.text(activity,
                I18n.t("世界 ") + world + I18n.t(" 已存在。\n继续导入会覆盖现有存档，是否继续？"),
                9.5f, MinecraftUi.PANEL_TEXT, false));
        Button ok = new MinecraftUi.McButton(activity, "覆盖导入");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(8);
        panel.addView(ok, lp);
        ok.setOnClickListener(v -> {
            d.dismiss();
            doImport(file, world);
        });
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

    private void doImport(final File file, final String world) {
        new Thread(() -> {
            try {
                String remote = "/data/local/tmp/mcbds/world-import.zip";
                ShizukuShell.copyIn(file, remote, null);
                String stage = DeployManager.BASE + "/tmp/world-import";
                ShizukuShell.sh("rm -rf " + q(stage) + "; mkdir -p " + q(stage)
                        + "; unzip -q -o " + q(remote) + " -d " + q(stage));
                String find = ShizukuShell.sh("find " + q(stage) + " -name level.dat -type f | head -n 1").trim();
                if (find.isEmpty()) throw new Exception(I18n.t("压缩包里没有 level.dat"));
                String root = find.substring(0, find.lastIndexOf('/'));
                String dest = DeployManager.SERVER_DIR + "/worlds/" + world;
                ShizukuShell.sh("rm -rf " + q(dest) + "; mkdir -p " + q(dest)
                        + "; cp -a " + q(root) + "/. " + q(dest) + "/");
                ShizukuShell.sh("rm -f " + q(remote) + "; rm -rf " + q(stage));
                AppLog.add(I18n.t("[存档] 导入 ") + world);
                handler.post(() -> {
                    toast(I18n.t("已导入存档 ") + world + I18n.t("，可在列表里切换"));
                    refresh();
                });
            } catch (Throwable e) {
                handler.post(() -> toast(I18n.t("导入失败：") + e.getMessage()));
            }
        }, "world-import").start();
    }

    private void setLevelName(String name) throws Exception {
        String path = DeployManager.SERVER_DIR + "/server.properties";
        String props = ShizukuShell.read(path);
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (String line : props.split("\n")) {
            if (line.trim().startsWith("level-name=")) {
                sb.append("level-name=").append(name).append('\n');
                found = true;
            } else {
                sb.append(line).append('\n');
            }
        }
        if (!found) sb.append("level-name=").append(name).append('\n');
        ShizukuShell.write(path, sb.toString());
    }

    private boolean ensureStopped() {
        if (ServerManager.get().isRunning()) {
            toast(I18n.t("请先停止服务端"));
            return false;
        }
        return true;
    }

    private String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return MinecraftUi.dp(activity, v);
    }
}
