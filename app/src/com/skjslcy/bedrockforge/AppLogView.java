package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;

/** 只读的 App 操作日志终端。 */
public class AppLogView extends ScrollView {
    private final TextView text;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            String s = AppLog.text();
            if (!s.equals(text.getText().toString())) {
                text.setText(s.isEmpty() ? I18n.t("暂无 App 操作日志。\n") : s);
                fullScroll(FOCUS_DOWN);
            }
            handler.postDelayed(this, 1000);
        }
    };

    public AppLogView(Activity activity) {
        super(activity);
        setBackground(MinecraftUi.terminalDrawable(activity));
        setPadding(dp(8), dp(8), dp(8), dp(8));
        text = MinecraftUi.text(activity, "暂无 App 操作日志。\n", 9.5f, 0xFFD0D0D0, false);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        addView(text);
        handler.post(poll);
    }

    private int dp(int v) {
        return MinecraftUi.dp(getContext(), v);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(poll);
        super.onDetachedFromWindow();
    }
}
