package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/** Shizuku 运行状态检查与启动引导。 */
public final class ShizukuGate {

    private ShizukuGate() {
    }

    public static boolean isRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isAuthorized() {
        try {
            return isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 需要 Shizuku 的操作入口：未运行则弹引导；已运行未授权则请求授权。
     * 返回 true 表示现在可以继续执行。
     */
    public static boolean ensure(Activity activity, String feature) {
        if (!isRunning()) {
            showDialog(activity, feature, null);
            return false;
        }
        if (!isAuthorized()) {
            try {
                Shizuku.requestPermission(0);
                Toast.makeText(activity, I18n.t("请在 Shizuku 弹窗中允许本应用"), Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(activity, I18n.t("Shizuku 授权失败：") + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    /** 带动作的入口：用户点击“重试”且 Shizuku 已就绪后自动继续。 */
    public static void require(final Activity activity, final String feature, final Runnable action) {
        if (ensure(activity, feature)) {
            action.run();
        } else if (isAuthorized()) {
            action.run();
        }
    }

    public static void showDialog(final Activity activity, String feature, final Runnable retryAction) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));

        LinearLayout panel = MinecraftUi.panel(activity, "需要 Shizuku");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String text = "“" + feature + I18n.t("”需要 Shizuku 以 shell 身份执行。\n\n")
                + I18n.t("当前没有检测到 Shizuku 服务。请：\n")
                + I18n.t("1. 打开 Shizuku\n")
                + I18n.t("2. 开发者选项 → 无线调试 → 配对并启动服务\n")
                + I18n.t("3. 回到本应用点“重试”并允许授权\n\n")
                + I18n.t("Shizuku 只负责部署文件和启动 Box64，不需要 Root。");
        TextView body = MinecraftUi.text(activity, text, 10, MinecraftUi.PANEL_TEXT, false);
        body.setPadding(0, dp(activity, 2), 0, dp(activity, 8));
        panel.addView(body);

        Button open = new MinecraftUi.McButton(activity, "打开 Shizuku");
        panel.addView(open, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)));
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = activity.getPackageManager()
                            .getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (i == null) {
                        Toast.makeText(activity, I18n.t("未安装 Shizuku，请先安装"), Toast.LENGTH_SHORT).show();
                    } else {
                        activity.startActivity(i);
                    }
                } catch (Throwable t) {
                    Toast.makeText(activity, I18n.t("无法打开 Shizuku：") + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button retry = new MinecraftUi.McButton(activity, "重试");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
        rlp.topMargin = dp(activity, 5);
        panel.addView(retry, rlp);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!isRunning()) {
                    Toast.makeText(activity, I18n.t("仍未检测到 Shizuku 服务"), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isAuthorized()) {
                    try {
                        Shizuku.requestPermission(0);
                        Toast.makeText(activity, I18n.t("请在 Shizuku 弹窗中允许本应用"), Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Toast.makeText(activity, I18n.t("授权失败：") + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                dialog.dismiss();
                if (retryAction != null) retryAction.run();
            }
        });

        Button guide = new MinecraftUi.McButton(activity, "查看启动指引");
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
        glp.topMargin = dp(activity, 5);
        panel.addView(guide, glp);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                PermissionView.showShizukuGuide(activity);
            }
        });

        Button cancel = new MinecraftUi.McButton(activity, "取消");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
        clp.topMargin = dp(activity, 5);
        panel.addView(cancel, clp);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static int dp(Activity a, float v) {
        return MinecraftUi.dp(a, v);
    }
}
