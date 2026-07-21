package com.amin.pocketgba;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class VoiceCommandActivityLauncher {
    static final String EXTRA_LAUNCH_TOKEN =
            "com.amin.pocketgba.extra.VOICE_LAUNCH_TOKEN";
    static final String EXTRA_ENTRY_POINT =
            "com.amin.pocketgba.extra.VOICE_ENTRY_POINT";
    static final String ENTRY_FLOATING_BUBBLE = "floating_bubble";

    private static final String TAG = "AminVoiceLauncher";
    private static final int REQUEST_CODE_FLOATING_BUBBLE = 6410;
    private static final AtomicReference<String> LAST_ACKNOWLEDGED_TOKEN =
            new AtomicReference<>(null);

    static final class LaunchResult {
        private final boolean success;
        private final String token;
        private final String message;

        private LaunchResult(boolean success, String token, String message) {
            this.success = success;
            this.token = token;
            this.message = message;
        }

        boolean isSuccess() {
            return success;
        }

        String getToken() {
            return token;
        }

        String getMessage() {
            return message;
        }
    }

    private VoiceCommandActivityLauncher() { }

    static LaunchResult openFromFloatingBubble(Context context) {
        if (context == null) {
            return new LaunchResult(false, null, "無法開啟語音指令：缺少系統環境");
        }

        String token = UUID.randomUUID().toString();
        Intent intent = createVoiceIntent(context, token);
        int flags = PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions creatorOptions = ActivityOptions.makeBasic();
                creatorOptions.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                );
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        REQUEST_CODE_FLOATING_BUBBLE,
                        intent,
                        flags,
                        creatorOptions.toBundle()
                );

                ActivityOptions senderOptions = ActivityOptions.makeBasic();
                senderOptions.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                );
                pendingIntent.send(senderOptions.toBundle());
            } else {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        REQUEST_CODE_FLOATING_BUBBLE,
                        intent,
                        flags
                );
                pendingIntent.send();
            }
            return new LaunchResult(true, token, "正在開啟 Amin 語音指令");
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            Log.e(TAG, "Unable to open voice activity from floating bubble", error);
            return new LaunchResult(false, token, "無法從浮動按鈕開啟語音指令");
        }
    }

    static Intent createVoiceIntent(Context context, String token) {
        Intent intent = new Intent(context, VoiceCommandActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        intent.putExtra(EXTRA_ENTRY_POINT, ENTRY_FLOATING_BUBBLE);
        intent.putExtra(EXTRA_LAUNCH_TOKEN, token);
        return intent;
    }

    static void acknowledgeLaunch(Intent intent) {
        if (intent == null) return;
        String token = intent.getStringExtra(EXTRA_LAUNCH_TOKEN);
        if (token != null && !token.isEmpty()) {
            LAST_ACKNOWLEDGED_TOKEN.set(token);
        }
    }

    static boolean wasAcknowledged(String token) {
        return token != null && token.equals(LAST_ACKNOWLEDGED_TOKEN.get());
    }
}
