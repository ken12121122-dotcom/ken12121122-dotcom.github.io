package com.amin.pocketgba;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

final class BrainNotificationCenter {
    static final String CHANNEL_ID = "amin_brain_results";
    private BrainNotificationCenter() { }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "AMIN Brain 任務", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("顯示需要你處理、已完成或失敗的私人 Agent 任務。 ");
        manager.createNotificationChannel(channel);
    }

    static void notify(Context context, BrainFeedState.Notification value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        ensureChannel(context);
        Intent intent = new Intent(context, BrainControlActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, value.id().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String status = displayStatus(value.lifecycleStatus());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("AMIN Brain · " + status)
                .setContentText(value.title())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(value.title()
                        + "\n狀態：" + status))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(value.id().hashCode(), builder.build());
    }

    private static String displayStatus(String status) {
        if ("waiting_owner".equals(status)) return "等待你處理";
        if ("completed".equals(status)) return "已完成";
        if ("failed".equals(status)) return "執行失敗";
        return status;
    }
}
