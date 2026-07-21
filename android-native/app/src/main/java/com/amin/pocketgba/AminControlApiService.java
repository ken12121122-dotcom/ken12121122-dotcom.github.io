package com.amin.pocketgba;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class AminControlApiService extends Service implements AminControlHttpServer.StateListener {
    public static final String ACTION_START = "com.amin.pocketgba.api.START";
    public static final String ACTION_STOP = "com.amin.pocketgba.api.STOP";
    public static final String ACTION_RELOAD = "com.amin.pocketgba.api.RELOAD";
    private static final String CHANNEL_ID = "amin_control_api";
    private static final int NOTIFICATION_ID = 4109;
    private static volatile boolean running;
    private static volatile String endpoint = "";
    private static volatile String lastError = "";
    private AminControlHttpServer server;

    public static void start(Context context) {
        Intent intent = new Intent(context, AminControlApiService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void reload(Context context) {
        Intent intent = new Intent(context, AminControlApiService.class).setAction(ACTION_RELOAD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, AminControlApiService.class));
    }

    public static boolean isRunning() { return running; }
    public static String getEndpoint() { return endpoint; }
    public static String getLastError() { return lastError; }

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        server = new AminControlHttpServer(this, this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServerAndSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification("啟動中…"));
        if (!new AminControlApiConfig(this).isApiEnabled()) {
            lastError = "API switch is off";
            stopServerAndSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RELOAD.equals(action) && server != null) {
            server.stop();
            server = null;
        }
        if (server == null) server = new AminControlHttpServer(this, this);
        server.start();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (server != null) server.stop();
        running = false;
        endpoint = "";
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onStarted(String activeEndpoint) {
        running = true;
        endpoint = activeEndpoint;
        lastError = "";
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(activeEndpoint));
    }

    @Override public void onStopped() { running = false; endpoint = ""; }

    @Override public void onError(String message) {
        running = false;
        lastError = message == null ? "Unknown API error" : message;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification("錯誤：" + lastError));
    }

    private void stopServerAndSelf() {
        if (server != null) server.stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification buildNotification(String status) {
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, new Intent(this, AminControlApiActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Amin Control API")
                .setContentText(status)
                .setContentIntent(pending)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Amin Control API", NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("顯示 localhost 或使用者主動開啟的 LAN 控制 API 狀態");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
