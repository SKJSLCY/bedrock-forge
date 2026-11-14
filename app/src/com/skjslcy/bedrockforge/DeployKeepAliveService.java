package com.skjslcy.bedrockforge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** 部署期间的前台保活服务：锁屏/切后台继续下载，并在通知栏显示进度。 */
public class DeployKeepAliveService extends Service {

    private static final String CHANNEL = "deploy_progress";
    private static final int ID = 9001;

    public static void start(Context c, String label) {
        try {
            Intent i = new Intent(c, DeployKeepAliveService.class).putExtra("label", label);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {
        }
    }

    public static void progress(Context c, int percent, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            nm.notify(ID, build(c, I18n.t("部署中 ") + percent + "%", text, percent));
        } catch (Throwable ignored) {
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, DeployKeepAliveService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, I18n.t("部署进度"),
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String label = intent == null ? I18n.t("部署") : intent.getStringExtra("label");
        startForeground(ID, build(this, I18n.t("部署进行中"), label == null ? "" : label, 0));
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    private static Notification build(Context c, String title, String text, int progress) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }
        b.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true);
        if (progress > 0) b.setProgress(100, progress, false);
        else b.setProgress(0, 0, true);
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
