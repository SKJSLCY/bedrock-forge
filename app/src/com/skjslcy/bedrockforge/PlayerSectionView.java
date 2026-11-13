package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.graphics.drawable.ColorDrawable;
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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 玩家管理：展示进服统计，并提供权限/模式/白名单/等级命令入口。 */
public class PlayerSectionView extends ScrollView {

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout col;
    private final TextView status;
    private LinearLayout whitelistPanel;

    public PlayerSectionView(Activity activity) {
        super(activity);
        this.activity = activity;
        setFillViewport(false);
        col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(8));
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = MinecraftUi.panel(activity, "玩家管理");
        head.addView(MinecraftUi.text(activity,
                "从服务端启动后开始记录。总在线时间按实际连接时长累计。",
                9, MinecraftUi.PANEL_MUTED, false));
        status = MinecraftUi.text(activity, "正在读取…", 8.5f, MinecraftUi.PANEL_MUTED, false);
        status.setPadding(0, dp(6), 0, 0);
        head.addView(status);
        Button refresh = new MinecraftUi.McButton(activity, "刷新玩家列表");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        rlp.topMargin = dp(8);
        head.addView(refresh, rlp);
        Button backfill = new MinecraftUi.McButton(activity, "从当前控制台历史补录");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        blp.topMargin = dp(5);
        head.addView(backfill, blp);
        backfill.setOnClickListener(v -> {
            PlayerTracker.backfill(ServerManager.get().getLogs());
            toast("已从当前控制台日志补录");
            refresh();
        });
        Button logBackfill = new MinecraftUi.McButton(activity, "从日志文件补录");
        LinearLayout.LayoutParams lblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lblp.topMargin = dp(5);
        head.addView(logBackfill, lblp);
        final TextView logState = MinecraftUi.text(activity, "日志：读取中…",
                8f, MinecraftUi.PANEL_MUTED, false);
        logState.setPadding(0, dp(5), 0, 0);
        head.addView(logState);
        logBackfill.setOnClickListener(v -> {
            logState.setText("日志：正在补录…");
            new Thread(() -> {
                final ServerLogTail.Result r = ServerLogTail.tail(activity);
                handler.post(() -> {
                    if (r.error != null) {
                        logState.setText("日志：" + r.error);
                        toast("日志补录失败：" + r.error);
                    } else {
                        logState.setText("日志：" + r.file + " · 已读 "
                                + r.newOffset + " / " + r.fileSize + " B");
                        toast("日志补录完成：进服 " + r.joins + " 次，离服 " + r.leaves + " 次");
                    }
                    refresh();
                });
            }, "log-backfill").start();
        });
        new Thread(() -> {
            final String d = ServerLogTail.describe(activity);
            handler.post(() -> logState.setText("日志：" + d));
        }, "log-state").start();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!isAttachedToWindow()) return;
                refresh();
                handler.postDelayed(this, 3000);
            }
        }, 3000);
        refresh.setOnClickListener(v -> refresh());
        col.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new Thread(() -> {
            final boolean allow = isAllowListEnabled();
            handler.post(() -> {
                if (!allow) return;
                whitelistPanel = buildWhitelistPanel();
                col.addView(whitelistPanel, 1, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            });
        }, "allowlist-check").start();
        refresh();
    }

    private LinearLayout buildWhitelistPanel() {
        LinearLayout panel = MinecraftUi.panel(activity, "添加白名单");
        panel.addView(MinecraftUi.text(activity,
                "白名单拦截的玩家不会出现在列表里，可在这里按名字直接添加。",
                8.5f, MinecraftUi.PANEL_MUTED, false));
        final EditText input = MinecraftUi.terminalInput(activity, "玩家名，例如 Steve");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        ilp.topMargin = dp(6);
        panel.addView(input, ilp);
        Button add = new MinecraftUi.McButton(activity, "添加");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        alp.topMargin = dp(5);
        panel.addView(add, alp);
        add.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.matches("[A-Za-z0-9_]{1,32}")) {
                toast("请输入正确的玩家名（字母、数字、下划线）");
                return;
            }
            if (command("allowlist add \"" + name + "\"")) {
                input.setText("");
                toast("已发送白名单添加：" + name);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        panel.setLayoutParams(lp);
        return panel;
    }

    private void refresh() {
        new Thread(() -> {
            List<PlayerTracker.Player> list = PlayerTracker.list();
            handler.post(() -> render(list));
        }, "players-load").start();
    }

    private void render(List<PlayerTracker.Player> list) {
        for (int i = col.getChildCount() - 1; i >= 1; i--) {
            if (col.getChildAt(i) == whitelistPanel) continue;
            col.removeViewAt(i);
        }
        status.setText("共 " + list.size() + " 名玩家");
        LinearLayout panel = MinecraftUi.panel(activity, "玩家列表");
        if (list.isEmpty()) {
            panel.addView(MinecraftUi.text(activity,
                    "还没有玩家记录。启动服务端并有玩家进服后会自动记录。",
                    9, MinecraftUi.PANEL_MUTED, false));
        }
        for (PlayerTracker.Player p : list) {
            LinearLayout row = MinecraftUi.slot(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(9), dp(7), dp(9), dp(7));
            boolean allowOn = isAllowListEnabled();
            String base = p.name + (p.online ? "  ● 在线" : "  ○ 离线");
            String wlText = "";
            int wlColor = 0;
            if (allowOn) {
                boolean wl = isWhitelisted(p.name);
                wlText = wl ? "  白名单" : "  非白名单";
                wlColor = wl ? 0xFF7FD67F : 0xFFFF6B6B;
            }
            SpannableString ss = new SpannableString(base + wlText);
            if (allowOn && !wlText.isEmpty()) {
                ss.setSpan(new ForegroundColorSpan(wlColor), base.length(), ss.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            TextView name = MinecraftUi.text(activity, "", 10,
                    p.online ? MinecraftUi.GREEN : MinecraftUi.PANEL_TEXT, true);
            name.setText(ss);
            row.addView(name);
            String info = "XUID：" + (p.xuid == null || p.xuid.isEmpty() ? "未知" : p.xuid) + "\n"
                    + "首次：" + time(p.firstSeen) + "\n"
                    + "上次：" + time(p.lastSeen) + "（" + PlayerTracker.ago(p.lastSeen) + "）" + "\n"
                    + "本次在线：" + dur(PlayerTracker.sessionMs(p))
                    + "　总在线：" + dur(p.totalMs);
            row.addView(MinecraftUi.text(activity, info, 8.5f, MinecraftUi.PANEL_MUTED, false));
            Button manage = new MinecraftUi.McButton(activity, "管理该玩家");
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            mlp.topMargin = dp(5);
            row.addView(manage, mlp);
            manage.setOnClickListener(v -> manage(p));
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
    private static final String[][] INPUT_PERMS = {
            {"摄像机视角", "camera"},
            {"允许骑乘", "mount"},
            {"解除骑乘", "dismount"},
            {"跳跃", "jump"},
            {"潜行", "sneak"},
            {"四向移动", "lateral_movement"},
            {"向前移动", "move_forward"},
            {"向后移动", "move_backward"},
            {"向左移动", "move_left"},
            {"向右移动", "move_right"},
            {"自主移动", "movement"},
    };

    private void showPermissions(final PlayerTracker.Player p, final Runnable back) {
        final String version;
        try {
            version = DeployManager.detect(activity, DeployManager.TYPE_SERVER).version;
        } catch (Throwable t) {
            toast("读取服务端版本失败");
            return;
        }
        final boolean supportsInput = compareVersion(version, "1.21.50") >= 0;

        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        ScrollView sv = new ScrollView(activity);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "权限 · " + p.name);
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSwitchRow(panel, p, "管理员权限", "op", "deop", true);

        if (supportsInput) {
            panel.addView(MinecraftUi.text(activity, "输入权限（BDS " + version + "）",
                    8.5f, MinecraftUi.PANEL_MUTED, false));
            for (String[] row : INPUT_PERMS) {
                addSwitchRow(panel, p, row[0], row[1], null, false);
            }
        } else {
            panel.addView(MinecraftUi.text(activity,
                    "当前服务端版本 " + version + " 低于 1.21.50，不显示输入权限。",
                    8.5f, MinecraftUi.PANEL_MUTED, false));
        }

        sv.addView(wrap);
        LinearLayout rootOuter = new LinearLayout(activity);
        rootOuter.setOrientation(LinearLayout.VERTICAL);
        rootOuter.setPadding(dp(14), dp(14), dp(14), dp(14));
        rootOuter.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button close = new MinecraftUi.McButton(activity, "返回");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        clp.topMargin = dp(8);
        rootOuter.addView(close, clp);
        close.setOnClickListener(v -> {
            d.dismiss();
            if (back != null) back.run();
        });
        d.setContentView(rootOuter);
        d.show();
        Window dw = d.getWindow();
        if (dw != null) {
            dw.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.8f));
        }
    }

    private void addSwitchRow(LinearLayout panel, PlayerTracker.Player p, String label,
                              final String onKey, final String offKey, boolean opPair) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        rlp.topMargin = dp(4);
        TextView t = MinecraftUi.text(activity, label, 9.5f, MinecraftUi.PANEL_TEXT, false);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean initial;
        if (opPair) {
            initial = isOperator(p);
        } else {
            String stored = prefs().getString("perm_" + p.name + "_" + onKey, null);
            initial = stored == null || "1".equals(stored);
        }
        final MinecraftUi.McSwitch sw = new MinecraftUi.McSwitch(activity);
        sw.setChecked(initial);
        row.addView(sw, new LinearLayout.LayoutParams(dp(66), dp(34)));
        sw.setOnCheckedChangeListener((view, checked) -> {
            String cmd = opPair
                    ? (checked ? "op \"" + p.name + "\"" : "deop \"" + p.name + "\"")
                    : "inputpermission set \"" + p.name + "\" " + onKey
                        + (checked ? " enabled" : " disabled");
            if (command(cmd)) {
                prefs().edit().putString("perm_" + p.name + "_" + (opPair ? "op" : onKey),
                        checked ? "1" : "0").apply();
            } else {
                sw.setChecked(!checked);
            }
        });
        panel.addView(row, rlp);
    }

    /** 从 permissions.json / allowlist.json 读取该玩家是否为管理员。 */
    private boolean isOperator(PlayerTracker.Player p) {
        String xuid = resolveXuid(p);
        if (xuid.isEmpty()) return false;
        try {
            String raw = ShizukuShell.read(DeployManager.SERVER_DIR + "/permissions.json");
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (!"operator".equalsIgnoreCase(o.optString("permission"))) continue;
                if (xuid.equalsIgnoreCase(o.optString("xuid"))) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 优先用玩家记录里的 XUID，其次从 allowlist.json 按名字匹配。 */
    private String resolveXuid(PlayerTracker.Player p) {
        if (p.xuid != null && !p.xuid.trim().isEmpty()) return p.xuid.trim();
        try {
            String raw = ShizukuShell.read(DeployManager.SERVER_DIR + "/allowlist.json");
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (p.name.equalsIgnoreCase(o.optString("name"))) {
                    String x = o.optString("xuid", "");
                    if (!x.isEmpty()) return x;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private android.content.SharedPreferences prefs() {
        return activity.getSharedPreferences("mcbds", android.content.Context.MODE_PRIVATE);
    }

    private boolean isAllowListEnabled() {
        try {
            String props = ShizukuShell.read(DeployManager.SERVER_DIR + "/server.properties");
            for (String line : props.split("\n")) {
                String t = line.trim();
                if (t.startsWith("allow-list=")) {
                    return "true".equalsIgnoreCase(t.substring("allow-list=".length()).trim());
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isWhitelisted(String name) {
        try {
            String raw = ShizukuShell.read(DeployManager.SERVER_DIR + "/allowlist.json");
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (name.equalsIgnoreCase(o.optString("name"))) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isBanned(String name) {
        try {
            String raw = ShizukuShell.read(DeployManager.SERVER_DIR + "/banned-players.json");
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (name.equalsIgnoreCase(o.optString("name"))) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void confirm(String title, String message, final Runnable ok) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, title);
        panel.addView(MinecraftUi.text(activity, message, 9.5f, MinecraftUi.PANEL_TEXT, false));
        Button yes = new MinecraftUi.McButton(activity, "确定");
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
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private int compareVersion(String a, String b) {
        String[] x = a == null ? new String[0] : a.split("\\.");
        String[] y = b == null ? new String[0] : b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? toInt(x[i]) : 0;
            int yi = i < y.length ? toInt(y[i]) : 0;
            if (xi != yi) return xi < yi ? -1 : 1;
        }
        return 0;
    }

    private int toInt(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Throwable t) { return 0; }
    }

    private void manage(final PlayerTracker.Player p) {
        showManageDialog(p);
    }

    /** 玩家操作主面板；子界面“返回”会重新打开它。 */
    private void showManageDialog(final PlayerTracker.Player p) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "玩家 · " + p.name);
        panel.addView(MinecraftUi.text(activity,
                "XUID：" + (p.xuid == null || p.xuid.isEmpty() ? "未知" : p.xuid)
                        + "\n首次：" + time(p.firstSeen) + "\n上次：" + time(p.lastSeen) + "（" + PlayerTracker.ago(p.lastSeen) + "）"
                        + "\n本次在线：" + dur(PlayerTracker.sessionMs(p))
                        + "　总在线：" + dur(p.totalMs),
                8.5f, MinecraftUi.PANEL_MUTED, false));

        final Runnable back = () -> showManageDialog(p);

        Button perm = new MinecraftUi.McButton(activity, "权限设置");
        panel.addView(perm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        perm.setOnClickListener(v -> {
            d.dismiss();
            showPermissions(p, back);
        });

        Button data = new MinecraftUi.McButton(activity, "查看玩家数据");
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        dlp.topMargin = dp(5);
        panel.addView(data, dlp);
        data.setOnClickListener(v -> {
            d.dismiss();
            PlayerDataView.show(activity, p, back);
        });

        Button mode = new MinecraftUi.McButton(activity, "修改游戏模式");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        mlp.topMargin = dp(5);
        panel.addView(mode, mlp);
        mode.setOnClickListener(v -> {
            d.dismiss();
            showGameModeDialog(p, back);
        });

        if (isAllowListEnabled()) {
            final boolean whitelisted = isWhitelisted(p.name);
            Button white = new MinecraftUi.McButton(activity,
                    whitelisted ? "撤销白名单" : "恢复白名单");
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            wlp.topMargin = dp(5);
            panel.addView(white, wlp);
            white.setOnClickListener(v -> {
                d.dismiss();
                command("allowlist " + (whitelisted ? "remove" : "add") + " \"" + p.name + "\"");
                back.run();
            });
        }

        Button xp = new MinecraftUi.McButton(activity, "设置等级");
        LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        xlp.topMargin = dp(5);
        panel.addView(xp, xlp);
        xp.setOnClickListener(v -> {
            d.dismiss();
            showLevelDialog(p, back);
        });

        Button kick = new MinecraftUi.McButton(activity, "踢出玩家");
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        klp.topMargin = dp(5);
        panel.addView(kick, klp);
        kick.setOnClickListener(v -> {
            d.dismiss();
            command("kick \"" + p.name + "\"");
            back.run();
        });

        Button close = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(6);
        panel.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** 游戏模式选择，MC 风格，带返回。 */
    private void showGameModeDialog(final PlayerTracker.Player p, final Runnable back) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "游戏模式 · " + p.name);
        String[] modes = {"survival 生存", "creative 创造", "adventure 冒险", "spectator 旁观"};
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i].split(" ")[0];
            Button b = new MinecraftUi.McButton(activity, modes[i]);
            b.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            lp.topMargin = i == 0 ? 0 : dp(5);
            panel.addView(b, lp);
            b.setOnClickListener(v -> {
                d.dismiss();
                command("gamemode " + mode + " \"" + p.name + "\"");
                back.run();
            });
        }
        Button cancel = new MinecraftUi.McButton(activity, "返回");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(6);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> {
            d.dismiss();
            back.run();
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** 设置等级，MC 风格输入框，带返回。 */
    private void showLevelDialog(final PlayerTracker.Player p, final Runnable back) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout panel = MinecraftUi.panel(activity, "设置等级 · " + p.name);
        final EditText input = MinecraftUi.terminalInput(activity, "输入等级，例如 30");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        ilp.topMargin = dp(6);
        panel.addView(input, ilp);
        Button ok = new MinecraftUi.McButton(activity, "设置");
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        olp.topMargin = dp(6);
        panel.addView(ok, olp);
        ok.setOnClickListener(v -> {
            String n = input.getText().toString().trim();
            if (!n.matches("\\d{1,5}")) {
                toast("请输入数字等级");
                return;
            }
            d.dismiss();
            command("xp " + n + "L \"" + p.name + "\"");
            back.run();
        });
        Button cancel = new MinecraftUi.McButton(activity, "返回");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> {
            d.dismiss();
            back.run();
        });
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    private interface Pick {
        void onPick(String label);
    }

    private void choose(String title, String[] items, final Pick pick) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(items, (d, which) -> pick.onPick(items[which]))
                .show();
    }

    private boolean command(String cmd) {
        boolean ok = ServerManager.get().send(cmd);
        toast(ok ? "已发送：" + cmd : "服务端未运行，发送失败");
        return ok;
    }

    private String time(long ms) {
        if (ms <= 0) return "未知";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date(ms));
    }

    private String dur(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return h + "小时" + m + "分钟";
        if (m > 0) return m + "分钟" + sec + "秒";
        return sec + "秒";
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return MinecraftUi.dp(activity, v);
    }
}
