package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 玩家数据编辑器：基础数值 + 背包物品 + 原始键值。写入前自动备份并校验。 */
final class PlayerDataEditor {

    private static final Set<String> DURABLE = new HashSet<>(Arrays.asList(
            "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword",
            "netherite_sword", "wooden_axe", "stone_axe", "iron_axe", "golden_axe",
            "diamond_axe", "netherite_axe", "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe",
            "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe", "wooden_shovel",
            "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel", "netherite_shovel",
            "wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe",
            "bow", "crossbow", "trident", "fishing_rod", "flint_and_steel", "shears",
            "shield", "elytra", "carrot_on_a_stick", "warped_fungus_on_a_stick",
            "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots",
            "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots",
            "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
            "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
            "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
            "turtle_helmet", "iron_horse_armor", "golden_horse_armor", "diamond_horse_armor",
            "mace", "wooden_spear", "stone_spear", "copper_spear", "iron_spear",
            "golden_spear", "diamond_spear", "netherite_spear"));

    private static final class ItemRef {
        String label;
        NbtCodec.Tag tag;
    }

    private final Activity activity;
    private final OfflinePlayerData.Record record;
    private final Runnable onDone;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ItemRef> items = new ArrayList<>();
    private NbtCodec.Tag root;
    private Map<String, NbtCodec.Tag> rootMap;
    private boolean versionSupportsUnbreakable;

    private EditText health, air, level, progress, mode, posX, posY, posZ;

    private PlayerDataEditor(Activity activity, OfflinePlayerData.Record record, Runnable onDone) {
        this.activity = activity;
        this.record = record;
        this.onDone = onDone;
    }

    static void start(Activity activity, OfflinePlayerData.Record record, Runnable onDone) {
        new PlayerDataEditor(activity, record, onDone).showRisk();
    }

