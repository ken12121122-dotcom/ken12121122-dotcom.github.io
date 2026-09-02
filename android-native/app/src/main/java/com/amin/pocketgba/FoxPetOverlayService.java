package com.amin.pocketgba;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.util.Random;

public final class FoxPetOverlayService extends Service {
    public static final String ACTION_START = "com.amin.pocketgba.foxpet.START";
    public static final String ACTION_STOP = "com.amin.pocketgba.foxpet.STOP";

    private static final int NOTIFICATION_ID = 2206;
    private static final String CHANNEL_ID = "amin_fox_pet";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private WindowManager windowManager;
    private ImageView foxView;
    private WindowManager.LayoutParams foxParams;
    private boolean dragging;
    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private int moveToken;

    private final Runnable idleFadeRunnable = () -> {
        if (foxView != null) foxView.setAlpha(0.45f);
    };

    private final Runnable randomWanderRunnable = new Runnable() {
        @Override
        public void run() {
            if (foxView == null || foxParams == null || dragging) {
                scheduleRandomWander();
                return;
            }

            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int foxSize = dp(96);
            int maxX = Math.max(0, screenW - foxSize);
            int minY = dp(40);
            int maxY = Math.max(minY, screenH - foxSize - dp(80));

            int stepX = dp(80) + random.nextInt(Math.max(1, dp(180)));
            int stepY = random.nextInt(Math.max(1, dp(140)));
            int targetX = clamp(foxParams.x + (random.nextBoolean() ? stepX : -stepX), 0, maxX);
            int targetY = clamp(foxParams.y + (random.nextBoolean() ? stepY : -stepY), minY, maxY);
            animateMoveTo(targetX, targetY);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            FoxPetState.setEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        FoxPetState.setEnabled(this, true);
        showFoxIfNeeded();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(idleFadeRunnable);
        handler.removeCallbacks(randomWanderRunnable);
        moveToken++;
        if (foxView != null) {
            try {
                windowManager.removeView(foxView);
            } catch (RuntimeException ignored) {
            }
        }
        foxView = null;
        foxParams = null;
        FoxPetState.setEnabled(this, false);
        super.onDestroy();
    }

    private void showFoxIfNeeded() {
        if (foxView != null) return;

        int size = dp(96);
        ImageView fox = new ImageView(this);
        fox.setImageResource(R.drawable.fox_pet_sit_right);
        fox.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fox.setAdjustViewBounds(true);
        fox.setAlpha(1f);
        fox.setBackground(null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size,
                size,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(120));
        params.y = getResources().getDisplayMetrics().heightPixels / 2;

        fox.setOnTouchListener((view, event) -> handleTouch(event));
        windowManager.addView(fox, params);
        foxView = fox;
        foxParams = params;
        scheduleIdleFade();
        scheduleRandomWander();
    }

    private boolean handleTouch(MotionEvent event) {
        if (foxView == null || foxParams == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moveToken++;
                handler.removeCallbacks(randomWanderRunnable);
                handler.removeCallbacks(idleFadeRunnable);
                foxView.setAlpha(1f);
                dragging = false;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = foxParams.x;
                startY = foxParams.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) dragging = true;
                if (dragging) {
                    foxParams.x = startX + Math.round(dx);
                    foxParams.y = startY + Math.round(dy);
                    try {
                        windowManager.updateViewLayout(foxView, foxParams);
                    } catch (RuntimeException ignored) {
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                scheduleIdleFade();
                scheduleRandomWander();
                return true;
            default:
                return true;
        }
    }

    private void animateMoveTo(int targetX, int targetY) {
        if (foxView == null || foxParams == null) return;
        final int token = ++moveToken;
        final int fromX = foxParams.x;
        final int fromY = foxParams.y;
        final int steps = 32;
        final long frameDelay = 55L;

        Runnable mover = new Runnable() {
            int step;

            @Override
            public void run() {
                if (token != moveToken || foxView == null || foxParams == null) return;
                if (step >= steps) {
                    foxParams.x = targetX;
                    foxParams.y = targetY;
                    try {
                        windowManager.updateViewLayout(foxView, foxParams);
                    } catch (RuntimeException ignored) {
                    }
                    scheduleIdleFade();
                    scheduleRandomWander();
                    return;
                }
                float t = (step + 1f) / steps;
                foxParams.x = Math.round(fromX + (targetX - fromX) * t);
                foxParams.y = Math.round(fromY + (targetY - fromY) * t);
                try {
                    windowManager.updateViewLayout(foxView, foxParams);
                } catch (RuntimeException ignored) {
                }
                step++;
                handler.postDelayed(this, frameDelay);
            }
        };
        handler.post(mover);
    }

    private void scheduleIdleFade() {
        handler.removeCallbacks(idleFadeRunnable);
        handler.postDelayed(idleFadeRunnable, 3000L);
    }

    private void scheduleRandomWander() {
        handler.removeCallbacks(randomWanderRunnable);
        handler.postDelayed(randomWanderRunnable, 5000L + random.nextInt(6001));
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        //noinspection deprecation
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "桌面狐狸",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, ControlCenterActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("桌面狐狸已開啟")
                .setContentText("可拖曳；閒置時會在桌面隨機走動")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
