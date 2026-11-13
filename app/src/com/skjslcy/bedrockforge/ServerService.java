package com.skjslcy.bedrockforge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class ServerService extends Service {

    public static final String ACTION_START = "com.skjslcy.bedrockforge.action.START";
    public static final String ACTION_STOP = "com.skjslcy.bedrockforge.action.STOP";
    public static final String ACTION_COMMAND = "com.skjslcy.bedrockforge.action.COMMAND";
    public static final String EXTRA_COMMAND = "command";

    private static final String CHANNEL_ID = "mcbds_server";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            ServerManager.get().stop();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_COMMAND.equals(action)) {
            String cmd = intent.getStringExtra(EXTRA_COMMAND);
            if (cmd != null) ServerManager.get().send(cmd);
            return START_STICKY;
        }

        // default: START
        startForegroundCompat(buildNotification("正在启动服务端…"));
        boolean ok = ServerManager.get().start(this);
        if (!ok) {
            updateNotification("启动失败：" + ServerManager.get().getLastError());
            stopForeground(true);
            stopSelf();
        } else {
            updateNotification("服务端运行中");
        }
        return START_NOT_STICKY;
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, ServerService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("MC BDS")
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPi).build());
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "服务器状态",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Minecraft Bedrock 服务端运行状态");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (ServerManager.get().isRunning()) {
            ServerManager.get().stop();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
