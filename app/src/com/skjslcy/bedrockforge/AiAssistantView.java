package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** AI 助手页：多会话、非流式对话、图片/文件、工具调用（执行前确认）。 */
final class AiAssistantView extends LinearLayout {

    static final int REQ_IMAGE = 9001;
    static final int REQ_FILE = 9002;

    private final Activity activity;
    private final LinearLayout tabRow;
    private final LinearLayout messagesBox;
    private final ScrollView chatScroll;
    private final EditText input;
    private final Button send;
    private final TextView status;

    private JSONArray sessions;
    private int current;
    private final List<AiHttp.Msg> history = new ArrayList<>();
    private final List<String> pendingImages = new ArrayList<>();
    private final List<Button> toolButtons = new ArrayList<>();
    private boolean busy;

    AiAssistantView(Activity a) {
        super(a);
        activity = a;
        setOrientation(VERTICAL);

        sessions = AiConfig.sessions(activity);
        if (sessions.length() == 0) {
            sessions.put(newSession("新对话"));
        }
        current = 0;

        HorizontalScrollView tabScroll = new HorizontalScrollView(activity);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabRow = new LinearLayout(activity);
        tabRow.setOrientation(HORIZONTAL);
        tabScroll.addView(tabRow);
        addView(tabScroll, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        chatScroll = new ScrollView(activity);
        chatScroll.setFillViewport(true);
        messagesBox = new LinearLayout(activity);
        messagesBox.setOrientation(VERTICAL);
        messagesBox.setPadding(0, dp(4), 0, dp(4));
        chatScroll.addView(messagesBox);
        addView(chatScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        status = MinecraftUi.text(activity, "", 9f, 0xFFE8E8E8, false);
        addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout tools = new LinearLayout(activity);
        tools.setOrientation(HORIZONTAL);
        tools.setPadding(0, dp(4), 0, 0);
        String[] toolLabels = {"上传图片", "上传文件", "模型", "思考强度"};
        for (String label : toolLabels) {
            Button b = new MinecraftUi.McButton(activity, label);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            tools.addView(b, lp);
            toolButtons.add(b);
            final String tool = label;
            b.setOnClickListener(v -> onToolButton(tool));
        }
        addView(tools, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(activity);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams irlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        irlp.topMargin = dp(6);
        addView(inputRow, irlp);

        input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        input.setTextColor(0xFFB8FFB0);
        input.setHintTextColor(0xFF6A8A6A);
        input.setHint("输入消息…");
        input.setTypeface(Typeface.MONOSPACE);
        input.setBackground(MinecraftUi.terminalDrawable(activity));
        input.setPadding(dp(9), dp(8), dp(9), dp(8));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputRow.addView(input, new LayoutParams(0, dp(42), 1f));

        send = new MinecraftUi.McButton(activity, "发送");
        LayoutParams slp = new LayoutParams(dp(70), dp(42));
        slp.leftMargin = dp(7);
        inputRow.addView(send, slp);
        send.setOnClickListener(v -> sendMessage());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        renderTabs();
        loadSession(current);
    }

    private int dp(int v) {
        return MinecraftUi.dp(activity, v);
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------ sessions

    private JSONObject newSession(String title) {
        JSONObject s = new JSONObject();
        try {
            s.put("id", String.valueOf(System.currentTimeMillis()));
            s.put("title", title);
            s.put("messages", new JSONArray());
        } catch (Throwable ignored) {
        }
        return s;
    }

    private JSONObject session(int i) {
        return sessions.optJSONObject(i);
    }

    private JSONArray sessionMessages(int i) {
        JSONObject s = session(i);
        JSONArray arr = s == null ? null : s.optJSONArray("messages");
        if (arr == null) {
            arr = new JSONArray();
            try {
                if (s != null) s.put("messages", arr);
            } catch (Throwable ignored) {
            }
        }
        return arr;
    }

    private void renderTabs() {
        tabRow.removeAllViews();
        for (int i = 0; i < sessions.length(); i++) {
            final int idx = i;
            JSONObject s = session(i);
            String title = s == null ? "对话" : s.optString("title", "对话");
            LinearLayout tab = new LinearLayout(activity);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(12), dp(8), dp(12), dp(8));
            TextView label = MinecraftUi.text(activity, title, 10,
                    i == current ? 0xFF1B1B1B : 0xFF4A4A4A, true);
            tab.addView(label);
            tab.setBackground(new MinecraftUi.BevelDrawable(
                    i == current ? 0xFFC6C6C6 : 0xFFA8A8A8,
                    i == current ? MinecraftUi.GREEN_BRIGHT : 0xFF151515,
                    i == current ? 0xFFFFFFFF : 0xFFE0E0E0,
                    i == current ? 0xFF5A5A5A : 0xFF777777, dp(2), dp(2)));
            tab.setOnClickListener(v -> {
                if (busy) return;
                current = idx;
                renderTabs();
                loadSession(idx);
            });
            tab.setOnLongClickListener(v -> {
                showSessionMenu(idx);
                return true;
            });
            LinearLayout.LayoutParams lp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(5);
            lp.bottomMargin = dp(4);
            tabRow.addView(tab, lp);
        }
        Button add = new MinecraftUi.McButton(activity, "+");
        LinearLayout.LayoutParams lp = new LayoutParams(dp(44), dp(36));
        lp.bottomMargin = dp(4);
        tabRow.addView(add, lp);
        add.setOnClickListener(v -> {
            if (busy) return;
            sessions.put(newSession("新对话" + (sessions.length() + 1)));
            current = sessions.length() - 1;
            renderTabs();
            loadSession(current);
            saveSessions();
        });
    }

    private void showSessionMenu(final int idx) {
        JSONObject s = session(idx);
        String title = s == null ? "对话" : s.optString("title", "对话");
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(activity, title);
        Button rename = new MinecraftUi.McButton(activity, "重命名");
        panel.addView(rename, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(42)));
        rename.setOnClickListener(v -> {
            d.dismiss();
            renameSession(idx);
        });
        Button del = new MinecraftUi.McButton(activity, "删除");
        if (del instanceof MinecraftUi.McButton) ((MinecraftUi.McButton) del).setDangerStyle();
        del.setTextColor(0xFFFFF2F2);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(42));
        dlp.topMargin = dp(4);
        panel.addView(del, dlp);
        del.setOnClickListener(v -> {
            d.dismiss();
            deleteSession(idx);
        });
        Button cancel = new MinecraftUi.McButton(activity, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(6);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.85f),
                    (int) (ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void renameSession(final int idx) {
        final EditText e = MinecraftUi.terminalInput(activity, "会话名称");
        JSONObject s = session(idx);
        e.setText(s == null ? "" : s.optString("title", ""));
        new AlertDialog.Builder(activity)
                .setTitle("重命名会话")
                .setView(e)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        s.put("title", e.getText().toString().trim());
                    } catch (Throwable ignored) {
                    }
                    renderTabs();
                    saveSessions();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteSession(int idx) {
        if (sessions.length() <= 1) {
            toast("至少保留一个会话");
            return;
        }
        sessions.remove(idx);
        if (current >= sessions.length()) current = sessions.length() - 1;
        renderTabs();
        loadSession(current);
        saveSessions();
    }

    private void saveSessions() {
        AiConfig.saveSessions(activity, sessions);
    }

    private void loadSession(int idx) {
        history.clear();
        pendingImages.clear();
        messagesBox.removeAllViews();
        JSONArray arr = sessionMessages(idx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            String role = m.optString("role", "assistant");
            String text = m.optString("text", "");
            AiHttp.Msg msg = new AiHttp.Msg();
            msg.role = role;
            msg.text = text;
            msg.toolCallId = m.optString("toolId", "");
            JSONArray calls = m.optJSONArray("calls");
            if (calls != null) {
                for (int j = 0; j < calls.length(); j++) {
                    JSONObject c = calls.optJSONObject(j);
                    if (c == null) continue;
                    AiHttp.ToolCall tc = new AiHttp.ToolCall();
                    tc.id = c.optString("id");
                    tc.name = c.optString("name");
                    tc.args = c.optString("args", "{}");
                    msg.calls.add(tc);
                }
            }
            if (!text.isEmpty() && !"tool".equals(role)) history.add(msg);
            if (!text.isEmpty() && !"tool".equals(role)) {
                if ("user".equals(role)) addBubble("你", text, true);
                else addBubble("AI 助手", text, false);
            }
        }
        updateStatus();
        scrollBottom();
    }

    private void persist() {
        JSONArray arr = sessionMessages(current);
        while (arr.length() > 0) arr.remove(arr.length() - 1);
        for (AiHttp.Msg m : history) {
            JSONObject o = new JSONObject();
            try {
                o.put("role", m.role);
                o.put("text", m.text);
                if (!m.calls.isEmpty()) {
                    JSONArray calls = new JSONArray();
                    for (AiHttp.ToolCall c : m.calls) {
                        calls.put(new JSONObject().put("id", c.id).put("name", c.name)
                                .put("args", c.args));
                    }
                    o.put("calls", calls);
                }
            } catch (Throwable ignored) {
            }
            arr.put(o);
        }
        saveSessions();
    }

    // ---------------------------------------------------------------- chat

    private void addBubble(String who, String text, boolean right) {
        if ("你".equals(who)) who = AiConfig.userName(activity);
        else if ("AI 助手".equals(who)) who = AiConfig.aiName(activity);
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(VERTICAL);
        block.setGravity(right ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams blp = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        blp.gravity = right ? Gravity.END : Gravity.START;
        blp.topMargin = dp(7);
        messagesBox.addView(block, blp);

        TextView label = MinecraftUi.text(activity, who, 9,
                right ? 0xFFD8C7AE : 0xFFB8FFB0, true);
        label.setGravity(right ? Gravity.END : Gravity.START);
        label.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        block.addView(label);

        LinearLayout bubble = new LinearLayout(activity);
        bubble.setBackground(new MinecraftUi.BevelDrawable(
                right ? 0xFF8FBF6A : 0xFFC6C6C6, 0xFF151515,
                right ? 0xFFCBE8B0 : 0xFFFFFFFF,
                right ? 0xFF5A7F3A : 0xFF5A5A5A, dp(2), dp(2)));
        bubble.setPadding(dp(10), dp(8), dp(10), dp(8));
        TextView msg = MinecraftUi.text(activity, text, 12, 0xFF1B1B1B, false);
        msg.setMaxWidth(dp(280));
        bubble.addView(msg);
        LinearLayout.LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(3);
        block.addView(bubble, lp);
    }

    private void scrollBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    void refreshStatus() {
        updateStatus();
    }

    private boolean ready() {
        String pid = AiConfig.providerId(activity);
        if (pid.isEmpty()) return false;
        if (!AiConfig.added(activity).contains(pid)) return false;
        if (!AiConfig.hasKey(activity, pid)) return false;
        String m = AiConfig.model(activity);
        return m != null && !m.trim().isEmpty();
    }

    private void updateStatus() {
        String pid = AiConfig.providerId(activity);
        boolean ready = ready();
        send.setEnabled(ready && !busy);
        send.setAlpha(ready ? 1f : 0.5f);
        if (input != null) {
            input.setEnabled(ready);
            input.setAlpha(ready ? 1f : 0.5f);
        }
        for (Button b : toolButtons) {
            b.setEnabled(ready);
            b.setAlpha(ready ? 1f : 0.5f);
        }
        if (pid.isEmpty()) {
            status.setText("未配置服务商 · AI 助手不可用");
            return;
        }
        AiConfig.Provider p = AiConfig.provider(pid);
        if (!AiConfig.hasKey(activity, pid)) {
            status.setText(p.name + " · 未配置 Key · AI 助手不可用");
            return;
        }
        String model = AiConfig.model(activity);
        if (model == null || model.trim().isEmpty()) {
            status.setText(p.name + " · 未选择模型 · AI 助手不可用");
            return;
        }
        int used = history.size();
        int limit = Math.max(1, AiConfig.contextLimit(activity));
        int pct = Math.min(100, used * 100 / limit);
        status.setText(p.name + " · " + model + " · 思考 " + reasoningLabel()
                + " · 上下文 " + used + "/" + limit + "（" + pct + "%）"
                + (pendingImages.isEmpty() ? "" : " · 图片 " + pendingImages.size() + " 张"));
    }

    private String reasoningLabel() {
        switch (AiConfig.reasoning(activity)) {
            case "off": return "关闭";
            case "low": return "低";
            case "high": return "高";
            default: return "中";
        }
    }

    private void sendMessage() {
        if (busy) {
            toast("AI 正在回复");
            return;
        }
        String text = input.getText().toString().trim();
        if (text.isEmpty() && pendingImages.isEmpty()) return;
        if (!ready()) {
            toast("请先在 设置 → AI 智能体 添加服务商、填写 Key 并选择模型");
            return;
        }
        input.setText("");
        AiHttp.Msg user = new AiHttp.Msg();
        user.role = "user";
        user.text = text.isEmpty() ? "（图片）" : text;
        user.images.addAll(pendingImages);
        pendingImages.clear();
        history.add(user);
        addBubble("你", user.text, true);
        persist();
        updateStatus();
        runAgent();
    }

    private void runAgent() {
        busy = true;
        send.setEnabled(false);
        status.setText("AI 正在思考…");
        new Thread(() -> {
            String system = buildSystem();
            List<AiHttp.ToolDef> tools = AiTools.defs();
            try {
                for (int round = 0; round < 6; round++) {
                    int limit = Math.max(1, AiConfig.contextLimit(activity));
                    List<AiHttp.Msg> send = history.size() <= limit
                            ? new ArrayList<>(history)
                            : new ArrayList<>(history.subList(history.size() - limit, history.size()));
                    while (!send.isEmpty() && "tool".equals(send.get(0).role)) send.remove(0);
                    AiHttp.Result r = AiHttp.chat(activity, system, send, tools,
                            AiConfig.reasoning(activity));
                    if (!r.ok) {
                        final String err = r.error;
                        activity.runOnUiThread(() -> finishWithError(err));
                        return;
                    }
                    if (!r.text.isEmpty()) {
                        AiHttp.Msg as = new AiHttp.Msg();
                        as.role = "assistant";
                        as.text = r.text;
                        as.calls.addAll(r.calls);
                        history.add(as);
                        final String out = r.text;
                        activity.runOnUiThread(() -> addBubble("AI 助手", out, false));
                    } else if (!r.calls.isEmpty()) {
                        AiHttp.Msg as = new AiHttp.Msg();
                        as.role = "assistant";
                        as.calls.addAll(r.calls);
                        history.add(as);
                    }
                    if (r.calls.isEmpty()) break;
                    for (AiHttp.ToolCall call : r.calls) {
                        String result = executeTool(call);
                        AiHttp.Msg tool = new AiHttp.Msg();
                        tool.role = "tool";
                        tool.toolCallId = call.id;
                        tool.text = result;
                        history.add(tool);
                    }
                    persist();
                }
                activity.runOnUiThread(this::finishOk);
            } catch (Throwable t) {
                final String err = t.getMessage() == null ? t.toString() : t.getMessage();
                activity.runOnUiThread(() -> finishWithError(err));
            }
        }, "ai-agent").start();
    }

    private String buildSystem() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「基岩工坊」的 AI 助手，可以调用工具查看服务端状态、日志、玩家，")
                .append("并在用户确认后启停服务端或执行命令。回答使用中文，简洁准确。")
                .append("需要操作服务端时必须调用工具，不要编造结果。");
        String extra = AiConfig.systemPrompt(activity);
        if (extra != null && !extra.trim().isEmpty()) sb.append("\n").append(extra);
        return sb.toString();
    }

    private String executeTool(AiHttp.ToolCall call) {
        if (!AiTools.needsConfirm(call.name)) {
            return AiTools.execute(activity, call.name, call.args);
        }
        final boolean[] allowed = {false};
        final CountDownLatch latch = new CountDownLatch(1);
        final String desc = AiTools.describe(call.name, call.args);
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle("AI 请求执行操作")
                .setMessage(desc + "\n\n是否允许？")
                .setPositiveButton("允许", (d, w) -> {
                    allowed[0] = true;
                    latch.countDown();
                })
                .setNegativeButton("拒绝", (d, w) -> latch.countDown())
                .setCancelable(false)
                .show());
        try {
            latch.await();
        } catch (Throwable ignored) {
        }
        if (!allowed[0]) return "用户拒绝执行";
        if ("start_server".equals(call.name)) {
            runOnUiAndWait(() -> ((MainActivity) activity).aiStartServer());
            return "已请求启动服务端，状态：" +
                    (ServerManager.get().isRunning() ? "运行中" : "启动中/失败");
        }
        if ("stop_server".equals(call.name)) {
            runOnUiAndWait(() -> ((MainActivity) activity).aiStopServer());
            return "已请求停止服务端";
        }
        return AiTools.execute(activity, call.name, call.args);
    }

    private void runOnUiAndWait(Runnable r) {
        final CountDownLatch l = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            try {
                r.run();
            } finally {
                l.countDown();
            }
        });
        try {
            l.await();
        } catch (Throwable ignored) {
        }
    }

