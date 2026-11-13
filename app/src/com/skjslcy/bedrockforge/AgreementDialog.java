package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** 用户协议：首次启动同意 + 设置页查看。 */
final class AgreementDialog {

    static final String VERSION = "1.1";

    private AgreementDialog() {
    }

    static boolean accepted(Activity a) {
        String v = a.getSharedPreferences("legal", Activity.MODE_PRIVATE)
                .getString("agreement_version", "");
        return VERSION.equals(v);
    }

    static void show(final Activity a, final boolean consent, final Runnable onAgree) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 8));

        LinearLayout panel = MinecraftUi.panel(a, "用户协议");
        panel.addView(MinecraftUi.text(a,
                "版本 " + VERSION + " · 生效 2026-11-13"
                        + (accepted(a) && !consent ? " · 已同意当前版本" : ""),
                8.5f, MinecraftUi.PANEL_MUTED, false));

        final ScrollView sv = new ScrollView(a);
        final TextView body = MinecraftUi.text(a, "", 9.2f, MinecraftUi.PANEL_TEXT, false);
        body.setText(styled(a, readAsset(a)));
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(a, 3), 1.08f);
        body.setPadding(dp(a, 2), dp(a, 6), dp(a, 2), dp(a, 2));
        sv.addView(body);
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (consent) {
            final TextView hint = MinecraftUi.text(a, "请滑动阅读至底部后同意", 8.5f,
                    MinecraftUi.PANEL_MUTED, false);
            hint.setPadding(0, dp(a, 4), 0, dp(a, 2));
            wrap.addView(hint);

            final Button agree = new MinecraftUi.McButton(a, "同意并继续");
            agree.setEnabled(false);
            agree.setAlpha(0.5f);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44));
            wrap.addView(agree, alp);

            final Button exit = new MinecraftUi.McButton(a, "不同意，退出应用");
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 42));
            elp.topMargin = dp(a, 5);
            wrap.addView(exit, elp);

            final Runnable enable = () -> {
                agree.setEnabled(true);
                agree.setAlpha(1f);
                hint.setText("已阅读完毕，可以同意");
                hint.setTextColor(MinecraftUi.GREEN);
            };
            sv.setOnScrollChangeListener((v, sx, sy, osx, osy) -> {
                if (!sv.canScrollVertically(1)) enable.run();
            });
            sv.post(() -> {
                if (!sv.canScrollVertically(1)) enable.run();
            });

            agree.setOnClickListener(v -> {
                if (!agree.isEnabled()) return;
                a.getSharedPreferences("legal", Activity.MODE_PRIVATE).edit()
                        .putString("agreement_version", VERSION).apply();
                d.dismiss();
                if (onAgree != null) onAgree.run();
            });
            exit.setOnClickListener(v -> {
                d.dismiss();
                a.finishAffinity();
            });
            d.setCancelable(false);
        } else {
            Button close = new MinecraftUi.McButton(a, "关闭");
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 44));
            clp.topMargin = dp(a, 6);
            wrap.addView(close, clp);
            close.setOnClickListener(v -> d.dismiss());
        }

        d.setContentView(wrap);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (a.getResources().getDisplayMetrics().heightPixels * 0.88f));
        }
    }

    private static SpannableStringBuilder styled(Activity a, String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String raw : text.split("\n")) {
            String line = raw == null ? "" : raw;
            int start = sb.length();
            sb.append(line).append('\n');
            String t = line.trim();
            boolean heading = t.matches("^[一二三四五六七八九十]+、.*")
                    || t.startsWith("附录");
            if (heading) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, start + line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(1.12f), start, start + line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(0xFF2F5A22), start, start + line.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    private static String readAsset(Activity a) {
        try {
            InputStream in = a.getAssets().open("legal/user_agreement.txt");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable e) {
            return "用户协议读取失败：" + e.getMessage();
        }
    }

    private static int dp(Activity a, int v) {
        return MinecraftUi.dp(a, v);
    }
}
