package com.amin.pocketgba;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public final class PackageInstallResultReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_STATUS = "com.amin.pocketgba.action.INSTALL_STATUS";
    public static final String EXTRA_SHOW_UPDATE_RECEIPT = "show_update_receipt";
    public static final String RECEIPT_PREFS = "amin_update_receipt";
    public static final String KEY_INSTALL_SUCCESS = "install_success";
    private static final String CHANNEL_ID = "amin_native_updates";
    private static final int NOTIFICATION_ID = 7301;

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            else confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            context.getSharedPreferences(RECEIPT_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_INSTALL_SUCCESS, true).apply();
            Toast.makeText(context, "Amin 更新已安裝完成", Toast.LENGTH_LONG).show();
            postNotification(context);
            Intent launch = new Intent(context, LaunchGateActivity.class);
            launch.putExtra(EXTRA_SHOW_UPDATE_RECEIPT, true);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { context.startActivity(launch); } catch (RuntimeException ignored) {}
            return;
        }
        context.getSharedPreferences(RECEIPT_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_INSTALL_SUCCESS, false).apply();
        String detail = message == null || message.trim().isEmpty() ? "Android 回報安裝失敗，請重新檢查更新。" : message;
        Toast.makeText(context, "更新失敗：" + detail, Toast.LENGTH_LONG).show();
        Intent reopen = new Intent(context, NativeUpdateActivity.class);
        reopen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        reopen.putExtra("install_error", detail);
        context.startActivity(reopen);
    }

    private void postNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Amin 版本更新", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(context, LaunchGateActivity.class);
        intent.putExtra(EXTRA_SHOW_UPDATE_RECEIPT, true);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Amin 更新完成")
                .setContentText("新版已安裝。點擊查看更新內容並進入遊戲。")
                .setContentIntent(pending)
                .setAutoCancel(true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
