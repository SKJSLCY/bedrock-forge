package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器设置页：读取实际 /data/local/tmp 下的 server.properties，
 * 汉化展示并写回；仅在服务端停止时允许修改。
 */
public class ServerSettingsView extends LinearLayout {

    public interface OnSaved {
        void onSaved(Map<String, String> values);
    }

    private static final String TEXT = "TEXT";
    private static final String NUMBER = "NUMBER";
    private static final String DECIMAL = "DECIMAL";
    private static final String BOOL = "BOOL";
    private static final String ENUM = "ENUM";
    private static final Object WRITE_LOCK = new Object();

    private final Activity activity;
    private final Runnable onBack;
    private final OnSaved onSaved;

    private final TextView warning;
    private final LinearLayout content;
    private final Button saveBtn;

    private final List<Spec> specs = new ArrayList<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, EditText> editors = new LinkedHashMap<>();
    private String original = "";
    private boolean editable = true;

    public ServerSettingsView(Activity activity, Runnable onBack, OnSaved onSaved) {
        super(activity);
        this.activity = activity;
        this.onBack = onBack;
        this.onSaved = onSaved;
        setOrientation(VERTICAL);

        warning = MinecraftUi.text(activity, "不同 BDS 版本支持的键可能不同；未识别的键会保留在“高级（自动）”里。",
                9.5f, 0xFFFFD180, true);
        warning.setPadding(dp(2), dp(4), dp(2), dp(6));
        addView(warning);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        content = new LinearLayout(activity);
        content.setOrientation(VERTICAL);
        content.setPadding(0, 0, 0, dp(6));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        saveBtn = new MinecraftUi.McButton(activity, "保存设置");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        slp.topMargin = dp(6);
        addView(saveBtn, slp);
        saveBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });

        buildSpecs();
        load();
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new MinecraftUi.McButton(activity, "← 返回");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(84), dp(40));
        row.addView(back, blp);
        back.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { onBack.run(); }
        });

        TextView title = MinecraftUi.text(activity, "服务器设置", 18, Color.WHITE, true);
        title.setShadowLayer(dp(2), dp(2), 0, 0xFF1A1A1A);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(12);
        row.addView(title, tlp);
        return row;
    }

    private void buildSpecs() {
        add("server-name", "服务器名称", "显示在局域网世界列表中的名字", TEXT, "基础", "Phone12662B", null, null);
        add("gamemode", "游戏模式", "新玩家的默认游戏模式", ENUM, "基础", "survival",
                new String[]{"survival", "creative", "adventure", "spectator"},
                new String[]{"生存", "创造", "冒险", "旁观"});
        add("difficulty", "难度", "世界难度", ENUM, "基础", "easy",
                new String[]{"peaceful", "easy", "normal", "hard"},
                new String[]{"和平", "简单", "普通", "困难"});
        add("max-players", "最大玩家数", "允许同时在线的玩家数量", NUMBER, "基础", "10", null, null);
        add("level-name", "世界名称", "存档目录名称，修改后使用另一个世界", TEXT, "基础", "Nether62", null, null);
        add("level-seed", "世界种子", "仅新建世界时生效", TEXT, "基础", "", null, null);
        add("server-port", "服务器端口", "TCP 信令端口，修改后需要重启", NUMBER, "基础", "19134", null, null);
        add("server-portv6", "IPv6 端口", "IPv6 信令端口", NUMBER, "基础", "19135", null, null);

        add("allow-cheats", "允许作弊", "开启后玩家可以使用作弊命令", BOOL, "玩家", "false", null, null);
        add("allow-list", "白名单", "只允许白名单中的玩家进入（离线模式可能不生效）", BOOL, "玩家", "false", null, null);
        add("default-player-permission-level", "默认权限", "新玩家的默认权限等级", ENUM, "玩家", "member",
                new String[]{"visitor", "member", "operator"},
                new String[]{"访客", "成员", "管理员"});
        add("force-gamemode", "强制游戏模式", "玩家加入时强制切换到默认模式", BOOL, "玩家", "false", null, null);
        add("player-idle-timeout", "挂机踢出（分钟）", "玩家无操作多久后被踢出，0 为不踢", NUMBER, "玩家", "30", null, null);
        add("chat-restriction", "聊天限制", "限制玩家聊天", ENUM, "玩家", "None",
                new String[]{"None", "Disabled"},
                new String[]{"无限制", "禁用聊天"});
        add("disable-player-interaction", "禁用玩家交互", "禁止玩家之间互相交互", BOOL, "玩家", "false", null, null);
        add("disable-custom-skins", "禁用自定义皮肤", "不允许客户端使用自定义皮肤", BOOL, "玩家", "false", null, null);
        add("disable-persona", "禁用 Persona", "禁用角色外观与表情", BOOL, "玩家", "false", null, null);
        add("texturepack-required", "强制纹理包", "要求客户端使用服务器纹理包", BOOL, "玩家", "false", null, null);

        add("view-distance", "视距", "服务器发送给客户端的区块距离", NUMBER, "世界", "32", null, null);
        add("tick-distance", "模拟距离", "实体和方块更新的距离", NUMBER, "世界", "4", null, null);
        add("enable-lan-visibility", "局域网可见", "允许同一 Wi-Fi 下的客户端发现服务器", BOOL, "世界", "true", null, null);
        add("client-side-chunk-generation-enabled", "客户端区块生成", "允许客户端本地生成部分区块", BOOL, "世界", "true", null, null);
        add("server-build-radius-ratio", "服务器建造半径比例", "限制玩家建造范围，Disabled 为不限制", TEXT, "世界", "Disabled", null, null);
        add("player-position-acceptance-threshold", "玩家位置接受阈值", "服务器接受玩家位置的最大偏差", DECIMAL, "世界", "0.5", null, null);
        add("player-movement-action-direction-threshold", "移动方向阈值", "玩家移动方向判定的阈值", DECIMAL, "世界", "0.85", null, null);

        add("max-threads", "最大线程数", "服务端使用的最大线程数", NUMBER, "性能", "8", null, null);
        add("persistent-ticking-area-max-count", "常驻 ticking 区域数量", "常驻加载区域的最大数量", NUMBER, "性能", "10", null, null);
        add("persistent-ticking-area-max-chunks", "常驻 ticking 区块数", "常驻加载区域的最大区块数", NUMBER, "性能", "100", null, null);
        add("transient-ticking-area-max-chunks", "临时 ticking 区块数", "临时加载区域的最大区块数", NUMBER, "性能", "300", null, null);

        add("online-mode", "在线模式", "需要微软认证；手机网络可能无法完成认证", BOOL, "网络", "false", null, null);
        add("server-udp-ports", "UDP 端口范围", "NetherNet 游戏 UDP 端口，如 19134 或 19140-19149", TEXT, "网络", "19134", null, null);
        add("transport", "传输协议", "当前版本仅支持 NetherNet；RakNet 会启动失败", ENUM, "网络", "nethernet",
                new String[]{"nethernet", "raknet"},
                new String[]{"NetherNet", "RakNet（不支持）"});
        add("server-ip", "绑定 IP", "留空表示所有网卡；只填本机 IP 可限制网卡", TEXT, "网络", "", null, null);
        add("compression-threshold", "压缩阈值", "小于该字节数的数据包不压缩", NUMBER, "网络", "1", null, null);
        add("compression-algorithm", "压缩算法", "网络压缩算法", ENUM, "网络", "zlib",
                new String[]{"zlib", "snappy"},
                new String[]{"zlib", "snappy"});
        add("block-network-ids-are-hashes", "网络 ID 使用哈希", "网络 ID 以哈希形式传输", BOOL, "网络", "true", null, null);

        add("server-authoritative-movement-strict", "严格移动校验", "启用严格的服务器移动校验", BOOL, "权威移动与反作弊", "false", null, null);
        add("server-authoritative-dismount-strict", "严格下马校验", "严格校验玩家下马位置", BOOL, "权威移动与反作弊", "false", null, null);
        add("server-authoritative-entity-interactions-strict", "严格实体交互", "严格校验实体交互", BOOL, "权威移动与反作弊", "false", null, null);
        add("server-authoritative-entity-attack-request-validation", "攻击请求校验", "攻击请求校验模式", TEXT, "权威移动与反作弊", "disabled", null, null);
        add("server-authoritative-entity-attack-range-validation", "攻击距离校验", "攻击距离校验模式", TEXT, "权威移动与反作弊", "disabled", null, null);
        add("server-authoritative-entity-attack-range-tolerance", "攻击距离容差", "攻击距离校验的容差", DECIMAL, "权威移动与反作弊", "0.5", null, null);
        add("server-authoritative-block-breaking-pick-range-scalar", "挖掘距离倍率", "服务器校验挖掘距离的倍率", DECIMAL, "权威移动与反作弊", "1.5", null, null);

        add("content-log-file-enabled", "内容日志写入文件", "把内容日志写入文件", BOOL, "日志与脚本", "false", null, null);
        add("content-log-console-output-enabled", "内容日志输出到控制台", "把内容日志输出到控制台", BOOL, "日志与脚本", "false", null, null);
        add("content-log-level", "内容日志级别", "日志详细程度", ENUM, "日志与脚本", "info",
                new String[]{"info", "debug", "warning", "error"},
                new String[]{"信息", "调试", "警告", "错误"});
        add("allow-inbound-script-debugging", "允许入站脚本调试", "允许外部调试器连接脚本", BOOL, "日志与脚本", "false", null, null);
        add("allow-outbound-script-debugging", "允许出站脚本调试", "允许脚本连接外部调试器", BOOL, "日志与脚本", "false", null, null);
        add("script-debugger-auto-attach", "脚本调试器自动附加", "脚本调试器附加模式", TEXT, "日志与脚本", "disabled", null, null);
        add("emit-server-telemetry", "发送服务器遥测", "向微软发送匿名服务器遥测", BOOL, "日志与脚本", "false", null, null);
    }

    private void add(String key, String label, String desc, String type, String section,
                     String def, String[] values, String[] labels) {
        specs.add(new Spec(key, label, desc, type, section, def, values, labels));
    }

    private void ensureExtraSpecs() {
        java.util.Set<String> known = new java.util.HashSet<>();
        for (Spec spec : specs) known.add(spec.key);
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (known.contains(e.getKey())) continue;
            String v = e.getValue() == null ? "" : e.getValue();
            String type;
            if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
                type = BOOL;
            } else if (v.matches("-?\\d+")) {
                type = NUMBER;
            } else if (v.matches("-?\\d+\\.\\d+")) {
                type = DECIMAL;
            } else {
                type = TEXT;
            }
            add(e.getKey(), e.getKey(), "未汉化的高级选项", type, "高级（自动）", v, null, null);
        }
    }

    private void load() {
        content.removeAllViews();
        warning.setVisibility(GONE);
        saveBtn.setEnabled(false);
        saveBtn.setText("读取中…");

        LinearLayout loading = MinecraftUi.panel(activity, "读取配置");
        TextView t = MinecraftUi.text(activity, "正在读取 server.properties…", 11, MinecraftUi.PANEL_MUTED, false);
        loading.addView(t);
        content.addView(loading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String text = ShizukuShell.read(ShizukuShell.PROPERTIES);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            original = text;
                            parse(text);
                            buildSettings();
                        }
                    });
                } catch (final Throwable e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showError(e.getMessage() == null ? e.toString() : e.getMessage());
                        }
                    });
                }
            }
        }, "settings-load").start();
    }

    private void parse(String text) {
        values.clear();
        for (String line : text.split("\n", -1)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            if (eq <= 0) continue;
            values.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
        }
    }

    private void showError(String message) {
        content.removeAllViews();
        LinearLayout panel = MinecraftUi.panel(activity, "读取失败");
        TextView t = MinecraftUi.text(activity, message, 10, MinecraftUi.RED, false);
        t.setPadding(0, dp(4), 0, dp(8));
        panel.addView(t);

        if (!ShizukuGate.isRunning()) {
            Button guide = new MinecraftUi.McButton(activity, "启动 Shizuku");
            panel.addView(guide, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            guide.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    ShizukuGate.showDialog(activity, "读取服务器设置", null);
                }
            });
        }

        Button retry = new MinecraftUi.McButton(activity, "重试");
        panel.addView(retry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        retry.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { load(); }
        });
        content.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        warning.setVisibility(VISIBLE);
        warning.setText("需要 Shizuku 已运行并授权，且服务端环境已安装");
        saveBtn.setEnabled(false);
        saveBtn.setText("保存设置");
    }

    private void buildSettings() {
        content.removeAllViews();
        ensureExtraSpecs();
        editable = !ServerManager.get().isRunning();

        warning.setVisibility(VISIBLE);
        if (!editable) {
            warning.setText("服务端运行中：请先停止服务端，再修改配置");
            warning.setTextColor(0xFFFFD180);
        } else {
            warning.setText("服务端已停止：修改会立即写入，下次启动生效。\n不同 BDS 版本支持的键可能不同，未识别的键会保留在“高级（自动）”。");
            warning.setTextColor(0xFFB8FFB0);
        }
        warning.setPadding(dp(2), dp(4), dp(2), dp(6));

        final java.util.Set<String> supported = supportedKeys();
        int hidden = 0;
        String section = null;
        LinearLayout panel = null;
        for (Spec spec : specs) {
            if (!supported.isEmpty() && !supported.contains(spec.key)) {
                hidden++;
                continue;
            }
            if (panel == null || !spec.section.equals(section)) {
                section = spec.section;
                panel = MinecraftUi.panel(activity, section);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                plp.bottomMargin = dp(8);
                content.addView(panel, plp);
            }
            addSettingRow(panel, spec);
        }

        if (hidden > 0) {
            TextView hideNote = MinecraftUi.text(activity,
                    "已按当前 BDS 版本隐藏 " + hidden + " 项不支持的设置。", 8.5f,
                    MinecraftUi.PANEL_MUTED, false);
            hideNote.setPadding(0, dp(6), 0, 0);
            content.addView(hideNote);
        }
        TextView note = MinecraftUi.text(activity, "修改的是实际运行目录中的 server.properties；不会自动重启服务端。", 8.5f,
                MinecraftUi.PANEL_MUTED, false);
        note.setPadding(0, dp(6), 0, dp(4));
        content.addView(note);

        saveBtn.setText("保存设置");
        saveBtn.setEnabled(editable);
    }

    /** 从服务端日志里读取该 BDS 版本实际支持的 server.properties 键。 */
    private java.util.Set<String> supportedKeys() {
        java.util.Set<String> set = new java.util.HashSet<>();
        try {
            String line = ShizukuShell.sh(
                    "grep -o 'Contents of server.properties: .*' "
                            + "'" + DeployManager.SERVER_DIR + "/Dedicated_Server.txt' 2>/dev/null"
                            + " | tail -n 1 || true").trim();
            int idx = line.indexOf('{');
            if (idx < 0) return set;
            org.json.JSONObject o = new org.json.JSONObject(line.substring(idx));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) set.add(it.next());
        } catch (Throwable ignored) {
        }
        return set;
    }

    private void addSettingRow(LinearLayout panel, final Spec spec) {
        String value = values.containsKey(spec.key) ? values.get(spec.key) : spec.def;
        if (spec.type.equals(BOOL) && value == null) value = "false";
        if (value == null) value = "";

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(MinecraftUi.slotDrawable(activity));
        row.setPadding(dp(9), dp(7), dp(9), dp(7));

        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(VERTICAL);
        TextView label = MinecraftUi.text(activity, spec.label, 11, MinecraftUi.PANEL_TEXT, true);
        left.addView(label);
        TextView desc = MinecraftUi.text(activity, spec.desc, 8, MinecraftUi.PANEL_MUTED, false);
        desc.setPadding(0, dp(2), 0, 0);
        left.addView(desc);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (spec.type.equals(TEXT) || spec.type.equals(NUMBER) || spec.type.equals(DECIMAL)) {
            final EditText edit = new EditText(activity);
        MinecraftUi.styleInput(edit);
            edit.setSingleLine(true);
            edit.setText(value);
            edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            edit.setTextColor(0xFFB8FFB0);
            edit.setTypeface(Typeface.MONOSPACE);
            edit.setBackground(MinecraftUi.terminalDrawable(activity));
            edit.setPadding(dp(7), dp(6), dp(7), dp(6));
            if (spec.type.equals(NUMBER)) {
                edit.setInputType(InputType.TYPE_CLASS_NUMBER);
            } else if (spec.type.equals(DECIMAL)) {
                edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            }
            edit.setEnabled(editable);
            edit.setAlpha(editable ? 1f : 0.6f);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(102), dp(40));
            row.addView(edit, elp);
            editors.put(spec.key, edit);
        } else if (spec.type.equals(BOOL)) {
            final boolean[] on = {parseBool(value)};
            final MinecraftUi.McSwitch sw = new MinecraftUi.McSwitch(activity);
            sw.setChecked(on[0]);
            sw.setEnabled(editable);
            sw.setAlpha(editable ? 1f : 0.6f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(66), dp(40));
            row.addView(sw, slp);
            sw.setOnCheckedChangeListener(new MinecraftUi.McSwitch.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(MinecraftUi.McSwitch view, boolean isChecked) {
                    if (!editable) return;
                    on[0] = isChecked;
                    values.put(spec.key, isChecked ? "true" : "false");
                }
            });
        } else {
            final Button btn = new MinecraftUi.McButton(activity, enumLabel(spec, value));
            btn.setEnabled(editable);
            btn.setAlpha(editable ? 1f : 0.6f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(98), dp(40));
            row.addView(btn, blp);
            values.put(spec.key, value);
            btn.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (!editable) return;
                    showEnumDialog(spec, btn);
                }
            });
        }

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(5);
        panel.addView(row, rlp);
    }

    private void showEnumDialog(final Spec spec, final Button btn) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(activity, spec.label);
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < spec.values.length; i++) {
            final String value = spec.values[i];
            Button option = new MinecraftUi.McButton(activity, spec.labels[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            lp.topMargin = i == 0 ? 0 : dp(5);
            panel.addView(option, lp);
            option.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    values.put(spec.key, value);
                    btn.setText(enumLabel(spec, value));
                    dialog.dismiss();
                }
            });
        }

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void save() {
        if (!editable || ServerManager.get().isRunning()) {
            Toast.makeText(activity, "请先停止服务端", Toast.LENGTH_SHORT).show();
            return;
        }
        final Map<String, String> updates = new LinkedHashMap<>();
        for (Spec spec : specs) {
            if (spec.type.equals(TEXT) || spec.type.equals(NUMBER) || spec.type.equals(DECIMAL)) {
                EditText edit = editors.get(spec.key);
                if (edit != null) updates.put(spec.key, edit.getText().toString().trim());
            } else {
                String v = values.get(spec.key);
                if (v != null) updates.put(spec.key, v);
            }
        }

        saveBtn.setEnabled(false);
        saveBtn.setText("保存中…");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String check;
                    synchronized (WRITE_LOCK) {
                        String current = ShizukuShell.read(ShizukuShell.PROPERTIES);
                        String merged = apply(current, updates);
                        ShizukuShell.write(ShizukuShell.PROPERTIES, merged);
                        check = ShizukuShell.read(ShizukuShell.PROPERTIES);
                    }
                    final String finalCheck = check;
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            original = finalCheck;
                            parse(finalCheck);
                            saveBtn.setEnabled(true);
                            saveBtn.setText("保存设置");
                            Toast.makeText(activity, "已保存，下次启动生效", Toast.LENGTH_SHORT).show();
                            AppLog.add("[设置] 保存 " + updates.size() + " 项");
                            if (onSaved != null) onSaved.onSaved(updates);
                        }
                    });
                } catch (final Throwable e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            saveBtn.setEnabled(true);
                            saveBtn.setText("保存设置");
                            Toast.makeText(activity, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, "settings-save").start();
    }

    private static String apply(String original, Map<String, String> updates) {
        Map<String, String> remain = new LinkedHashMap<>(updates);
        List<String> out = new ArrayList<>();
        String[] lines = original.split("\n", -1);
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                int eq = t.indexOf('=');
                if (eq > 0) {
                    String key = t.substring(0, eq).trim();
                    if (remain.containsKey(key)) {
                        out.add(key + "=" + remain.remove(key));
                        continue;
                    }
                }
            }
            out.add(line);
        }
        for (Map.Entry<String, String> e : remain.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            sb.append(out.get(i));
            if (i < out.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private static boolean parseBool(String s) {
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "on".equalsIgnoreCase(s);
    }

    private static String enumLabel(Spec spec, String value) {
        for (int i = 0; i < spec.values.length; i++) {
            if (spec.values[i].equalsIgnoreCase(value)) return spec.labels[i];
        }
        return value;
    }

    private int dp(float v) {
        return MinecraftUi.dp(activity, v);
    }

    private static final class Spec {
        final String key, label, desc, type, section, def;
        final String[] values, labels;

        Spec(String key, String label, String desc, String type, String section,
             String def, String[] values, String[] labels) {
            this.key = key;
            this.label = label;
            this.desc = desc;
            this.type = type;
            this.section = section;
            this.def = def;
            this.values = values;
            this.labels = labels;
        }
    }
}
