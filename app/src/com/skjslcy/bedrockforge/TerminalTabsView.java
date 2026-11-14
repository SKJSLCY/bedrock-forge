package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** 多终端：多个独立 TerminalView + 一个 App 日志终端。 */
public class TerminalTabsView extends LinearLayout {
    private final Activity activity;
    private final LinearLayout tabRow;
    private final FrameLayout content;
    private final List<TerminalView> terminals = new ArrayList<>();
    private int current = 0;

    public TerminalTabsView(Activity activity) {
        super(activity);
        this.activity = activity;
        setOrientation(VERTICAL);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        tabRow = new LinearLayout(activity);
        tabRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tabRow.setOrientation(HORIZONTAL);
        scroll.addView(tabRow);
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        content = new FrameLayout(activity);
        addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        terminals.add(new TerminalView(activity));
        rebuildTabs();
        select(0);
    }

    private void rebuildTabs() {
        tabRow.removeAllViews();
        for (int i = 0; i < terminals.size(); i++) {
            final int idx = i;
            Button b = new MinecraftUi.McButton(activity,
                    (i == current ? "▶ " : "") + I18n.t("终端") + (i + 1));
            b.setTextSize(10);
            if (i == current) b.setTextColor(MinecraftUi.GREEN_BRIGHT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(82), dp(38));
            lp.rightMargin = dp(3);
            tabRow.addView(b, lp);
            b.setOnClickListener(v -> select(idx));
        }
        Button add = new MinecraftUi.McButton(activity, "+");
        add.setTextSize(14);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(42), dp(38));
        addLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        tabRow.addView(add, addLp);
        add.setOnClickListener(v -> {
            terminals.add(new TerminalView(activity));
            rebuildTabs();
            select(terminals.size() - 1);
        });
        Button close = new MinecraftUi.McButton(activity, "关闭");
        close.setTextSize(10);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(64), dp(38));
        clp.leftMargin = dp(3);
        tabRow.addView(close, clp);
        close.setOnClickListener(v -> closeCurrent());
    }

    private void select(int idx) {
        current = Math.max(0, Math.min(idx, terminals.size() - 1));
        rebuildTabs();
        showCurrent();
    }

    private void showCurrent() {
        content.removeAllViews();
        if (current < 0 || current >= terminals.size()) current = 0;
        content.addView(terminals.get(current), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeCurrent() {
        if (terminals.size() <= 1) {
            return;
        }
        terminals.remove(current);
        rebuildTabs();
        select(Math.min(current, terminals.size() - 1));
    }

    private int dp(int v) {
        return MinecraftUi.dp(activity, v);
    }
}
