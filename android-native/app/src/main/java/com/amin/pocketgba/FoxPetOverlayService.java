package com.amin.pocketgba;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Random;

/** Existing desktop-pet overlay used only as a presentation for FloatingVoiceController. */
public final class FoxPetOverlayService extends Service {
    public static final String ACTION_START = "com.amin.pocketgba.foxpet.START";
    public static final String ACTION_STOP = "com.amin.pocketgba.foxpet.STOP";
    public static final String ACTION_UPDATE = "com.amin.pocketgba.foxpet.UPDATE";
    public static final String EXTRA_VISUAL_STATE = "visual_state";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_SPEAK = "speak";
    private static final int NOTIFICATION_ID = 2206;
    private static final String CHANNEL_ID = "amin_fox_pet";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private WindowManager windowManager;
    private ImageView foxView;
    private TextView chatBubble;
    private WindowManager.LayoutParams foxParams, chatParams;
    private TextToSpeech tts;
    private boolean dragging;
    private float downRawX, downRawY;
    private int startX, startY, moveToken;
    private FoxPresentationBridge.VisualState visualState = FoxPresentationBridge.VisualState.ACTIVE;

    private final Runnable hideChat = () -> { if (chatBubble != null) chatBubble.setVisibility(View.GONE); };
    private final Runnable idleFade = () -> {
        if (foxView != null && visualState == FoxPresentationBridge.VisualState.ACTIVE) foxView.setAlpha(0.62f);
    };
    private final Runnable settleActive = () -> {
        visualState = FoxPresentationBridge.VisualState.ACTIVE;
        applyVisualState(); scheduleWander();
    };
    private final Runnable randomWander = new Runnable() {
        @Override public void run() {
            if (foxView == null || dragging || visualState != FoxPresentationBridge.VisualState.ACTIVE) {
                scheduleWander(); return;
            }
            int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(96));
            int maxY = Math.max(dp(40), getResources().getDisplayMetrics().heightPixels - dp(190));
            animateMoveTo(clamp(foxParams.x + (random.nextBoolean() ? dp(110) : -dp(110)), 0, maxX),
                    clamp(foxParams.y + random.nextInt(dp(121)) - dp(60), dp(40), maxY));
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) tts.setLanguage(Locale.TAIWAN);
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)
                || !FoxPetPreferences.MODE_FOX.equals(FoxPetPreferences.getDisplayMode(this))) {
            stopSelf(); return START_NOT_STICKY;
        }
        showFoxIfNeeded();
        if (ACTION_UPDATE.equals(action)) updatePresentation(intent);
        return START_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null); moveToken++;
        removeView(chatBubble); removeView(foxView);
        chatBubble = null; foxView = null; chatParams = null; foxParams = null;
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        super.onDestroy();
    }

    private void showFoxIfNeeded() {
        if (foxView != null || windowManager == null) return;
        foxView = new ImageView(this);
        foxView.setImageResource(R.drawable.fox_pet_sit_right);
        foxView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        foxView.setContentDescription("桌面狐狸 · 點一下開始聆聽");
        foxParams = overlayParams(dp(96), dp(96));
        foxParams.x = Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(120));
        foxParams.y = getResources().getDisplayMetrics().heightPixels / 2;
        foxView.setOnTouchListener((view, event) -> handleTouch(view, event));
        windowManager.addView(foxView, foxParams);

        chatBubble = new TextView(this);
        chatBubble.setTextColor(Color.rgb(22, 35, 27)); chatBubble.setTextSize(13f);
        chatBubble.setPadding(dp(12), dp(8), dp(12), dp(8)); chatBubble.setMaxWidth(dp(280));
        chatBubble.setBackground(roundRect(0xf5ffffff, 0x55324a3c));
        chatBubble.setVisibility(View.GONE);
        chatParams = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        chatParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        syncChatPosition(); windowManager.addView(chatBubble, chatParams);
        applyVisualState(); scheduleWander();
    }

    private void updatePresentation(Intent intent) {
        try { visualState = FoxPresentationBridge.VisualState.valueOf(intent.getStringExtra(EXTRA_VISUAL_STATE)); }
        catch (Exception ignored) { visualState = FoxPresentationBridge.VisualState.ACTIVE; }
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        applyVisualState();
        handler.removeCallbacks(settleActive);
        if (visualState == FoxPresentationBridge.VisualState.TALKING) {
            handler.postDelayed(settleActive, 12000L);
        }
        if (message != null && !message.trim().isEmpty() && FoxPetPreferences.isChatBubbleEnabled(this)) {
            chatBubble.setText(message.trim()); chatBubble.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideChat);
            if (visualState != FoxPresentationBridge.VisualState.SITTING) handler.postDelayed(hideChat, 12000L);
        } else if (visualState != FoxPresentationBridge.VisualState.SITTING) chatBubble.setVisibility(View.GONE);
        if (intent.getBooleanExtra(EXTRA_SPEAK, false) && FoxPetPreferences.isAutoSpeakEnabled(this)) speak(message);
    }

    private void applyVisualState() {
        if (foxView == null) return;
        handler.removeCallbacks(idleFade);
        float alpha = visualState == FoxPresentationBridge.VisualState.SLEEPING ? 0.38f : 1f;
        float scale = visualState == FoxPresentationBridge.VisualState.THINKING ? 1.08f
                : visualState == FoxPresentationBridge.VisualState.SITTING ? 0.92f : 1f;
        float rotation = visualState == FoxPresentationBridge.VisualState.LISTENING ? -5f
                : visualState == FoxPresentationBridge.VisualState.TALKING ? 5f : 0f;
        foxView.animate().alpha(alpha).scaleX(scale).scaleY(scale).rotation(rotation).setDuration(180L).start();
        foxView.setContentDescription("桌面狐狸 · " + visualState.name() + " · 點一下開始聆聽");
        if (visualState == FoxPresentationBridge.VisualState.ACTIVE) handler.postDelayed(idleFade, 3000L);
    }

    private boolean handleTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moveToken++; handler.removeCallbacks(randomWander); handler.removeCallbacks(idleFade); dragging = false;
                foxView.setAlpha(1f);
                downRawX = event.getRawX(); downRawY = event.getRawY();
                startX = foxParams.x; startY = foxParams.y; return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX, dy = event.getRawY() - downRawY;
                if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) dragging = true;
                if (dragging && FoxPetPreferences.isDraggable(this)) {
                    foxParams.x = startX + Math.round(dx); foxParams.y = startY + Math.round(dy);
                    updateView(foxView, foxParams); syncChatPosition(); updateView(chatBubble, chatParams);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) { view.performClick(); FoxPresentationBridge.requestListening(); }
                dragging = false; scheduleWander(); return true;
            case MotionEvent.ACTION_CANCEL: dragging = false; scheduleWander(); return true;
            default: return true;
        }
    }

    private void speak(String message) {
        if (tts == null || message == null || message.trim().isEmpty()) return;
        tts.setSpeechRate(FoxPetPreferences.getSpeechRate(this)); tts.setPitch(FoxPetPreferences.getPitch(this));
        Bundle params = new Bundle(); params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,
                FoxPetPreferences.getVolume(this));
        tts.speak(message.trim(), TextToSpeech.QUEUE_FLUSH, params, "fox-assistant-reply");
    }

    private void animateMoveTo(int targetX, int targetY) {
        final int token = ++moveToken, fromX = foxParams.x, fromY = foxParams.y;
        handler.post(new Runnable() {
            int step;
            @Override public void run() {
                if (token != moveToken || foxView == null) return;
                float t = Math.min(1f, (++step) / 24f);
                foxParams.x = Math.round(fromX + (targetX - fromX) * t);
                foxParams.y = Math.round(fromY + (targetY - fromY) * t);
                updateView(foxView, foxParams); syncChatPosition(); updateView(chatBubble, chatParams);
                if (t < 1f) handler.postDelayed(this, 50L); else scheduleWander();
            }
        });
    }

    private void scheduleWander() {
        handler.removeCallbacks(randomWander);
        handler.postDelayed(randomWander, 6500L + random.nextInt(5001));
    }

    private void syncChatPosition() {
        if (chatParams == null || foxParams == null) return;
        chatParams.x = Math.max(0, foxParams.x - dp(190)); chatParams.y = Math.max(dp(20), foxParams.y - dp(72));
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START; return params;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "桌面狐狸", NotificationManager.IMPORTANCE_LOW));
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, FoxPetControlActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setContentTitle("桌面狐狸已開啟").setContentText("點狐狸會使用既有語音與聊天 Runtime")
                .setSmallIcon(android.R.drawable.ic_menu_compass).setContentIntent(open).setOngoing(true).build();
    }

    private GradientDrawable roundRect(int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill);
        shape.setCornerRadius(dp(14)); shape.setStroke(dp(1), stroke); return shape;
    }

    private void updateView(View view, WindowManager.LayoutParams params) {
        if (view == null || params == null || windowManager == null) return;
        try { windowManager.updateViewLayout(view, params); } catch (RuntimeException ignored) { }
    }
    private void removeView(View view) {
        if (view == null || windowManager == null) return;
        try { windowManager.removeView(view); } catch (RuntimeException ignored) { }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
