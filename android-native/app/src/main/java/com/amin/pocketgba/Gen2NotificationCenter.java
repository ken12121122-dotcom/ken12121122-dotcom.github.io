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

final class Gen2NotificationCenter {
    static final String CHANNEL_ID = "amin_gen2_runs";
    private Gen2NotificationCenter() { }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "GEN2 待核准", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("GEN2 流程跑到需要你核准或提供資料的步驟時通知。 ");
        manager.createNotificationChannel(channel);
    }

    static void notify(Context context, Gen2RunView run) {
        if (run == null || run.pending == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        ensureChannel(context);
        Intent intent = new Intent(context, Gen2RunsActivity.class)
                .putExtra(Gen2RunsActivity.EXTRA_ISSUE, run.number)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, notificationId(run.number), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = "GEN2 · " + run.statusText();
        String text = run.workflowId + "｜" + run.pending.title;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text
                        + (run.pending.reviewText.isEmpty() ? "" : "\n\n" + preview(run.pending.reviewText))))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(notificationId(run.number), builder.build());
    }

    static void cancel(Context context, int issueNumber) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId(issueNumber));
    }

    static int notificationId(int issueNumber) {
        return ("gen2-run#" + issueNumber).hashCode();
    }

    private static String preview(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
