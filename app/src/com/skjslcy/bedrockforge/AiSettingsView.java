package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 设置页：AI 智能体（添加服务商、检测模型、Key 加密）。 */
final class AiSettingsView extends LinearLayout {

    private final Activity activity;

    AiSettingsView(Activity a) {
        super(a);
        activity = a;
        setOrientation(VERTICAL);
        refresh();
    }

    private int dp(int v) {
        return MinecraftUi.dp(activity, v);
    }

    private void refresh() {
        removeAllViews();
        LinearLayout panel = MinecraftUi.panel(activity, "AI 智能体");
        panel.addView(MinecraftUi.text(activity,
                "先添加服务商并填入 API Key，检测到可用模型后选择模型。\n"
                        + "Key 使用 Android Keystore 加密保存在本机。",
                8.5f, MinecraftUi.PANEL_MUTED, false));

        List<String> added = AiConfig.added(activity);
        String pid = AiConfig.providerId(activity);
        if (added.isEmpty()) {
            panel.addView(MinecraftUi.text(activity,
                    "尚未添加服务商，AI 助手不可用。", 9.5f, 0xFFFF6B6B, true));
        } else {
            AiConfig.Provider p = AiConfig.provider(pid);
            String m = AiConfig.model(activity);
            panel.addView(MinecraftUi.text(activity,
                    "当前：" + (pid.isEmpty() ? "未选择" : p.name
                            + " · " + (m.isEmpty() ? "未选择模型" : m)),
                    9.5f, MinecraftUi.PANEL_TEXT, true));
        }

        panel.addView(MinecraftUi.text(activity, "已添加的服务商", 9f,
                MinecraftUi.PANEL_MUTED, true));
        for (final String id : added) {
            AiConfig.Provider p = AiConfig.provider(id);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(MinecraftUi.slotDrawable(activity));
            row.setPadding(dp(8), dp(6), dp(8), dp(6));
            String model = AiConfig.model(activity);
            String status = (AiConfig.hasKey(activity, id) ? "已配置 Key" : "未配置 Key")
                    + (id.equals(pid) && model != null && !model.isEmpty() ? " · " + model : "");
            TextView label = MinecraftUi.text(activity,
                    p.name + (id.equals(pid) ? "（当前）" : "") + "\n" + status,
                    8.5f, id.equals(pid) ? MinecraftUi.PANEL_TEXT : MinecraftUi.PANEL_MUTED,
                    id.equals(pid));
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button edit = new MinecraftUi.McButton(activity, "配置");
            edit.setTextSize(8.5f);
            row.addView(edit, new LinearLayout.LayoutParams(dp(70), dp(34)));
            edit.setOnClickListener(v -> showConfig(id));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(4);
            panel.addView(row, rlp);
        }
        Button add = new MinecraftUi.McButton(activity, "添加服务商");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        alp.topMargin = dp(8);
        panel.addView(add, alp);
        add.setOnClickListener(v -> showAddProvider());

        panel.addView(MinecraftUi.text(activity, "助手设置", 9f,
                MinecraftUi.PANEL_MUTED, true));
        final EditText aiName = MinecraftUi.terminalInput(activity, "默认：AI 助手");
        aiName.setText(AiConfig.aiName(activity));
        panel.addView(label("AI 助手名称"));
        panel.addView(aiName, height(44));
        final EditText userName = MinecraftUi.terminalInput(activity, "默认：你");
        userName.setText(AiConfig.userName(activity));
        panel.addView(label("用户名称"));
        panel.addView(userName, height(44));
        final EditText ctx = MinecraftUi.terminalInput(activity, "默认 20 条");
        ctx.setText(String.valueOf(AiConfig.contextLimit(activity)));
        panel.addView(label("上下文长度（消息条数，默认 20）"));
        panel.addView(ctx, height(44));
        Button saveChat = new MinecraftUi.McButton(activity, "保存助手设置");
        panel.addView(saveChat, heightMargin(42, 6));
        saveChat.setOnClickListener(v -> {
            AiConfig.setAiName(activity, aiName.getText().toString());
            AiConfig.setUserName(activity, userName.getText().toString());
            try {
                int n = Integer.parseInt(ctx.getText().toString().trim());
                AiConfig.setContextLimit(activity, Math.max(2, Math.min(200, n)));
                toast("已保存（上下文 " + AiConfig.contextLimit(activity) + " 条）");
            } catch (Throwable e) {
                AiConfig.setContextLimit(activity, 20);
                toast("上下文长度无效，已使用默认 20 条");
            }
            refresh();
        });

        panel.addView(MinecraftUi.text(activity, "系统提示词", 9f,
                MinecraftUi.PANEL_MUTED, true));
        android.widget.HorizontalScrollView presetScroll =
                new android.widget.HorizontalScrollView(activity);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(activity);
        presetRow.setOrientation(HORIZONTAL);
        final String currentPreset = AiConfig.presetId(activity);
        for (final String[] row : AiConfig.PROMPTS) {
            Button b = new MinecraftUi.McButton(activity, row[1]);
            b.setTextSize(8f);
            if (row[0].equals(currentPreset)) {
                b.setTextColor(0xFF1B1B1B);
                b.setBackground(new MinecraftUi.BevelDrawable(0xFFC6C6C6,
                        MinecraftUi.GREEN_BRIGHT, 0xFFFFFFFF, 0xFF5A5A5A, dp(2), dp(2)));
            }
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(120), dp(34));
            blp.rightMargin = dp(4);
            presetRow.addView(b, blp);
            b.setOnClickListener(v -> {
                AiConfig.setPreset(activity, row[0]);
                refresh();
            });
        }
        Button customPreset = new MinecraftUi.McButton(activity, "自定义");
        customPreset.setTextSize(8f);
        if ("custom".equals(currentPreset)) {
            customPreset.setTextColor(0xFF1B1B1B);
            customPreset.setBackground(new MinecraftUi.BevelDrawable(0xFFC6C6C6,
                    MinecraftUi.GREEN_BRIGHT, 0xFFFFFFFF, 0xFF5A5A5A, dp(2), dp(2)));
        }
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(dp(90), dp(34));
        clp2.rightMargin = dp(4);
        presetRow.addView(customPreset, clp2);
        customPreset.setOnClickListener(v -> {
            AiConfig.setPreset(activity, "custom");
            refresh();
        });
        presetScroll.addView(presetRow);
        LinearLayout.LayoutParams pslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pslp.topMargin = dp(4);
        panel.addView(presetScroll, pslp);

