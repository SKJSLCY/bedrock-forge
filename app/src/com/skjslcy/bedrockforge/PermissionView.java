package com.skjslcy.bedrockforge;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/** 设置 → 权限管理：必要权限的状态与跳转。 */
public class PermissionView extends LinearLayout {

    private final Activity activity;
    private final TextView notificationStatus;
    private final TextView shizukuStatus;
    private final TextView batteryStatus;
    private final TextView storageStatus;
    private final TextView networkStatus;

    public PermissionView(Activity activity) {
        super(activity);
        this.activity = activity;
        setOrientation(VERTICAL);

        LinearLayout panel = MinecraftUi.panel(activity, "必要权限");
        addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView intro = MinecraftUi.text(activity,
                I18n.t("运行服务端需要 Shizuku；通知权限用于部署进度和前台服务。\n")
                        + I18n.t("AI 功能需要网络，INTERNET 权限在安装时已授予。"), 9,
                MinecraftUi.PANEL_MUTED, false);
        intro.setPadding(0, 0, 0, dp(6));
        panel.addView(intro);

        LinearLayout nRow = row(panel, I18n.t("通知权限"));
        notificationStatus = (TextView) nRow.getTag();
        Button nBtn = new MinecraftUi.McButton(activity, "打开通知设置");
        nRow.addView(nBtn, new LinearLayout.LayoutParams(dp(130), dp(40)));
        nBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 33
                        && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    try {
                        activity.requestPermissions(
                                new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 992);
                    } catch (Throwable t) {
                        openNotificationSettings();
                    }
                } else {
                    openNotificationSettings();
                }
            }
        });

        LinearLayout sRow = row(panel, "Shizuku");
        shizukuStatus = (TextView) sRow.getTag();
        Button sBtn = new MinecraftUi.McButton(activity, "启动指引");
        sRow.addView(sBtn, new LinearLayout.LayoutParams(dp(110), dp(40)));
        sBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { showShizukuGuide(activity); }
        });

        LinearLayout bRow = row(panel, I18n.t("电池优化"));
        batteryStatus = (TextView) bRow.getTag();
        Button bBtn = new MinecraftUi.McButton(activity, "忽略优化");
        bRow.addView(bBtn, new LinearLayout.LayoutParams(dp(110), dp(40)));
        bBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { requestBattery(); }
        });

        LinearLayout stRow = row(panel, I18n.t("存储权限"));
        storageStatus = (TextView) stRow.getTag();
        Button stBtn = new MinecraftUi.McButton(activity,
                Build.VERSION.SDK_INT >= 30 ? "系统设置" : "申请");
        stRow.addView(stBtn, new LinearLayout.LayoutParams(dp(104), dp(40)));
        stBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { requestStorage(); }
        });

        LinearLayout netRow = row(panel, I18n.t("网络"));
        networkStatus = (TextView) netRow.getTag();
        Button nRefresh = new MinecraftUi.McButton(activity, "刷新");
        netRow.addView(nRefresh, new LinearLayout.LayoutParams(dp(76), dp(40)));
        nRefresh.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        Button nSet = new MinecraftUi.McButton(activity, "网络设置");
        LinearLayout.LayoutParams nslp = new LinearLayout.LayoutParams(dp(96), dp(40));
        nslp.leftMargin = dp(4);
        netRow.addView(nSet, nslp);
        nSet.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { openNetworkSettings(); }
        });

        refresh();
    }

    private void openNetworkSettings() {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Throwable t) {
            try {
                activity.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t2) {
                toast(I18n.t("无法打开网络设置"));
            }
        }
    }

    public void refresh() {
        boolean notify = true;
        if (Build.VERSION.SDK_INT >= 24) {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) activity.getSystemService(Activity.NOTIFICATION_SERVICE);
            notify = nm.areNotificationsEnabled();
        }
        notificationStatus.setText(notify ? I18n.t("已允许") : I18n.t("未允许"));
        notificationStatus.setTextColor(notify ? MinecraftUi.GREEN : MinecraftUi.RED);

        boolean ping = false, granted = false;
        try {
            ping = Shizuku.pingBinder();
            granted = ping && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
        }
        shizukuStatus.setText(!ping ? I18n.t("未运行") : (granted ? I18n.t("已连接并授权") : I18n.t("已连接，待授权")));
        shizukuStatus.setTextColor(granted ? MinecraftUi.GREEN : MinecraftUi.RED);

        boolean battery = true;
        try {
            PowerManager pm = (PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
            battery = pm.isIgnoringBatteryOptimizations(activity.getPackageName());
        } catch (Throwable ignored) {
        }
        batteryStatus.setText(battery ? I18n.t("已忽略") : I18n.t("未忽略"));
        batteryStatus.setTextColor(battery ? MinecraftUi.GREEN : MinecraftUi.AMBER);

        boolean storage;
        if (Build.VERSION.SDK_INT >= 30) {
            storage = true;
            storageStatus.setText(I18n.t("无需（分区存储）"));
        } else {
            storage = activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            storageStatus.setText(storage ? I18n.t("已允许") : I18n.t("未允许"));
        }
        storageStatus.setTextColor(storage ? MinecraftUi.GREEN : MinecraftUi.RED);

        boolean connected = false;
        boolean wifi = false;
        boolean cellular = false;
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) activity.getSystemService(Activity.CONNECTIVITY_SERVICE);
            android.net.Network n = cm.getActiveNetwork();
            android.net.NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            if (caps != null) {
                connected = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
                wifi = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
                cellular = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
            }
        } catch (Throwable ignored) {
        }
        if (!connected) {
            networkStatus.setText(I18n.t("未连接"));
            networkStatus.setTextColor(MinecraftUi.RED);
        } else {
            networkStatus.setText(wifi ? I18n.t("Wi‑Fi 已连接") : (cellular ? I18n.t("移动数据已连接") : I18n.t("已连接")));
            networkStatus.setTextColor(MinecraftUi.GREEN);
        }

    }


    private LinearLayout row(LinearLayout panel, String label) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(MinecraftUi.slotDrawable(activity));
        row.setPadding(dp(9), dp(7), dp(9), dp(7));

        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(VERTICAL);
        left.addView(MinecraftUi.text(activity, label, 11, MinecraftUi.PANEL_TEXT, true));
        TextView st = MinecraftUi.text(activity, "", 9, MinecraftUi.PANEL_MUTED, false);
        st.setPadding(0, dp(2), 0, 0);
        left.addView(st);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setTag(st);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        panel.addView(row, lp);
        return row;
    }

    private void openNotificationSettings() {
        if (!showNotificationSettings(activity)) {
            toast(I18n.t("无法打开通知设置，请手动进入系统设置"));
        }
    }

    /** 依次尝试多种系统入口，返回是否成功启动。 */
    public static boolean showNotificationSettings(Activity a) {
        String pkg = a.getPackageName();
        if (safeStart(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                .putExtra(Settings.EXTRA_CHANNEL_ID, pkg))) return true;
        if (safeStart(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra("app_package", pkg)
                .putExtra("app_uid", a.getApplicationInfo().uid))) return true;
        if (safeStart(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + pkg)))) return true;
        if (safeStart(a, new Intent("android.settings.NOTIFICATION_SETTINGS"))) return true;
        if (safeStart(a, new Intent(Settings.ACTION_SETTINGS))) return true;
        return false;
    }

    private static boolean safeStart(Activity a, Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void requestBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
        } catch (Throwable t) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable t2) {
                toast(I18n.t("无法打开电池优化设置"));
            }
        }
    }

    private void requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+ 分区存储下 App 不需要 WRITE_EXTERNAL_STORAGE；
            // 去系统应用信息页，用户可自行查看/清理存储。
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(i);
            } catch (Throwable t) {
                toast(I18n.t("无法打开应用设置：") + t.getMessage());
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 21);
        }
    }

    public static void showShizukuGuide(final Activity activity) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        LinearLayout panel = MinecraftUi.panel(activity, "Shizuku 启动指引");
        wrap.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String steps = I18n.t("1. 安装 Shizuku\n")
                + I18n.t("2. 打开开发者选项 → 无线调试\n")
                + I18n.t("3. 在 Shizuku 内配对并启动服务\n")
                + I18n.t("4. 回到本应用，允许 Shizuku 授权\n\n")
                + I18n.t("Shizuku 只负责以 shell 身份启动 Box64；不需要电脑长期连接。");
        TextView t = MinecraftUi.text(activity, steps, 10, MinecraftUi.PANEL_TEXT, false);
        t.setPadding(0, dp(activity, 2), 0, dp(activity, 8));
        panel.addView(t);

        Button open = new MinecraftUi.McButton(activity, "打开 Shizuku");
        panel.addView(open, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)));
        open.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = activity.getPackageManager()
                            .getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (i == null) {
                        Toast.makeText(activity, I18n.t("未安装 Shizuku，请先安装"), Toast.LENGTH_SHORT).show();
                    } else {
                        activity.startActivity(i);
                    }
                } catch (Throwable e) {
                    Toast.makeText(activity, I18n.t("无法打开 Shizuku：") + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button close = new MinecraftUi.McButton(activity, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
        clp.topMargin = dp(activity, 5);
        panel.addView(close, clp);
        close.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        dialog.setContentView(wrap);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            w.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static int dp(Activity a, float v) {
        return MinecraftUi.dp(a, v);
    }

    private int dp(float v) {
        return MinecraftUi.dp(activity, v);
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }
}
