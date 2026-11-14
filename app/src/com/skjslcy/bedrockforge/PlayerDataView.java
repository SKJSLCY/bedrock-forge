package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

/** 离线查看玩家在存档中的数据（卡片式中文展示）。 */
final class PlayerDataView {

    private PlayerDataView() {
    }

    static void show(final Activity activity, final PlayerTracker.Player p, final Runnable back) {
        if (ServerManager.get().isRunning()) {
            Toast.makeText(activity, I18n.t("请先停止服务端再查看存档数据"), Toast.LENGTH_SHORT).show();
            return;
        }
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));

        final LinearLayout panel = MinecraftUi.panel(activity, I18n.t("玩家数据 · ") + p.name);
        panel.addView(MinecraftUi.text(activity, I18n.t("正在读取世界存档…"),
                9.5f, MinecraftUi.PANEL_MUTED, false));
        content.addView(panel);

        final ScrollView sv = new ScrollView(activity);
        sv.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final Button editBottom = new MinecraftUi.McButton(activity, "编辑数据");
        if (editBottom instanceof MinecraftUi.McButton) {
            ((MinecraftUi.McButton) editBottom).setDangerStyle();
        }
        editBottom.setTextColor(0xFFFFF2F2);
        editBottom.setVisibility(android.view.View.GONE);

        final Button backupBtn = new MinecraftUi.McButton(activity, "备份数据");
        backupBtn.setVisibility(android.view.View.GONE);

        final Button deleteBtn = new MinecraftUi.McButton(activity, "删除数据");
        if (deleteBtn instanceof MinecraftUi.McButton) {
            ((MinecraftUi.McButton) deleteBtn).setDangerStyle();
        }
        deleteBtn.setTextColor(0xFFFFF2F2);
        deleteBtn.setVisibility(android.view.View.GONE);

        Button close = new MinecraftUi.McButton(activity, "返回");
        close.setOnClickListener(v -> {
            dialog.dismiss();
            if (back != null) back.run();
        });

        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(activity, 12), dp(activity, 2), dp(activity, 12), dp(activity, 2));

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(editBottom, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
        b1.leftMargin = dp(activity, 8);
        row1.addView(backupBtn, b1);
        LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r1.topMargin = dp(activity, 6);
        grid.addView(row1, r1);

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(deleteBtn, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
        c1.leftMargin = dp(activity, 8);
        row2.addView(close, c1);
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2.topMargin = dp(activity, 6);
        grid.addView(row2, r2);

        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(activity, 6);
        root.addView(grid, glp);

        dialog.setContentView(root);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.82f));
        }

        new Thread(() -> {
            final OfflinePlayerData.Snapshot s = OfflinePlayerData.load(activity);
            final String bound = PlayerMapping.msaForName(activity, p.name);
            activity.runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                if (bound != null && !bound.isEmpty() && s.error == null) {
                    for (OfflinePlayerData.Record r : s.records) {
                        if (bound.equalsIgnoreCase(r.msaId)) {
                            panel.removeAllViews();
                            showRecord(activity, panel, s, r, false, editBottom, backupBtn, deleteBtn);
                            return;
                        }
                    }
                }
                if (s.error == null && s.records.size() == 1) {
                    panel.removeAllViews();
                    showRecord(activity, panel, s, s.records.get(0), false, editBottom, backupBtn, deleteBtn);
                    return;
                }
                render(activity, panel, s, editBottom, backupBtn, deleteBtn);
            });
        }, "offline-player").start();
    }

    private static void render(final Activity activity, LinearLayout panel,
                               OfflinePlayerData.Snapshot s, Button editBottom,
                               Button backupBtn, Button deleteBtn) {
        editBottom.setVisibility(android.view.View.GONE);
        backupBtn.setVisibility(android.view.View.GONE);
        deleteBtn.setVisibility(android.view.View.GONE);
        panel.removeAllViews();
        if (s.error != null) {
            panel.addView(MinecraftUi.text(activity, I18n.t("读取失败：") + s.error,
                    9.5f, MinecraftUi.RED, false));
            return;
        }
        panel.addView(MinecraftUi.text(activity,
                I18n.t("世界：") + s.world, 9f, MinecraftUi.PANEL_MUTED, false));
        panel.addView(MinecraftUi.text(activity,
                I18n.t("共 ") + s.records.size() + I18n.t(" 条玩家记录，存档只保存账号 ID，按记录查看。"),
                8.5f, MinecraftUi.PANEL_MUTED, false));
        for (final OfflinePlayerData.Record r : s.records) {
            Button b = new MinecraftUi.McButton(activity, r.label());
            b.setTextSize(10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
            lp.topMargin = dp(activity, 6);
            panel.addView(b, lp);
            b.setOnClickListener(v -> {
                panel.removeAllViews();
                showRecord(activity, panel, s, r, s.records.size() > 1, editBottom, backupBtn, deleteBtn);
            });
        }
    }

    private static void showRecord(final Activity activity, LinearLayout panel,
                                   OfflinePlayerData.Snapshot s, OfflinePlayerData.Record r,
                                   boolean fromList, Button editBottom,
                                   Button backupBtn, Button deleteBtn) {
        // 头部信息
        card(activity, panel, I18n.t("基本信息"), new OfflinePlayerData.Row[]{
                new OfflinePlayerData.Row("世界", s.world),
                new OfflinePlayerData.Row("账号", r.msaId.isEmpty() ? "未知" : r.msaId),
                new OfflinePlayerData.Row("ServerId", r.serverId)
        });

        if (!r.basic.isEmpty()) card(activity, panel, I18n.t("基础属性"), r.basic.toArray(new OfflinePlayerData.Row[0]));

        if (r.inventory.isEmpty()) {
            card(activity, panel, I18n.t("背包 / 装备"), new OfflinePlayerData.Row[]{
                    new OfflinePlayerData.Row("背包", "空")});
        } else {
            card(activity, panel, I18n.t("背包 / 装备"), r.inventory.toArray(new OfflinePlayerData.Row[0]));
        }

        if (r.ender.isEmpty()) {
            card(activity, panel, I18n.t("末影箱"), new OfflinePlayerData.Row[]{
                    new OfflinePlayerData.Row("末影箱", "空")});
        } else {
            card(activity, panel, I18n.t("末影箱"), r.ender.toArray(new OfflinePlayerData.Row[0]));
        }

        if (!r.effects.isEmpty()) {
            card(activity, panel, I18n.t("药水效果"), r.effects.toArray(new OfflinePlayerData.Row[0]));
        }

        for (String group : r.groups.keySet()) {
            List<OfflinePlayerData.Row> rows = r.groups.get(group);
            if (rows == null || rows.isEmpty()) continue;
            card(activity, panel, group, rows.toArray(new OfflinePlayerData.Row[0]));
        }

        boolean running = ServerManager.get().isRunning();
        editBottom.setText(running ? I18n.t("编辑数据（请先停止服务端）") : I18n.t("编辑数据"));
        editBottom.setEnabled(!running);
        editBottom.setAlpha(running ? 0.55f : 1f);
        editBottom.setVisibility(running ? android.view.View.GONE : android.view.View.VISIBLE);
        editBottom.setOnClickListener(v -> {
            if (ServerManager.get().isRunning()) {
                Toast.makeText(activity, I18n.t("请先停止服务端再编辑"), Toast.LENGTH_SHORT).show();
                return;
            }
            PlayerDataEditor.start(activity, r, () -> { });
        });

        backupBtn.setVisibility(android.view.View.VISIBLE);
        deleteBtn.setVisibility(android.view.View.VISIBLE);
        backupBtn.setOnClickListener(v -> {
            backupBtn.setEnabled(false);
            new Thread(() -> {
                try {
                    String ver = "unknown";
                    try {
                        ver = DeployManager.detect(activity, DeployManager.TYPE_SERVER).version;
                    } catch (Throwable ignored) {
                    }
                    final String path = OfflinePlayerData.backupPlayer(activity,
                            r.boundName == null ? "player" : r.boundName,
                            r.msaId, r.serverId, ver);
                    activity.runOnUiThread(() -> {
                        backupBtn.setEnabled(true);
                        Toast.makeText(activity, I18n.t("已备份到 ") + path, Toast.LENGTH_LONG).show();
                    });
                } catch (Throwable e) {
                    activity.runOnUiThread(() -> {
                        backupBtn.setEnabled(true);
                        Toast.makeText(activity, I18n.t("备份失败：") + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }, "player-backup").start();
        });
        deleteBtn.setOnClickListener(v -> confirmDelete(activity, s, r, deleteBtn, backupBtn, editBottom));

        Button more = new MinecraftUi.McButton(activity,
                r.unknown.isEmpty() ? "更多数据" : I18n.t("更多数据（") + r.unknown.size() + I18n.t(" 项未识别）"));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
        mlp.topMargin = dp(activity, 10);
        panel.addView(more, mlp);
        more.setOnClickListener(v -> showUnknown(activity, r.unknown, r.raw));

        if (fromList) {
            Button back = new MinecraftUi.McButton(activity, "返回记录列表");
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42));
            blp.topMargin = dp(activity, 6);
            panel.addView(back, blp);
            back.setOnClickListener(v -> render(activity, panel, s, editBottom, backupBtn, deleteBtn));
        }
    }

    /** 一张卡片：标题 + 若干“标签 / 值”行。 */
    private static void card(Activity activity, LinearLayout parent, String title,
                             OfflinePlayerData.Row[] rows) {
        LinearLayout box = MinecraftUi.panel(activity, title);
        for (int i = 0; i < rows.length; i++) {
            OfflinePlayerData.Row row = rows[i];
            if (row == null) continue;
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.TOP);
            line.setBackground(MinecraftUi.slotDrawable(activity));
            line.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));

            TextView label = MinecraftUi.text(activity, row.label, 8.5f,
                    MinecraftUi.PANEL_MUTED, false);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.36f);
            line.addView(label, llp);

            LinearLayout right = new LinearLayout(activity);
            right.setOrientation(LinearLayout.VERTICAL);
            TextView value = MinecraftUi.text(activity, row.value, 9.5f,
                    MinecraftUi.PANEL_TEXT, false);
            right.addView(value);
            if (row.sub != null && !row.sub.isEmpty()) {
                TextView sub = MinecraftUi.text(activity, row.sub, 8f,
                        MinecraftUi.PANEL_MUTED, false);
                sub.setPadding(0, dp(activity, 2), 0, 0);
                right.addView(sub);
            }
            line.addView(right, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.64f));
            if (row.item != null) {
                line.setClickable(true);
                line.setOnClickListener(v -> showItemDetail(activity, row.item));
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i == 0 ? 0 : dp(activity, 4);
            box.addView(line, lp);
        }
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(activity, 8);
        parent.addView(box, cardLp);
    }

    private static void confirmDelete(final Activity activity, OfflinePlayerData.Snapshot s,
                                      final OfflinePlayerData.Record r, final Button deleteBtn,
                                      final Button backupBtn, final Button editBottom) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        LinearLayout panel = MinecraftUi.panel(activity, "删除玩家数据");
        panel.addView(MinecraftUi.text(activity,
                I18n.t("世界：") + s.world + "\n"
                        + I18n.t("玩家：") + (r.boundName == null || r.boundName.isEmpty()
                        ? r.label() : r.boundName) + "\n"
                        + I18n.t("账号：") + r.msaId + "\n\n"
                        + I18n.t("将删除该世界中的这个玩家数据，其他玩家不受影响。\n")
                        + I18n.t("背包、等级、位置、末影箱都会清空，再次进服将重新生成。\n")
                        + I18n.t("此操作不可撤销（应用会先自动备份数据库）。"),
                8.5f, MinecraftUi.PANEL_TEXT, false));
        Button ok = new MinecraftUi.McButton(activity, "确认删除");
        if (ok instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) ok).setDangerStyle();
        ok.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
        olp.topMargin = dp(activity, 10);
        panel.addView(ok, olp);
        ok.setOnClickListener(v -> {
            d.dismiss();
            deleteBtn.setEnabled(false);
            new Thread(() -> {
                try {
                    String backup = I18n.t("已自动备份数据库");
                    OfflinePlayerData.deletePlayerData(activity, r.msaId, r.serverId);
                    String verify = OfflinePlayerData.verifyDeleted(activity, r.msaId, r.serverId);
                    if (!"ok".equals(verify)) throw new Exception(I18n.t("删除校验失败"));
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, I18n.t("已删除，启动服务端后生效"), Toast.LENGTH_LONG).show();
                        deleteBtn.setVisibility(android.view.View.GONE);
                        backupBtn.setVisibility(android.view.View.GONE);
                        editBottom.setVisibility(android.view.View.GONE);
                    });
                } catch (Throwable e) {
                    activity.runOnUiThread(() -> {
                        deleteBtn.setEnabled(true);
                        Toast.makeText(activity, I18n.t("删除失败：") + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }, "player-delete").start();
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42));
        clp.topMargin = dp(activity, 5);
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

    /** 物品详情弹窗。 */
    private static void showItemDetail(final Activity activity, Map<String, Object> im) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        LinearLayout panel = MinecraftUi.panel(activity, "物品详情");
        String id = BedrockNbt.str(im.get("Name"));
        panel.addView(MinecraftUi.text(activity, ItemNames.display(id), 11, MinecraftUi.PANEL_TEXT, true));
        addDetail(panel, "ID", id);
        addDetail(panel, I18n.t("数量"), BedrockNbt.str(im.get("Count")));
        addDetail(panel, I18n.t("槽位"), BedrockNbt.str(im.get("Slot")));
        addDetail(panel, I18n.t("耐久损耗"), BedrockNbt.str(im.get("Damage")));
        Map<String, Object> tag = BedrockNbt.asMap(im.get("tag"));
        if (tag != null) {
            Object unb = tag.get("Unbreakable");
            if (unb != null) addDetail(panel, I18n.t("无限耐久"), String.valueOf(unb));
            List<Object> ench = BedrockNbt.asList(tag.get("ench"));
            if (ench != null && !ench.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Object e : ench) {
                    Map<String, Object> em = BedrockNbt.asMap(e);
                    if (em == null) continue;
                    if (sb.length() > 0) sb.append("、");
                    sb.append(Translations.enchant(em.get("id")))
                            .append(" ").append(BedrockNbt.str(em.get("lvl"))).append(I18n.t(" 级"));
                }
                addDetail(panel, I18n.t("附魔"), sb.toString());
            }
            Map<String, Object> display = BedrockNbt.asMap(tag.get("display"));
            if (display != null) {
                String cn = BedrockNbt.str(display.get("Name"));
                if (!cn.isEmpty()) addDetail(panel, I18n.t("自定义名称"), cn);
                List<Object> lore = BedrockNbt.asList(display.get("Lore"));
                if (lore != null && !lore.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Object o : lore) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(o);
                    }
                    addDetail(panel, I18n.t("描述"), sb.toString());
                }
            }
            Object rc = tag.get("RepairCost");
            if (rc != null) addDetail(panel, I18n.t("修复成本"), String.valueOf(rc));
            addDetail(panel, I18n.t("原始 tag"), compact(tag));
        }
        Button close = new MinecraftUi.McButton(activity, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42));
        clp.topMargin = dp(activity, 8);
        panel.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());
        ScrollView sv = new ScrollView(activity);
        sv.addView(panel);
        wrap.addView(sv);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.72f));
        }
    }

    private static void addDetail(LinearLayout panel, String label, String value) {
        if (value == null || value.isEmpty()) return;
        panel.addView(MinecraftUi.text(panel.getContext(), label + "：" + value,
                8.5f, MinecraftUi.PANEL_MUTED, false));
    }

    private static String compact(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            Object v = e.getValue();
            if (v instanceof Map) sb.append(e.getKey()).append("={...}");
            else if (v instanceof java.util.List) sb.append(e.getKey()).append("=[")
                    .append(((java.util.List<?>) v).size()).append(I18n.t(" 项]"));
            else sb.append(e.getKey()).append('=').append(v);
        }
        return sb.toString();
    }

    /** 更多数据：未识别字段 + 原始字段。 */
    private static void showUnknown(final Activity activity, List<String> unknown,
                                    List<String> raw) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        LinearLayout panel = MinecraftUi.panel(activity, "更多数据");

        panel.addView(MinecraftUi.text(activity, I18n.t("未识别字段"), 9f,
                MinecraftUi.PANEL_TEXT, true));
        String unknownText = unknown.isEmpty() ? I18n.t("无") : joinLines(unknown);
        TextView ut = MinecraftUi.text(activity, unknownText, 8.5f,
                unknown.isEmpty() ? MinecraftUi.PANEL_MUTED : MinecraftUi.RED, false);
        ut.setPadding(0, dp(activity, 3), 0, dp(activity, 8));
        ut.setTypeface(Typeface.MONOSPACE);
        panel.addView(ut);

        panel.addView(MinecraftUi.text(activity, I18n.t("原始字段"), 9f,
                MinecraftUi.PANEL_TEXT, true));
        TextView rt = MinecraftUi.text(activity, joinLines(raw), 8.5f,
                MinecraftUi.PANEL_MUTED, false);
        rt.setPadding(0, dp(activity, 3), 0, 0);
        rt.setTypeface(Typeface.MONOSPACE);
        ScrollView sv = new ScrollView(activity);
        sv.addView(rt);
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button close = new MinecraftUi.McButton(activity, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42));
        clp.topMargin = dp(activity, 8);
        panel.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.78f));
        }
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static int dp(Activity a, int v) {
        return MinecraftUi.dp(a, v);
    }
}
