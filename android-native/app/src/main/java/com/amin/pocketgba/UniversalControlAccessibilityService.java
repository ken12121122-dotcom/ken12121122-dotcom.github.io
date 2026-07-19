package com.amin.pocketgba;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class UniversalControlAccessibilityService extends AccessibilityService {
    private static final String PREFS = "amin_universal_control";
    private static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    private static final String KEY_AUTO_HIDE_SECONDS = "auto_hide_seconds";
    private static final String KEY_BUBBLE_X = "bubble_x";
    private static final String KEY_BUBBLE_Y = "bubble_y";

    private static final long LONG_PRESS_MS = 520L;
    private static final long TAP_DURATION_MS = 70L;
    private static final long HOLD_DURATION_MS = 720L;
    private static final long SWIPE_DURATION_MS = 320L;
    private static final long BUBBLE_FADE_DELAY_MS = 2000L;
    private static final int CURSOR_STEP_DP = 48;
    private static final float BUBBLE_IDLE_ALPHA = 0.25f;

    private static volatile UniversalControlAccessibilityService activeInstance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private TextView bubbleOverlay;
    private WindowManager.LayoutParams bubbleParams;
    private LinearLayout controlOverlay;
    private WindowManager.LayoutParams controlParams;
    private View cursorOverlay;
    private WindowManager.LayoutParams cursorParams;

    private int screenWidth;
    private int screenHeight;
    private float cursorX;
    private float cursorY;
    private boolean panelVisible;

    private final Runnable fadeBubbleTask = () -> {
        if (bubbleOverlay != null) {
            bubbleOverlay.animate()
                    .alpha(BUBBLE_IDLE_ALPHA)
                    .setDuration(260L)
                    .start();
        }
    };

    private final Runnable autoHidePanelTask = this::hidePanel;

    public static boolean isOverlayEnabled(Context context) {
        return preferences(context).getBoolean(KEY_OVERLAY_ENABLED, true);
    }

    public static void setOverlayEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply();
        UniversalControlAccessibilityService instance = activeInstance;
        if (instance != null) {
            instance.mainHandler.post(() -> instance.applyOverlayEnabled(enabled));
        }
    }

    public static int getAutoHideSeconds(Context context) {
        return preferences(context).getInt(KEY_AUTO_HIDE_SECONDS, 10);
    }

    public static void setAutoHideSeconds(Context context, int seconds) {
        int safeSeconds = seconds == 0 ? 0 : Math.max(5, Math.min(60, seconds));
        preferences(context).edit().putInt(KEY_AUTO_HIDE_SECONDS, safeSeconds).apply();
        UniversalControlAccessibilityService instance = activeInstance;
        if (instance != null) {
            instance.mainHandler.post(instance::schedulePanelAutoHide);
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeInstance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        refreshScreenBounds();
        cursorX = screenWidth * 0.5f;
        cursorY = screenHeight * 0.5f;
        applyOverlayEnabled(isOverlayEnabled(this));
        Toast.makeText(this, "全域控制服務已連線", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // v0.2 does not inspect other apps or read their content.
    }

    @Override
    public void onInterrupt() {
        // There is no spoken or haptic feedback to interrupt.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshScreenBounds();
        cursorX = clamp(cursorX, dp(14), screenWidth - dp(14));
        cursorY = clamp(cursorY, dp(14), screenHeight - dp(14));
        updateCursorPosition();
        clampAndUpdateBubble();
    }

    @Override
    public void onDestroy() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        removeOverlays();
        super.onDestroy();
    }

    private void applyOverlayEnabled(boolean enabled) {
        if (!enabled) {
            removeOverlays();
            return;
        }
        showBubble();
        hidePanel();
    }

    private void showBubble() {
        if (windowManager == null || bubbleOverlay != null) {
            return;
        }

        bubbleOverlay = new TextView(this);
        bubbleOverlay.setText("☰");
        bubbleOverlay.setTextColor(Color.WHITE);
        bubbleOverlay.setTextSize(26f);
        bubbleOverlay.setGravity(Gravity.CENTER);
        bubbleOverlay.setContentDescription("全域控制喚醒球，點擊展開或收起，拖曳可移動");
        bubbleOverlay.setBackground(circleBackground(0xe6192a20, Color.WHITE, 1));
        bubbleOverlay.setElevation(dp(8));

        int size = dp(56);
        bubbleParams = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = preferences(this).getInt(KEY_BUBBLE_X, Math.max(0, screenWidth - size - dp(8)));
        bubbleParams.y = preferences(this).getInt(KEY_BUBBLE_Y, Math.max(dp(72), screenHeight / 3));
        bubbleParams.setTitle("Amin Universal Control Wake Bubble");
        clampBubbleCoordinates();

        bubbleOverlay.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int downWindowX;
            private int downWindowY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        wakeBubble();
                        markPanelActivity();
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downWindowX = bubbleParams.x;
                        downWindowY = bubbleParams.y;
                        dragging = false;
                        view.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!dragging && (Math.abs(dx) > dp(7) || Math.abs(dy) > dp(7))) {
                            dragging = true;
                        }
                        if (dragging) {
                            bubbleParams.x = downWindowX + Math.round(dx);
                            bubbleParams.y = downWindowY + Math.round(dy);
                            clampAndUpdateBubble();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        if (dragging) {
                            snapBubbleToEdge();
                        } else {
                            togglePanel();
                            view.performClick();
                        }
                        scheduleBubbleFade();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        scheduleBubbleFade();
                        return true;
                    default:
                        return true;
                }
            }
        });

        windowManager.addView(bubbleOverlay, bubbleParams);
        scheduleBubbleFade();
    }

    private void ensureControlPanel() {
        if (windowManager == null || controlOverlay != null) {
            return;
        }

        controlOverlay = new LinearLayout(this);
        controlOverlay.setOrientation(LinearLayout.VERTICAL);
        controlOverlay.setGravity(Gravity.CENTER);
        controlOverlay.setPadding(dp(10), dp(8), dp(10), dp(8));
        controlOverlay.setBackground(roundedBackground(0xe617231c, 20f));
        controlOverlay.setElevation(dp(7));

        LinearLayout primaryRow = new LinearLayout(this);
        primaryRow.setOrientation(LinearLayout.HORIZONTAL);
        primaryRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout dpad = new LinearLayout(this);
        dpad.setOrientation(LinearLayout.VERTICAL);
        dpad.setGravity(Gravity.CENTER);

        Button up = controlButton("▲", "游標向上");
        bindClick(up, () -> moveCursor(0, -CURSOR_STEP_DP));
        dpad.addView(up, dpadButtonParams());

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setGravity(Gravity.CENTER);

        Button left = controlButton("◀", "短按游標向左，長按切換上一個頁面");
        bindShortLongPress(
                left,
                () -> moveCursor(-CURSOR_STEP_DP, 0),
                () -> swipeHorizontal(false)
        );
        middle.addView(left, dpadButtonParams());

        TextView center = new TextView(this);
        center.setText("＋");
        center.setTextColor(0xffa9bbb1);
        center.setTextSize(18f);
        center.setGravity(Gravity.CENTER);
        middle.addView(center, dpadButtonParams());

        Button right = controlButton("▶", "短按游標向右，長按切換下一個頁面");
        bindShortLongPress(
                right,
                () -> moveCursor(CURSOR_STEP_DP, 0),
                () -> swipeHorizontal(true)
        );
        middle.addView(right, dpadButtonParams());
        dpad.addView(middle);

        Button down = controlButton("▼", "游標向下");
        bindClick(down, () -> moveCursor(0, CURSOR_STEP_DP));
        dpad.addView(down, dpadButtonParams());

        primaryRow.addView(dpad);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionGroupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionGroupParams.leftMargin = dp(12);

        Button a = controlButton("A", "點擊游標位置");
        bindClick(a, this::tapCursor);
        actions.addView(a, actionButtonParams());

        Button b = controlButton("B", "短按返回，長按回到桌面");
        bindShortLongPress(
                b,
                () -> performSystemAction(GLOBAL_ACTION_BACK, "無法執行返回"),
                () -> performSystemAction(GLOBAL_ACTION_HOME, "無法回到桌面")
        );
        actions.addView(b, actionButtonParams());

        primaryRow.addView(actions, actionGroupParams);
        controlOverlay.addView(primaryRow);

        LinearLayout functionRow = new LinearLayout(this);
        functionRow.setOrientation(LinearLayout.HORIZONTAL);
        functionRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams functionRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        functionRowParams.topMargin = dp(7);

        Button home = functionButton("⌂", "回到桌面");
        bindClick(home, () -> performSystemAction(GLOBAL_ACTION_HOME, "無法回到桌面"));
        functionRow.addView(home, functionButtonParams());

        Button recents = functionButton("▣", "開啟最近使用的應用程式");
        bindClick(recents, () -> performSystemAction(GLOBAL_ACTION_RECENTS, "無法開啟最近使用"));
        functionRow.addView(recents, functionButtonParams());

        Button pageUp = functionButton("⇧", "頁面向上捲動");
        bindClick(pageUp, () -> swipeVertical(false));
        functionRow.addView(pageUp, functionButtonParams());

        Button pageDown = functionButton("⇩", "頁面向下捲動");
        bindClick(pageDown, () -> swipeVertical(true));
        functionRow.addView(pageDown, functionButtonParams());

        Button hold = functionButton("●", "長按游標位置");
        bindClick(hold, this::holdCursor);
        functionRow.addView(hold, functionButtonParams());

        Button collapse = functionButton("—", "收起控制盤");
        bindClick(collapse, this::hidePanel);
        functionRow.addView(collapse, functionButtonParams());

        controlOverlay.addView(functionRow, functionRowParams);

        controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        controlParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        controlParams.y = dp(18);
        controlParams.setTitle("Amin Universal Control Pad v0.2");
        windowManager.addView(controlOverlay, controlParams);
        controlOverlay.setVisibility(View.GONE);
    }

    private void ensureCursor() {
        if (windowManager == null || cursorOverlay != null) {
            return;
        }

        cursorOverlay = new View(this);
        GradientDrawable cursorBackground = new GradientDrawable();
        cursorBackground.setShape(GradientDrawable.OVAL);
        cursorBackground.setColor(0xff23a867);
        cursorBackground.setStroke(dp(2), Color.WHITE);
        cursorOverlay.setBackground(cursorBackground);
        cursorOverlay.setContentDescription("全域控制游標");

        int size = dp(24);
        cursorParams = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        cursorParams.gravity = Gravity.TOP | Gravity.START;
        cursorParams.setTitle("Amin Universal Control Cursor");
        updateCursorCoordinates();
        windowManager.addView(cursorOverlay, cursorParams);
        cursorOverlay.setVisibility(View.GONE);
    }

    private void showPanel() {
        if (!isOverlayEnabled(this)) {
            return;
        }
        showBubble();
        ensureControlPanel();
        ensureCursor();
        if (controlOverlay != null) {
            controlOverlay.setVisibility(View.VISIBLE);
        }
        if (cursorOverlay != null) {
            cursorOverlay.setVisibility(View.VISIBLE);
        }
        panelVisible = true;
        wakeBubble();
        schedulePanelAutoHide();
    }

    private void hidePanel() {
        mainHandler.removeCallbacks(autoHidePanelTask);
        if (controlOverlay != null) {
            controlOverlay.setVisibility(View.GONE);
        }
        if (cursorOverlay != null) {
            cursorOverlay.setVisibility(View.GONE);
        }
        panelVisible = false;
        scheduleBubbleFade();
    }

    private void togglePanel() {
        if (panelVisible) {
            hidePanel();
        } else {
            showPanel();
        }
    }

    private void markPanelActivity() {
        wakeBubble();
        if (panelVisible) {
            schedulePanelAutoHide();
        }
    }

    private void schedulePanelAutoHide() {
        mainHandler.removeCallbacks(autoHidePanelTask);
        if (!panelVisible) {
            return;
        }
        int seconds = getAutoHideSeconds(this);
        if (seconds > 0) {
            mainHandler.postDelayed(autoHidePanelTask, seconds * 1000L);
        }
    }

    private void wakeBubble() {
        mainHandler.removeCallbacks(fadeBubbleTask);
        if (bubbleOverlay != null) {
            bubbleOverlay.animate().cancel();
            bubbleOverlay.setAlpha(1f);
        }
    }

    private void scheduleBubbleFade() {
        mainHandler.removeCallbacks(fadeBubbleTask);
        if (bubbleOverlay != null) {
            mainHandler.postDelayed(fadeBubbleTask, BUBBLE_FADE_DELAY_MS);
        }
    }

    private void snapBubbleToEdge() {
        if (bubbleParams == null || bubbleOverlay == null) {
            return;
        }
        int size = bubbleParams.width;
        bubbleParams.x = bubbleParams.x + size / 2 < screenWidth / 2
                ? dp(4)
                : Math.max(dp(4), screenWidth - size - dp(4));
        clampAndUpdateBubble();
        preferences(this).edit()
                .putInt(KEY_BUBBLE_X, bubbleParams.x)
                .putInt(KEY_BUBBLE_Y, bubbleParams.y)
                .apply();
    }

    private void clampAndUpdateBubble() {
        if (bubbleParams == null) {
            return;
        }
        clampBubbleCoordinates();
        if (windowManager != null && bubbleOverlay != null) {
            try {
                windowManager.updateViewLayout(bubbleOverlay, bubbleParams);
            } catch (IllegalArgumentException ignored) {
                // The system may already be removing the overlay.
            }
        }
    }

    private void clampBubbleCoordinates() {
        if (bubbleParams == null) {
            return;
        }
        int width = bubbleParams.width > 0 ? bubbleParams.width : dp(56);
        int height = bubbleParams.height > 0 ? bubbleParams.height : dp(56);
        bubbleParams.x = Math.max(0, Math.min(bubbleParams.x, Math.max(0, screenWidth - width)));
        bubbleParams.y = Math.max(dp(24), Math.min(bubbleParams.y, Math.max(dp(24), screenHeight - height - dp(24))));
    }

    private Button controlButton(String label, String description) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setContentDescription(description);
        button.setBackground(roundedBackground(0xee2a3b31, 16f));
        return button;
    }

    private Button functionButton(String label, String description) {
        Button button = controlButton(label, description);
        button.setTextSize(16f);
        button.setBackground(roundedBackground(0xee354a3e, 13f));
        return button;
    }

    private LinearLayout.LayoutParams dpadButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(42));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private LinearLayout.LayoutParams actionButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(56), dp(50));
        params.setMargins(dp(2), dp(4), dp(2), dp(4));
        return params;
    }

    private LinearLayout.LayoutParams functionButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(38));
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        return params;
    }

    private void bindClick(View view, Runnable action) {
        view.setOnClickListener(touched -> {
            markPanelActivity();
            action.run();
            if (panelVisible) {
                schedulePanelAutoHide();
            }
        });
    }

    private void bindShortLongPress(View view, Runnable shortAction, Runnable longAction) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private boolean longTriggered;
            private final Runnable longPressTask = () -> {
                longTriggered = true;
                longAction.run();
                markPanelActivity();
            };

            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        longTriggered = false;
                        touched.setPressed(true);
                        markPanelActivity();
                        mainHandler.postDelayed(longPressTask, LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_UP:
                        mainHandler.removeCallbacks(longPressTask);
                        touched.setPressed(false);
                        if (!longTriggered) {
                            shortAction.run();
                        }
                        touched.performClick();
                        markPanelActivity();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(longPressTask);
                        touched.setPressed(false);
                        markPanelActivity();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void moveCursor(int dxDp, int dyDp) {
        cursorX = clamp(cursorX + dp(dxDp), dp(14), screenWidth - dp(14));
        cursorY = clamp(cursorY + dp(dyDp), dp(14), screenHeight - dp(14));
        updateCursorPosition();
    }

    private void updateCursorPosition() {
        if (windowManager == null || cursorOverlay == null || cursorParams == null) {
            return;
        }
        updateCursorCoordinates();
        try {
            windowManager.updateViewLayout(cursorOverlay, cursorParams);
        } catch (IllegalArgumentException ignored) {
            // The system may already be removing the accessibility overlay.
        }
    }

    private void updateCursorCoordinates() {
        if (cursorParams == null) {
            return;
        }
        int radius = dp(12);
        cursorParams.x = Math.round(cursorX) - radius;
        cursorParams.y = Math.round(cursorY) - radius;
    }

    private void tapCursor() {
        Path path = new Path();
        path.moveTo(cursorX, cursorY);
        dispatchPath(path, TAP_DURATION_MS, "無法點擊目前位置");
    }

    private void holdCursor() {
        Path path = new Path();
        path.moveTo(cursorX, cursorY);
        dispatchPath(path, HOLD_DURATION_MS, "無法長按目前位置");
    }

    private void swipeHorizontal(boolean nextPage) {
        float y = screenHeight * 0.5f;
        float startX = screenWidth * (nextPage ? 0.82f : 0.18f);
        float endX = screenWidth * (nextPage ? 0.18f : 0.82f);

        Path path = new Path();
        path.moveTo(startX, y);
        path.lineTo(endX, y);
        dispatchPath(path, SWIPE_DURATION_MS, "無法水平切換頁面");
    }

    private void swipeVertical(boolean pageDown) {
        float x = screenWidth * 0.55f;
        float startY = screenHeight * (pageDown ? 0.76f : 0.28f);
        float endY = screenHeight * (pageDown ? 0.28f : 0.76f);

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        dispatchPath(path, SWIPE_DURATION_MS, "無法垂直捲動頁面");
    }

    private void dispatchPath(Path path, long durationMs, String failureMessage) {
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs))
                .build();

        boolean accepted = dispatchGesture(
                gesture,
                new GestureResultCallback() {
                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        Toast.makeText(
                                UniversalControlAccessibilityService.this,
                                failureMessage,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                },
                null
        );

        if (!accepted) {
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void performSystemAction(int action, String failureMessage) {
        if (!performGlobalAction(action)) {
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshScreenBounds() {
        if (windowManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }

        if (screenWidth <= 0) {
            screenWidth = getResources().getDisplayMetrics().widthPixels;
        }
        if (screenHeight <= 0) {
            screenHeight = getResources().getDisplayMetrics().heightPixels;
        }
    }

    private void removeOverlays() {
        mainHandler.removeCallbacks(fadeBubbleTask);
        mainHandler.removeCallbacks(autoHidePanelTask);

        removeViewSafely(controlOverlay);
        removeViewSafely(cursorOverlay);
        removeViewSafely(bubbleOverlay);

        controlOverlay = null;
        controlParams = null;
        cursorOverlay = null;
        cursorParams = null;
        bubbleOverlay = null;
        bubbleParams = null;
        panelVisible = false;
    }

    private void removeViewSafely(View view) {
        if (windowManager == null || view == null) {
            return;
        }
        try {
            windowManager.removeView(view);
        } catch (IllegalArgumentException ignored) {
            // Already removed by the system.
        }
    }

    private GradientDrawable roundedBackground(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable circleBackground(int fill, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), stroke);
        }
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