    private void showRisk() {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(activity, "风险提示");

        panel.addView(MinecraftUi.text(activity,
                I18n.t("你即将直接修改世界存档中的玩家数据。"), 9.5f, MinecraftUi.PANEL_TEXT, true));

        panel.addView(MinecraftUi.text(activity, I18n.t("可能的风险"), 9f, 0xFFFF6B6B, true));
        panel.addView(MinecraftUi.text(activity,
                I18n.t("· 写错类型或字段可能导致该玩家数据损坏\n")
                        + I18n.t("· 服务端启动可能报错或无法进服\n")
                        + I18n.t("· 修改不可撤销（应用会自动备份数据库，可回滚）"),
                8.5f, MinecraftUi.PANEL_MUTED, false));

        panel.addView(MinecraftUi.text(activity,
                I18n.t("请确认服务端已停止。"), 8.5f, MinecraftUi.PANEL_MUTED, false));

        Button ok = new MinecraftUi.McButton(activity, "我已了解，继续");
        if (ok instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) ok).setDangerStyle();
        ok.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        olp.topMargin = dp(10);
        panel.addView(ok, olp);
        ok.setOnClickListener(v -> {
            d.dismiss();
            openEditor();
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
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

    private void confirmSave(final Dialog editor) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(activity, "确认保存");
        panel.addView(MinecraftUi.text(activity,
                I18n.t("即将写入世界存档，写入后无法撤销。\n\n")
                        + I18n.t("可能的风险：\n")
                        + I18n.t("· 该玩家数据损坏\n")
                        + I18n.t("· 服务端启动报错或无法进服\n\n")
                        + I18n.t("确定要保存吗？"),
                9f, MinecraftUi.PANEL_TEXT, false));
        Button ok = new MinecraftUi.McButton(activity, "确认保存");
        if (ok instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) ok).setDangerStyle();
        ok.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        olp.topMargin = dp(10);
        panel.addView(ok, olp);
        ok.setOnClickListener(v -> {
            d.dismiss();
            editor.dismiss();
            saveAsync();
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void openEditor() {
        new Thread(() -> {
            try {
                byte[] data = OfflinePlayerData.readServerData(activity, record.serverId);
                if (data == null) throw new Exception(I18n.t("找不到玩家数据"));
                root = NbtCodec.parse(data);
                rootMap = NbtCodec.map(root);
                if (rootMap == null) throw new Exception(I18n.t("玩家数据格式异常"));
                String ver = DeployManager.detect(activity, DeployManager.TYPE_SERVER).version;
                versionSupportsUnbreakable = atLeast112(ver);
                buildItems();
                handler.post(this::showEditor);
            } catch (Throwable e) {
                handler.post(() -> toast(I18n.t("读取失败：") + e.getMessage()));
            }
        }, "editor-read").start();
    }

    private static boolean atLeast112(String v) {
        try {
            String[] p = v.replaceAll("[^0-9.]", " ").trim().split("\\s+")[0].split("\\.");
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            return major > 1 || (major == 1 && minor >= 12);
        } catch (Throwable t) {
            return true;
        }
    }

    private void buildItems() {
        items.clear();
        collectItems("Inventory", I18n.t("背包"));
        collectItems("Armor", I18n.t("装备"));
        collectItems("Offhand", I18n.t("副手"));
        collectItems("EnderChestInventory", I18n.t("末影箱"));
    }

    private void collectItems(String key, String prefix) {
        NbtCodec.Tag t = rootMap.get(key);
        List<NbtCodec.Tag> list = NbtCodec.list(t);
        if (list == null) return;
        for (NbtCodec.Tag e : list) {
            Map<String, NbtCodec.Tag> im = NbtCodec.map(e);
            if (im == null) continue;
            String name = ItemNames.display(str(im.get("Name")));
            if (name.isEmpty()) continue;
            String slot = str(im.get("Slot"));
            ItemRef ref = new ItemRef();
            ref.label = prefix + I18n.t(" · 槽位 ") + slot + " · " + (name.isEmpty() ? "?" : name);
            ref.tag = e;
            items.add(ref);
        }
    }

    private void showEditor() {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(MinecraftUi.text(activity,
                I18n.t("直接修改存档，保存后需启动服务端才生效。"), 8.5f, 0xFFFF6B6B, false));

        LinearLayout base = card(I18n.t("基础数值"));
        health = addField(base, I18n.t("生命"), numOf(rootMap.get("Health")));
        air = addField(base, I18n.t("氧气"), numOf(rootMap.get("Air")));
        level = addField(base, I18n.t("经验等级"), numOf(rootMap.get("PlayerLevel")));
        progress = addField(base, I18n.t("经验进度"), numOf(rootMap.get("PlayerLevelProgress")));
        mode = addField(base, I18n.t("游戏模式（0-6）"), numOf(rootMap.get("PlayerGameMode")));
        NbtCodec.Tag pos = rootMap.get("Pos");
        List<NbtCodec.Tag> pl = NbtCodec.list(pos);
        posX = addField(base, I18n.t("坐标 X"), pl != null && pl.size() > 0 ? numOf(pl.get(0)) : "");
        posY = addField(base, I18n.t("坐标 Y"), pl != null && pl.size() > 1 ? numOf(pl.get(1)) : "");
        posZ = addField(base, I18n.t("坐标 Z"), pl != null && pl.size() > 2 ? numOf(pl.get(2)) : "");
        addCard(content, base);

        LinearLayout inv = card(I18n.t("背包物品"));
        if (items.isEmpty()) {
            inv.addView(MinecraftUi.text(activity, I18n.t("（没有可编辑的物品）"), 8.5f,
                    MinecraftUi.PANEL_MUTED, false));
        } else {
            for (final ItemRef ref : items) {
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setBackground(MinecraftUi.slotDrawable(activity));
                row.setPadding(dp(8), dp(5), dp(8), dp(5));
                TextView label = MinecraftUi.text(activity, ref.label, 8.5f,
                        MinecraftUi.PANEL_TEXT, false);
                row.addView(label, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                Button edit = new MinecraftUi.McButton(activity, "编辑");
                edit.setTextSize(8.5f);
                row.addView(edit, new LinearLayout.LayoutParams(dp(62), dp(32)));
                edit.setOnClickListener(v -> showItemEditor(ref));
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(4);
                inv.addView(row, rlp);
            }
        }
        addCard(content, inv);


        ScrollView sv = new ScrollView(activity);
        sv.addView(content);
        wrap.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button save = new MinecraftUi.McButton(activity, "保存修改");
        if (save instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) save).setDangerStyle();
        save.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        slp.topMargin = dp(6);
        wrap.addView(save, slp);
        save.setOnClickListener(v -> confirmSave(d));
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(4);
        wrap.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());

        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.96f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.88f));
        }
    }

    private LinearLayout card(String title) {
        return MinecraftUi.panel(activity, title);
    }

    private void addCard(LinearLayout content, LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        content.addView(card, lp);
    }

    private EditText addField(LinearLayout panel, String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackground(MinecraftUi.slotDrawable(activity));
        row.setPadding(dp(8), dp(5), dp(8), dp(5));
        TextView l = MinecraftUi.text(activity, label, 8.5f, MinecraftUi.PANEL_MUTED, false);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
        EditText e = new EditText(activity);
        MinecraftUi.styleInput(e);
        e.setSingleLine(true);
        e.setText(value == null ? "" : value);
        e.setTextSize(10f);
        e.setTextColor(0xFFB8FFB0);
        e.setTypeface(android.graphics.Typeface.MONOSPACE);
        e.setBackground(MinecraftUi.terminalDrawable(activity));
        e.setPadding(dp(6), dp(5), dp(6), dp(5));
        row.addView(e, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(4);
        panel.addView(row, rlp);
        return e;
    }

    private String scalarDump() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, NbtCodec.Tag> e : rootMap.entrySet()) {
            if (!isScalar(e.getValue().type)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append('=').append(rawScalar(e.getValue()));
        }
        return sb.toString();
    }

    private static boolean isScalar(int type) {
        return type == NbtCodec.BYTE || type == NbtCodec.SHORT || type == NbtCodec.INT
                || type == NbtCodec.LONG || type == NbtCodec.FLOAT || type == NbtCodec.DOUBLE
                || type == NbtCodec.STRING;
    }

    private void saveAsync() {
        if (ServerManager.get().isRunning()) {
            toast(I18n.t("请先停止服务端"));
            return;
        }
        new Thread(() -> {
            try {
                applyBasic();
                byte[] out = NbtCodec.write(root);
                if (out.length > 65000) throw new Exception(I18n.t("数据过大，写回被拒绝"));
                String world = OfflinePlayerData.currentWorldForEdit(activity);
                String key = "player_server_" + record.serverId;
                String backup = backupDb(world);
                try {
                    long seq = OfflinePlayerData.writeBack(activity, key, out);
                    String verify = OfflinePlayerData.verifyWritten(activity, key);
                    if (!"ok".equals(verify)) throw new Exception(I18n.t("写入校验失败"));
                    handler.post(() -> {
                        toast(I18n.t("已写入（sequence ") + seq + I18n.t("），启动服务端后生效"));
                        if (onDone != null) onDone.run();
                    });
                } catch (Throwable e) {
                    android.util.Log.e("MCBDS", "write/verify failed, rollback", e);
                    restoreDb(world, backup);
                    throw e;
                }
            } catch (Throwable e) {
                android.util.Log.e("MCBDS", "editor save failed", e);
                handler.post(() -> toast(I18n.t("保存失败：") + e.getMessage()));
            }
        }, "editor-save").start();
    }

    private void applyRaw(String text) throws Exception {
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            NbtCodec.Tag t = rootMap.get(k);
            if (t == null || !isScalar(t.type)) continue;
            try {
                setScalar(t, v);
            } catch (Throwable e) {
                throw new Exception(I18n.t("字段 ") + k + I18n.t(" 的值无效"));
            }
        }
    }

    private void applyBasic() {
        setByKey("Health", health, NbtCodec.SHORT);
        setByKey("Air", air, NbtCodec.SHORT);
        setByKey("PlayerLevel", level, NbtCodec.INT);
        setByKey("PlayerLevelProgress", progress, NbtCodec.FLOAT);
        setByKey("PlayerGameMode", mode, NbtCodec.INT);
        NbtCodec.Tag pos = rootMap.get("Pos");
        List<NbtCodec.Tag> pl = NbtCodec.list(pos);
        if (pl != null && pl.size() >= 3) {
            try {
                pl.get(0).value = Float.parseFloat(posX.getText().toString().trim());
                pl.get(1).value = Float.parseFloat(posY.getText().toString().trim());
                pl.get(2).value = Float.parseFloat(posZ.getText().toString().trim());
            } catch (Throwable ignored) {
            }
        }
    }

    private void setByKey(String key, EditText input, int defaultType) {
        if (input == null) return;
        String v = input.getText().toString().trim();
        if (v.isEmpty()) return;
        NbtCodec.Tag t = rootMap.get(key);
        if (t == null) {
            t = new NbtCodec.Tag(defaultType, null);
            rootMap.put(key, t);
        }
        try {
            setScalar(t, v);
        } catch (Throwable ignored) {
        }
    }

    private void setScalar(NbtCodec.Tag t, String v) throws Exception {
        switch (t.type) {
            case NbtCodec.BYTE:
                t.value = (byte) Integer.parseInt(v);
                break;
            case NbtCodec.SHORT:
                t.value = (short) Integer.parseInt(v);
                break;
            case NbtCodec.INT:
                t.value = Integer.parseInt(v);
                break;
            case NbtCodec.LONG:
                t.value = Long.parseLong(v);
                break;
            case NbtCodec.FLOAT:
                t.value = Float.parseFloat(v);
                break;
            case NbtCodec.DOUBLE:
                t.value = Double.parseDouble(v);
                break;
            case NbtCodec.STRING:
                t.value = v;
                break;
            default:
                break;
        }
    }

    private void showItemEditor(final ItemRef ref) {
        final Map<String, NbtCodec.Tag> im = NbtCodec.map(ref.tag);
        if (im == null) return;
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout panel = MinecraftUi.panel(activity, "编辑物品");
        String id = str(im.get("Name"));
        panel.addView(MinecraftUi.text(activity, ItemNames.display(id), 11,
                MinecraftUi.PANEL_TEXT, true));
        panel.addView(MinecraftUi.text(activity,
                id + I18n.t(" · 槽位 ") + str(im.get("Slot")), 8f, MinecraftUi.PANEL_MUTED, false));

        panel.addView(MinecraftUi.text(activity, I18n.t("基础属性"), 9f,
                MinecraftUi.PANEL_MUTED, true));
        final EditText count = addField(panel, I18n.t("数量"), numOf(im.get("Count")));
        final boolean durable = isDurable(im);
        final EditText damage = durable ? addField(panel, I18n.t("耐久损耗"), numOf(im.get("Damage"))) : null;

        panel.addView(MinecraftUi.text(activity, I18n.t("附魔"), 9f,
                MinecraftUi.PANEL_MUTED, true));
        panel.addView(MinecraftUi.text(activity,
                I18n.t("格式：id:等级，多个用逗号分隔，如 9:5,17:3"), 8f,
                MinecraftUi.PANEL_MUTED, false));
        final EditText ench = addField(panel, I18n.t("附魔列表"), enchantText(im));

        panel.addView(MinecraftUi.text(activity, I18n.t("显示名称与描述"), 9f,
                MinecraftUi.PANEL_MUTED, true));
        panel.addView(MinecraftUi.text(activity,
                I18n.t("支持 § 颜色/格式代码；描述支持换行。"), 8f,
                MinecraftUi.PANEL_MUTED, false));
        final EditText nameField = addField(panel, I18n.t("自定义名称"), displayName(im));
        final EditText loreField = addMultiline(panel, I18n.t("描述"), displayLore(im));
        addColorRow(panel, nameField, loreField);

        final boolean[] unbState = {isUnbreakable(im)};
        final boolean showUnb = durable && versionSupportsUnbreakable;
        if (showUnb) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackground(MinecraftUi.slotDrawable(activity));
            row.setPadding(dp(8), dp(5), dp(8), dp(5));
            TextView l = MinecraftUi.text(activity, "无限耐久（Unbreakable）", 8.5f,
                    MinecraftUi.PANEL_MUTED, false);
            row.addView(l, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final Button toggle = new MinecraftUi.McButton(activity,
                    unbState[0] ? "已开启" : "已关闭");
            toggle.setTextSize(8.5f);
            toggle.setOnClickListener(v -> {
                unbState[0] = !unbState[0];
                toggle.setText(unbState[0] ? I18n.t("已开启") : I18n.t("已关闭"));
            });
            row.addView(toggle, new LinearLayout.LayoutParams(dp(86), dp(32)));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(8);
            panel.addView(row, rlp);
        }

        Button ok = new MinecraftUi.McButton(activity, "应用");
        if (ok instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) ok).setDangerStyle();
        ok.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        olp.topMargin = dp(12);
        panel.addView(ok, olp);
        ok.setOnClickListener(v -> {
            try {
                setIntTag(im, "Count", count.getText().toString().trim());
                if (damage != null) setIntTag(im, "Damage", damage.getText().toString().trim());
                applyEnchants(im, ench.getText().toString().trim());
                applyDisplay(im, nameField.getText().toString(), loreField.getText().toString());
                if (showUnb) applyUnbreakable(im, unbState[0]);
                d.dismiss();
            } catch (Throwable e) {
                toast(I18n.t("物品参数无效：") + e.getMessage());
            }
        });
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());

        ScrollView sv = new ScrollView(activity);
        sv.addView(panel);
        wrap.addView(sv);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.8f));
        }
    }

    private EditText addMultiline(LinearLayout panel, String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(MinecraftUi.slotDrawable(activity));
        row.setPadding(dp(8), dp(5), dp(8), dp(5));
        row.addView(MinecraftUi.text(activity, label, 8.5f, MinecraftUi.PANEL_MUTED, false));
        EditText e = new EditText(activity);
        MinecraftUi.styleInput(e);
        e.setText(value == null ? "" : value);
        e.setTextSize(9.5f);
        e.setTextColor(0xFFB8FFB0);
        e.setTypeface(android.graphics.Typeface.MONOSPACE);
        e.setBackground(MinecraftUi.terminalDrawable(activity));
        e.setPadding(dp(6), dp(5), dp(6), dp(5));
        e.setSingleLine(false);
        e.setMinLines(3);
        e.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        elp.topMargin = dp(4);
        row.addView(e, elp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(6);
        panel.addView(row, rlp);
        return e;
    }

    private void addColorRow(LinearLayout panel, final EditText nameField, final EditText loreField) {
        final EditText[] target = {nameField};
        android.view.View.OnFocusChangeListener focus = (v, has) -> {
            if (has && v instanceof EditText) target[0] = (EditText) v;
        };
        nameField.setOnFocusChangeListener(focus);
        loreField.setOnFocusChangeListener(focus);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(activity);
        LinearLayout row = new LinearLayout(activity);
        addColorButton(row, target, "§0", I18n.t("黑"), 0xFF000000);
        addColorButton(row, target, "§1", I18n.t("深蓝"), 0xFF0000AA);
        addColorButton(row, target, "§2", I18n.t("深绿"), 0xFF00AA00);
        addColorButton(row, target, "§3", I18n.t("深青"), 0xFF00AAAA);
        addColorButton(row, target, "§4", I18n.t("深红"), 0xFFAA0000);
        addColorButton(row, target, "§5", I18n.t("深紫"), 0xFFAA00AA);
        addColorButton(row, target, "§6", I18n.t("金"), 0xFFFFAA00);
        addColorButton(row, target, "§7", I18n.t("灰"), 0xFFAAAAAA);
        addColorButton(row, target, "§8", I18n.t("深灰"), 0xFF555555);
        addColorButton(row, target, "§9", I18n.t("亮蓝"), 0xFF5555FF);
        addColorButton(row, target, "§a", I18n.t("亮绿"), 0xFF55FF55);
        addColorButton(row, target, "§b", I18n.t("亮青"), 0xFF55FFFF);
        addColorButton(row, target, "§c", I18n.t("亮红"), 0xFFFF5555);
        addColorButton(row, target, "§d", I18n.t("亮紫"), 0xFFFF55FF);
        addColorButton(row, target, "§e", I18n.t("亮黄"), 0xFFFFFF55);
        addColorButton(row, target, "§f", I18n.t("白"), 0xFFFFFFFF);
        addColorButton(row, target, "§g", I18n.t("金锭"), 0xFFDDD605);
        addColorButton(row, target, "§h", I18n.t("石英"), 0xFFE3D4D1);
        addColorButton(row, target, "§i", I18n.t("铁"), 0xFFCECACA);
        addColorButton(row, target, "§j", I18n.t("合金"), 0xFF443A3B);
        addColorButton(row, target, "§m", I18n.t("红石"), 0xFF971607);
        addColorButton(row, target, "§n", I18n.t("铜"), 0xFFB4684D);
        addColorButton(row, target, "§p", I18n.t("金"), 0xFFDEB12D);
        addColorButton(row, target, "§q", I18n.t("绿宝石"), 0xFF47A036);
        addColorButton(row, target, "§s", I18n.t("钻石"), 0xFF2CBAA8);
        addColorButton(row, target, "§t", I18n.t("青金石"), 0xFF21497B);
        addColorButton(row, target, "§u", I18n.t("紫水晶"), 0xFF9A5CC6);
        addFormatButton(row, target, "§r", I18n.t("重置"), 0xFFFFFFFF);
        addFormatButton(row, target, "§l", I18n.t("加粗"), 0xFFFFFFFF);
        addFormatButton(row, target, "§o", I18n.t("斜体"), 0xFFFFFFFF);
        addFormatButton(row, target, "§n", I18n.t("下划线"), 0xFFFFFFFF);
        addFormatButton(row, target, "§m", I18n.t("删除线"), 0xFFFFFFFF);
        addFormatButton(row, target, "§k", I18n.t("随机"), 0xFFFFFFFF);
        hs.addView(row);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(6);
        panel.addView(hs, hlp);
    }

    private void addColorButton(LinearLayout row, final EditText[] target,
                                final String code, String label, int color) {
        Button b = new MinecraftUi.McButton(activity, code + " " + label);
        b.setTextSize(8.5f);
        b.setTextColor(color);
        b.setShadowLayer(3f, 0f, 0f, 0xFFFFFFFF);
        b.setBackground(MinecraftUi.terminalDrawable(activity));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(74), dp(32));
        blp.rightMargin = dp(3);
        row.addView(b, blp);
        b.setOnClickListener(v -> insertAtCursor(target[0], code));
    }

    private void addFormatButton(LinearLayout row, final EditText[] target,
                                 final String code, String label, int color) {
        Button b = new MinecraftUi.McButton(activity, code + " " + label);
        b.setTextSize(8f);
        b.setTextColor(color);
        b.setShadowLayer(3f, 0f, 0f, 0xFFFFFFFF);
        b.setBackground(MinecraftUi.terminalDrawable(activity));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(96), dp(32));
        blp.rightMargin = dp(3);
        row.addView(b, blp);
        b.setOnClickListener(v -> insertAtCursor(target[0], code));
    }

    private void insertAtCursor(EditText e, String text) {
        int start = Math.max(0, e.getSelectionStart());
        int end = Math.max(0, e.getSelectionEnd());
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        try {
            e.getText().replace(start, end, text);
        } catch (Throwable t) {
            e.append(text);
        }
    }

    private void setIntTag(Map<String, NbtCodec.Tag> im, String key, String v) throws Exception {
        NbtCodec.Tag t = im.get(key);
        if (t == null) return;
        if (t.type == NbtCodec.BYTE) t.value = (byte) Integer.parseInt(v);
        else if (t.type == NbtCodec.SHORT) t.value = (short) Integer.parseInt(v);
        else if (t.type == NbtCodec.INT) t.value = Integer.parseInt(v);
        else if (t.type == NbtCodec.LONG) t.value = Long.parseLong(v);
    }

    private static String enchantText(Map<String, NbtCodec.Tag> im) {
        Map<String, NbtCodec.Tag> tag = NbtCodec.map(im.get("tag"));
        if (tag == null) return "";
        List<NbtCodec.Tag> ench = NbtCodec.list(tag.get("ench"));
        if (ench == null) return "";
        StringBuilder sb = new StringBuilder();
        for (NbtCodec.Tag e : ench) {
            Map<String, NbtCodec.Tag> em = NbtCodec.map(e);
            if (em == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(intOf(em.get("id"))).append(':').append(intOf(em.get("lvl")));
        }
        return sb.toString();
    }

    private void applyEnchants(Map<String, NbtCodec.Tag> im, String text) throws Exception {
        Map<String, NbtCodec.Tag> tag = ensureTag(im);
        List<NbtCodec.Tag> list = new ArrayList<>();
        if (!text.isEmpty()) {
            for (String part : text.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                String[] kv = p.split(":");
                if (kv.length != 2) throw new Exception(I18n.t("附魔格式应为 id:等级"));
                int id = Integer.parseInt(kv[0].trim());
                int lvl = Integer.parseInt(kv[1].trim());
                Map<String, NbtCodec.Tag> em = new java.util.LinkedHashMap<>();
                em.put("id", new NbtCodec.Tag(NbtCodec.SHORT, (short) id));
                em.put("lvl", new NbtCodec.Tag(NbtCodec.SHORT, (short) lvl));
                list.add(new NbtCodec.Tag(NbtCodec.COMPOUND, em));
            }
        }
        if (list.isEmpty()) {
            tag.remove("ench");
        } else {
            tag.put("ench", new NbtCodec.Tag(NbtCodec.LIST, list));
        }
    }

    private Map<String, NbtCodec.Tag> ensureTag(Map<String, NbtCodec.Tag> im) {
        Map<String, NbtCodec.Tag> tag = NbtCodec.map(im.get("tag"));
        if (tag == null) {
            tag = new java.util.LinkedHashMap<>();
            im.put("tag", new NbtCodec.Tag(NbtCodec.COMPOUND, tag));
        }
        return tag;
    }

    private String displayName(Map<String, NbtCodec.Tag> im) {
        Map<String, NbtCodec.Tag> tag = NbtCodec.map(im.get("tag"));
        Map<String, NbtCodec.Tag> display = tag == null ? null : NbtCodec.map(tag.get("display"));
        return display == null ? "" : str(display.get("Name"));
    }

    private String displayLore(Map<String, NbtCodec.Tag> im) {
        Map<String, NbtCodec.Tag> tag = NbtCodec.map(im.get("tag"));
        Map<String, NbtCodec.Tag> display = tag == null ? null : NbtCodec.map(tag.get("display"));
        if (display == null) return "";
        List<NbtCodec.Tag> lore = NbtCodec.list(display.get("Lore"));
        if (lore == null) return "";
        StringBuilder sb = new StringBuilder();
        for (NbtCodec.Tag e : lore) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(str(e));
        }
        return sb.toString();
    }

    private void applyDisplay(Map<String, NbtCodec.Tag> im, String name, String lore) {
        Map<String, NbtCodec.Tag> tag = ensureTag(im);
        Map<String, NbtCodec.Tag> display = NbtCodec.map(tag.get("display"));
        if (display == null) {
            display = new java.util.LinkedHashMap<>();
            tag.put("display", new NbtCodec.Tag(NbtCodec.COMPOUND, display));
        }
        if (name.trim().isEmpty()) display.remove("Name");
        else display.put("Name", new NbtCodec.Tag(NbtCodec.STRING, name));
        List<NbtCodec.Tag> lines = new ArrayList<>();
        for (String line : lore.split("\n")) {
            if (!line.isEmpty()) lines.add(new NbtCodec.Tag(NbtCodec.STRING, line));
        }
        if (lines.isEmpty()) display.remove("Lore");
        else display.put("Lore", new NbtCodec.Tag(NbtCodec.LIST, lines));
    }

    private boolean isUnbreakable(Map<String, NbtCodec.Tag> im) {
        Map<String, NbtCodec.Tag> tag = NbtCodec.map(im.get("tag"));
        if (tag == null) return false;
        NbtCodec.Tag t = tag.get("Unbreakable");
        return t != null && t.type == NbtCodec.BYTE && ((Number) t.value).intValue() != 0;
    }

    private void applyUnbreakable(Map<String, NbtCodec.Tag> im, boolean on) {
        Map<String, NbtCodec.Tag> tag = ensureTag(im);
        if (on) tag.put("Unbreakable", new NbtCodec.Tag(NbtCodec.BYTE, (byte) 1));
        else tag.remove("Unbreakable");
    }

    private boolean isDurable(Map<String, NbtCodec.Tag> im) {
        NbtCodec.Tag dmg = im.get("Damage");
        if (dmg != null && dmg.value instanceof Number
                && ((Number) dmg.value).doubleValue() > 0) return true;
        String id = str(im.get("Name"));
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        return DURABLE.contains(id);
    }

    private String backupDb(String world) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        String path = DeployManager.BACKUPS_DIR + "/playeredit-" + ts + ".tar.gz";
        ShizukuShell.sh("mkdir -p " + q(DeployManager.BACKUPS_DIR)
                + "; tar -czf " + q(path) + " -C " + q(DeployManager.SERVER_DIR + "/worlds")
                + " " + q(world) + " 2>/dev/null || true");
        if (!"1".equals(ShizukuShell.sh("test -f " + q(path) + " && echo 1 || echo 0").trim())) {
            throw new Exception(I18n.t("自动备份失败，已取消写入"));
        }
        return path;
    }

    private void restoreDb(String world, String backup) {
        try {
            ShizukuShell.sh("rm -rf " + q(DeployManager.SERVER_DIR + "/worlds/" + world)
                    + "; tar -xzf " + q(backup) + " -C "
                    + q(DeployManager.SERVER_DIR + "/worlds") + " 2>/dev/null || true");
        } catch (Throwable ignored) {
        }
    }

    private static String numOf(NbtCodec.Tag t) {
        if (t == null || t.value == null) return "";
        Object v = t.value;
        if (v instanceof Float || v instanceof Double) {
            double d = ((Number) v).doubleValue();
            if (Math.abs(d - Math.rint(d)) < 0.0001) return String.valueOf((long) Math.rint(d));
            return String.valueOf(d);
        }
        return String.valueOf(v);
    }

    private static String rawScalar(NbtCodec.Tag t) {
        return String.valueOf(t.value);
    }

    private static String str(NbtCodec.Tag t) {
        return t == null || t.value == null ? "" : String.valueOf(t.value);
    }

    private static long intOf(NbtCodec.Tag t) {
        return t == null || !(t.value instanceof Number) ? 0 : ((Number) t.value).longValue();
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return MinecraftUi.dp(activity, v);
    }
}
