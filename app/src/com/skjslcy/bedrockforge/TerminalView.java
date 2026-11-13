package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/** 服务端停止时可用的 Ubuntu 终端（proot + Box64 + rootfs）。 */
public class TerminalView extends LinearLayout {

    private static final String PROOT = "/data/local/tmp/mcbds/proot/proot";

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SpannableStringBuilder buffer = new SpannableStringBuilder();
    private final TextView output;
    private final EditText input;
    private final ScrollView scroll;
    private boolean busy;

    private static final int C_NORMAL = 0xFFD0D0D0;
    private static final int C_COMMAND = 0xFF79C0FF;
    private static final int C_SUCCESS = 0xFF7EE787;
    private static final int C_WARN = 0xFFFFD166;
    private static final int C_ERROR = 0xFFFF6B6B;

    public TerminalView(Activity activity) {
        super(activity);
        this.activity = activity;
        setOrientation(VERTICAL);

        scroll = new ScrollView(activity);
        scroll.setBackground(MinecraftUi.terminalDrawable(activity));
        scroll.setPadding(dp(8), dp(8), dp(8), dp(8));
        output = MinecraftUi.text(activity, "", 9.5f, C_NORMAL, false);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setLineSpacing(dp(2), 1f);
        scroll.addView(output, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        appendColored("Ubuntu 终端 · 服务端停止时可用\n", C_SUCCESS);
        appendColored("命令 蓝 · 输出 白 · 警告 黄 · 错误 红\n\n", C_NORMAL);
        appendColored("$ ", C_COMMAND);

        TextView symbolLabel = MinecraftUi.text(activity, "快捷符号", 9, 0xFFD8C7AE, true);
        symbolLabel.setPadding(0, dp(5), 0, dp(3));
        addView(symbolLabel);

        HorizontalScrollView symbolScroll = new HorizontalScrollView(activity);
        symbolScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout symbolRow = new LinearLayout(activity);
        symbolRow.setOrientation(HORIZONTAL);
        String[] symbols = {"/", "~", "-", "|", ">", "<", "\"", "'", "*", "?", "[", "]", "{", "}", "$", "&", ";", ":"};
        for (final String symbol : symbols) {
            Button b = new MinecraftUi.McButton(activity, symbol);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(40), dp(38));
            blp.setMargins(dp(2), 0, dp(2), 0);
            symbolRow.addView(b, blp);
            b.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { insertSymbol(symbol); }
            });
        }
        symbolScroll.addView(symbolRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(symbolScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(5);
        addView(row, rlp);

        input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        input.setTextColor(0xFFB8FFB0);
        input.setHintTextColor(0xFF6A8A6A);
        input.setHint("输入 Ubuntu 命令，如 ls /");
        input.setTypeface(Typeface.MONOSPACE);
        input.setBackground(MinecraftUi.terminalDrawable(activity));
        input.setPadding(dp(8), dp(7), dp(8), dp(7));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submit();
                    return true;
                }
                return false;
            }
        });
        row.addView(input, new LinearLayout.LayoutParams(0, dp(40), 1f));

        Button send = new MinecraftUi.McButton(activity, "执行");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(76), dp(40));
        slp.leftMargin = dp(6);
        row.addView(send, slp);
        send.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { submit(); }
        });
    }

    public void submit() {
        if (busy) return;
        String cmd = input.getText().toString().trim();
        if (cmd.isEmpty()) return;
        if (ServerManager.get().isRunning()) {
            Toast.makeText(activity, "请先停止服务端", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ShizukuGate.ensure(activity, "Ubuntu 终端")) return;
        input.setText("");
        hideKeyboard();
        appendColored(cmd + "\n", C_COMMAND);
        busy = true;

        new Thread(new Runnable() {
            @Override public void run() {
                int code = -1;
                String out = "";
                try {
                    boolean useBox64 = new java.io.File(
                            "/data/local/tmp/box64/rootfs/usr/local/bin/box64").exists();
                    String bashCmd = "export PATH=/usr/local/sbin:/usr/local/bin:"
                            + "/usr/sbin:/usr/bin:/sbin:/bin; " + cmd;
                    if (!useBox64) {
                        throw new IllegalStateException("Box64 运行环境未部署");
                    }
                    String[] cmdArr = new String[]{
                            PROOT, "--link2symlink", "-r", "/data/local/tmp/box64/rootfs",
                            "-0", "-b", "/dev", "-b", "/proc", "-b", "/sys",
                            "-w", "/root", "/bin/bash", "-lc", bashCmd
                    };
                    String ldPath = "/data/local/tmp/mcbds/proot/lib";
                    String[] env = new String[]{
                            "PROOT_TMP_DIR=/data/local/tmp/mcbds/tmp",
                            "PROOT_LOADER=/data/local/tmp/mcbds/proot/libexec/loader",
                            "PROOT_LOADER_32=/data/local/tmp/mcbds/proot/libexec/loader32",
                            "LD_LIBRARY_PATH=" + ldPath,
                            "HOME=/root",
                            "TERM=xterm",
                            "PATH=/system/bin:/system/xbin"
                    };
                    Process p = ShizukuProcess.start(cmdArr, env, "/data/local/tmp");
                    out = read(p.getInputStream()) + read(p.getErrorStream());
                    code = p.waitFor();
                } catch (Throwable t) {
                    out = "[错误] " + t.getMessage();
                }
                final String text = out;
                final int exit = code;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (!text.isEmpty()) appendAuto(text + (text.endsWith("\n") ? "" : "\n"));
                        appendColored("[exit " + exit + "]\n\n", exit == 0 ? C_SUCCESS : C_ERROR);
                        appendColored("$ ", C_COMMAND);
                        busy = false;
                    }
                });
            }
        }, "ubuntu-terminal").start();
    }

    private static String read(InputStream in) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("WARNING: linker: Warning: failed to find generated linker configuration")) {
                    continue;
                }
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private void appendAuto(String text) {
        for (String line : text.split("\n", -1)) {
            int color = colorFor(line);
            appendColored(line + "\n", color);
        }
    }

    private int colorFor(String line) {
        String u = line.toUpperCase();
        if (line.startsWith("$ ")) return C_COMMAND;
        if (line.startsWith("[EXIT 0]")) return C_SUCCESS;
        if (line.startsWith("[EXIT ")) return C_ERROR;
        if (u.contains("[错误]") || u.contains("ERROR") || u.contains("NOT FOUND")
                || u.contains("PERMISSION DENIED") || u.contains("NO SUCH FILE")
                || u.contains("FAILED") || u.contains("CANNOT")) {
            return C_ERROR;
        }
        if (u.contains("WARNING") || u.contains("WARN")) return C_WARN;
        return C_NORMAL;
    }

    private void appendColored(String s, int color) {
        int start = buffer.length();
        buffer.append(s);
        buffer.setSpan(new ForegroundColorSpan(color), start, buffer.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        output.setText(buffer);
        scroll.post(new Runnable() {
            @Override public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private void insertSymbol(String symbol) {
        int start = input.getSelectionStart();
        if (start < 0) start = input.getText().length();
        String text = input.getText().toString();
        String newText = text.substring(0, start) + symbol + text.substring(start);
        input.setText(newText);
        input.setSelection(start + symbol.length());
        input.requestFocus();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    private int dp(float v) {
        return MinecraftUi.dp(activity, v);
    }
}