    private void finishOk() {
        busy = false;
        send.setEnabled(true);
        persist();
        updateStatus();
        scrollBottom();
    }

    private void finishWithError(String err) {
        busy = false;
        send.setEnabled(true);
        addBubble("系统", "请求失败：" + err, false);
        status.setText("请求失败");
    }

    // --------------------------------------------------------------- tools

    private void onToolButton(String tool) {
        if ("上传图片".equals(tool)) {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            activity.startActivityForResult(i, REQ_IMAGE);
        } else if ("上传文件".equals(tool)) {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("text/*");
            activity.startActivityForResult(i, REQ_FILE);
        } else if ("模型".equals(tool)) {
            showModelDialog();
        } else {
            showReasoningDialog();
        }
    }

    private void showModelDialog() {
        final String pid = AiConfig.providerId(activity);
        if (pid.isEmpty()) {
            toast("请先在 设置 → AI 智能体 添加服务商");
            return;
        }
        final AiConfig.Provider p = AiConfig.provider(pid);
        final java.util.List<String> models = AiConfig.detectedModels(activity, pid);
        if (models.isEmpty()) {
            toast("请先在 AI 智能体里检测可用模型");
            return;
        }
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(activity, "选择模型 · " + p.name);
        final LinearLayout list = new LinearLayout(activity);
        list.setOrientation(VERTICAL);
        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAllViews();
            for (final String m : models) {
                boolean selected = m.equals(AiConfig.model(activity));
                Button b = new MinecraftUi.McButton(activity, (selected ? "✓ " : "") + m);
                b.setTextSize(9);
                if (selected) {
                    b.setTextColor(0xFF1B1B1B);
                    b.setBackground(new MinecraftUi.BevelDrawable(0xFFC6C6C6,
                            MinecraftUi.GREEN_BRIGHT, 0xFFFFFFFF, 0xFF5A5A5A,
                            dp(2), dp(2)));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, dp(40));
                lp.topMargin = dp(4);
                list.addView(b, lp);
                b.setOnClickListener(v -> {
                    AiConfig.setModel(activity, pid, m);
                    AiConfig.setProvider(activity, pid);
                    rebuild[0].run();
                    updateStatus();
                });
            }
        };
        rebuild[0].run();
        ScrollView sv = new ScrollView(activity);
        sv.addView(list);
        panel.addView(sv, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        final EditText custom = MinecraftUi.terminalInput(activity, "自定义模型名");
        custom.setText(AiConfig.model(activity));
        panel.addView(custom, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(44)));
        Button use = new MinecraftUi.McButton(activity, "使用自定义模型");
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(42));
        ulp.topMargin = dp(4);
        panel.addView(use, ulp);
        use.setOnClickListener(v -> {
            String m = custom.getText().toString().trim();
            if (m.isEmpty()) return;
            AiConfig.setModel(activity, pid, m);
            AiConfig.setProvider(activity, pid);
            updateStatus();
            d.dismiss();
        });
        Button close = new MinecraftUi.McButton(activity, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(6);
        panel.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.7f));
        }
    }

    private void showReasoningDialog() {
        final String[] labels = {"关闭", "低", "中", "高"};
        final String[] values = {"off", "low", "medium", "high"};
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout panel = MinecraftUi.panel(activity, "思考强度");
        String current = AiConfig.reasoning(activity);
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            boolean selected = value.equals(current);
            Button b = new MinecraftUi.McButton(activity,
                    (selected ? "✓ " : "") + labels[i]);
            if (selected) {
                b.setTextColor(0xFF1B1B1B);
                b.setBackground(new MinecraftUi.BevelDrawable(0xFFC6C6C6,
                        MinecraftUi.GREEN_BRIGHT, 0xFFFFFFFF, 0xFF5A5A5A,
                        dp(2), dp(2)));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(42));
            lp.topMargin = i == 0 ? 0 : dp(4);
            panel.addView(b, lp);
            b.setOnClickListener(v -> {
                AiConfig.setReasoning(activity, value);
                updateStatus();
                d.dismiss();
            });
        }
        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(42));
        clp.topMargin = dp(6);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(v -> d.dismiss());
        wrap.addView(panel);
        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    (int) (ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_IMAGE) {
                byte[] bytes = readAll(uri, 6 * 1024 * 1024);
                pendingImages.add(Base64.encodeToString(bytes, Base64.NO_WRAP));
                updateStatus();
                toast("已添加图片，发送后随消息提交");
            } else if (requestCode == REQ_FILE) {
                byte[] bytes = readAll(uri, 512 * 1024);
                String text = new String(bytes, "UTF-8");
                String name = displayName(uri);
                String cur = input.getText().toString();
                if (!cur.isEmpty()) cur += "\n";
                input.setText(cur + "【文件 " + name + "】\n" + text);
                toast("已载入文件内容");
            }
        } catch (Throwable t) {
            toast("读取失败：" + t.getMessage());
        }
    }

    private byte[] readAll(Uri uri, int max) throws Exception {
        InputStream in = activity.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (bos.size() + n > max) throw new Exception("文件过大");
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = activity.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Throwable ignored) {
        }
        return "文件";
    }
}