        final EditText system = MinecraftUi.terminalInput(activity, "系统提示词");
        system.setText(AiConfig.systemPrompt(activity));
        system.setSingleLine(false);
        system.setMinLines(3);
        system.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120));
        slp.topMargin = dp(4);
        panel.addView(system, slp);
        Button saveSys = new MinecraftUi.McButton(activity, "保存系统提示词");
        LinearLayout.LayoutParams sslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        sslp.topMargin = dp(4);
        panel.addView(saveSys, sslp);
        saveSys.setOnClickListener(v -> {
            String text = system.getText().toString().trim();
            String presetPid = AiConfig.presetId(activity);
            if (!"custom".equals(presetPid) && text.equals(AiConfig.presetText(presetPid).trim())) {
                AiConfig.setPreset(activity, presetPid);
            } else {
                AiConfig.setCustomPrompt(activity, text);
                AiConfig.setPreset(activity, "custom");
            }
            toast("已保存系统提示词");
            refresh();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(panel, lp);
    }

    private void showAddProvider() {
        final List<AiConfig.Provider> candidates = new ArrayList<>();
        for (AiConfig.Provider p : AiConfig.providers()) {
            if (!AiConfig.added(activity).contains(p.id)) candidates.add(p);
        }
        if (candidates.isEmpty()) {
            toast("已添加全部服务商");
            return;
        }
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(activity, "添加服务商");
        ScrollView sv = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(VERTICAL);
        for (final AiConfig.Provider p : candidates) {
            Button b = new MinecraftUi.McButton(activity, p.name);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            lp.topMargin = dp(4);
            list.addView(b, lp);
            b.setOnClickListener(v -> {
                AiConfig.addProvider(activity, p.id);
                AiConfig.setProvider(activity, p.id);
                d.dismiss();
                refresh();
                showConfig(p.id);
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
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.65f));
        }
    }

    private void showConfig(final String pid) {
        final AiConfig.Provider p = AiConfig.provider(pid);
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(activity, "配置 · " + p.name);

        final EditText base = MinecraftUi.terminalInput(activity, "Base URL");
        base.setText(AiConfig.baseUrl(activity, pid));
        panel.addView(label("Base URL"));
        panel.addView(base, height(44));

        final EditText key = MinecraftUi.terminalInput(activity,
                AiConfig.hasKey(activity, pid) ? "已保存，留空表示不修改" : "输入 API Key");
        panel.addView(label("API Key"));
        panel.addView(key, height(44));

        final TextView modelLabel = MinecraftUi.text(activity,
                "模型：" + (AiConfig.model(activity) == null || AiConfig.model(activity).isEmpty()
                        ? "未选择" : AiConfig.model(activity)),
                9.5f, MinecraftUi.PANEL_TEXT, true);
        modelLabel.setPadding(0, dp(8), 0, dp(2));
        panel.addView(modelLabel);

        final Button detect = new MinecraftUi.McButton(activity, "检测可用模型");
        panel.addView(detect, heightMargin(44, 4));
        final LinearLayout modelList = new LinearLayout(activity);
        modelList.setOrientation(VERTICAL);
        panel.addView(modelList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fillModels(modelList, pid, modelLabel);

        final Runnable[] autoDetect = new Runnable[1];
        autoDetect[0] = () -> {
            String k = key.getText().toString().trim();
            if (k.length() < 8) return;
            if (!detect.isEnabled()) return;
            detect.performClick();
        };
        key.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                key.removeCallbacks(autoDetect[0]);
                if (s.toString().trim().length() >= 8) {
                    key.postDelayed(autoDetect[0], 900);
                }
            }
        });
        detect.setOnClickListener(v -> {
            saveKeyAndBase(pid, base, key);
            detect.setEnabled(false);
            detect.setText("检测中…");
            toast("正在检测模型…");
            new Thread(() -> {
                AiHttp.ModelList ml = AiHttp.listModels(activity, pid);
                activity.runOnUiThread(() -> {
                    detect.setEnabled(true);
                    detect.setText("检测可用模型");
                    if (!ml.ok) {
                        toast("检测失败：" + ml.error);
                        return;
                    }
                    AiConfig.setDetectedModels(activity, pid, ml.models);
                    fillModels(modelList, pid, modelLabel);
                    toast("检测到 " + ml.models.size() + " 个模型，请选择");
                });
            }, "ai-models").start();
        });

        Button use = new MinecraftUi.McButton(activity,
                pid.equals(AiConfig.providerId(activity)) ? "当前使用中" : "设为当前服务商");
        use.setEnabled(!pid.equals(AiConfig.providerId(activity)));
        panel.addView(use, heightMargin(42, 8));
        use.setOnClickListener(v -> {
            AiConfig.setProvider(activity, pid);
            use.setEnabled(false);
            use.setText("当前使用中");
            refresh();
        });

        Button remove = new MinecraftUi.McButton(activity, "删除该服务商");
        if (remove instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) remove).setDangerStyle();
        remove.setTextColor(0xFFFFF2F2);
        panel.addView(remove, heightMargin(40, 6));
        remove.setOnClickListener(v -> {
            AiConfig.removeProvider(activity, pid);
            d.dismiss();
            refresh();
        });

        Button close = new MinecraftUi.McButton(activity, "关闭");
        panel.addView(close, heightMargin(42, 6));
        close.setOnClickListener(v -> {
            saveKeyAndBase(pid, base, key);
            d.dismiss();
        });

        ScrollView sv = new ScrollView(activity);
        sv.addView(panel);
        wrap.addView(sv);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.82f));
        }
        if (AiConfig.hasKey(activity, pid) && AiConfig.detectedModels(activity, pid).isEmpty()) {
            key.postDelayed(autoDetect[0], 600);
        }
        d.setOnDismissListener(x -> saveKeyAndBase(pid, base, key));
    }

    private void fillModels(final LinearLayout modelList, final String pid,
                            final TextView modelLabel) {
        modelList.removeAllViews();
        List<String> models = AiConfig.detectedModels(activity, pid);
        if (models.isEmpty()) {
            modelList.addView(MinecraftUi.text(activity,
                    "尚未检测模型，请填写 Key 后点击检测。", 8.5f, MinecraftUi.PANEL_MUTED, false));
            return;
        }
        for (final String m : models) {
            boolean selected = m.equals(AiConfig.model(activity));
            Button b = new MinecraftUi.McButton(activity,
                    (selected ? "✓ " : "") + m);
            b.setTextSize(8.5f);
            if (selected) {
                b.setTextColor(0xFF1B1B1B);
                b.setBackground(new MinecraftUi.BevelDrawable(0xFFC6C6C6,
                        MinecraftUi.GREEN_BRIGHT, 0xFFFFFFFF, 0xFF5A5A5A,
                        dp(2), dp(2)));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            lp.topMargin = dp(3);
            modelList.addView(b, lp);
            b.setOnClickListener(v -> {
                AiConfig.setModel(activity, pid, m);
                AiConfig.setProvider(activity, pid);
                modelLabel.setText("模型：" + m);
                fillModels(modelList, pid, modelLabel);
                refresh();
            });
        }
    }

    private void saveKeyAndBase(String pid, EditText base, EditText key) {
        AiConfig.setBaseUrl(activity, pid, base.getText().toString().trim());
        String k = key.getText().toString().trim();
        if (!k.isEmpty()) AiConfig.saveKey(activity, pid, k);
        refresh();
    }

    private TextView label(String s) {
        TextView t = MinecraftUi.text(activity, s, 8.5f, MinecraftUi.PANEL_MUTED, false);
        t.setPadding(0, dp(5), 0, 0);
        return t;
    }

    private LinearLayout.LayoutParams height(int h) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h));
    }

    private LinearLayout.LayoutParams heightMargin(int h, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(h));
        lp.topMargin = dp(top);
        return lp;
    }

    private void toast(String s) {
        android.widget.Toast.makeText(activity, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
