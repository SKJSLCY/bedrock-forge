package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 通用「导入进度」对话框：主题配色进度条 + 文案，支持确定进度与扫描动画。
 * 用于服务端 / 存档 / addons / 备份文件的导入。
 */
final class ProgressDialog {

    static final class Holder {
        private final Dialog dialog;
        private final ProgressBar bar;
        private final TextView text;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private android.animation.ValueAnimator scan;
        private long lastUpdateAt = 0;

        Holder(Dialog d, ProgressBar bar, TextView text) {
            this.dialog = d;
            this.bar = bar;
            this.text = text;
        }

        /** 确定性进度：done/total 字节，label 为阶段说明。 */
        void set(final long done, final long total, final String label) {
            handler.post(new Runnable() {
                @Override public void run() {
                    if (scan != null) {
                        scan.cancel();
                        scan = null;
                        bar.setIndeterminate(false);
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastUpdateAt < 100 && done < total) return;
                    lastUpdateAt = now;
                    int pct = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
                    bar.setProgress(pct * 10);
                    text.setText(label + "  " + pct + "%");
                }
            });
        }

        /** 不确定进度：扫描动画 + 文案。 */
        void indeterminate(final String label) {
            handler.post(new Runnable() {
                @Override public void run() {
                    text.setText(label);
                    if (scan == null) {
                        scan = MinecraftUi.scanAnimation(bar);
                    }
                }
            });
        }

        void dismiss() {
            handler.post(new Runnable() {
                @Override public void run() {
                    if (dialog == null) return;
                    if (scan != null) {
                        scan.cancel();
                        scan = null;
                    }
                    try { dialog.dismiss(); } catch (Throwable ignored) { }
                }
            });
        }
    }

    private ProgressDialog() {
    }

    static Holder show(final Activity activity, final String title) {
        final Holder[] out = new Holder[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    final Dialog dialog = new Dialog(activity);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setCancelable(false);
                    LinearLayout wrap = new LinearLayout(activity);
                    wrap.setOrientation(LinearLayout.VERTICAL);
                    wrap.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
                    LinearLayout panel = MinecraftUi.panel(activity, title);
                    ProgressBar bar = MinecraftUi.themedProgress(activity);
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 10));
                    blp.topMargin = dp(activity, 10);
                    panel.addView(bar, blp);
                    TextView text = MinecraftUi.text(activity, I18n.t("准备中…"), 9,
                            MinecraftUi.PANEL_MUTED, false);
                    text.setPadding(0, dp(activity, 6), 0, 0);
                    panel.addView(text);
                    wrap.addView(panel);
                    dialog.setContentView(wrap);
                    Window w = dialog.getWindow();
                    if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    dialog.show();
                    Window dw = dialog.getWindow();
                    if (dw != null) {
                        dw.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f),
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                    out[0] = new Holder(dialog, bar, text);
                } catch (Throwable ignored) {
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
        if (out[0] == null) out[0] = new Holder(null, null, null);
        return out[0];
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
