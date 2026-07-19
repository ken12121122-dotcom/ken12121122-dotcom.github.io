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
import android.widget.FrameLayout;
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
    private static final int DPAD_KEY_DP = 54;
    private static final int DPAD_SIZE_DP = DPAD_KEY_DP * 3;
    private static final float BUBBLE_IDLE_ALPHA = 0.25f;

    private static volatile UniversalControlAccessibilityService activeInstance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;

    private TextView bubbleOverlay;
    private WindowManager.LayoutParams bubbleParams;

    private FrameLayout dpadOverlay;
    private WindowManager.LayoutParams dpadParams;

    private FrameLayout actionOverlay;
    private WindowManager.LayoutParams actionParams;

    private Button leftShoulderOverlay;
    private WindowManager.LayoutParams leftShoulderParams;

    private Button rightShoulderOverlay;
    private WindowManager.LayoutParams rightShoulderParams;

    private LinearLayout centerOverlay;
    private WindowManager.LayoutParams centerParams;

    private View cursorOverlay;
    private WindowManager.LayoutParams cursorParams;

    private int screenWidth;
    private int screenHeight;
    private float cursorX;
    private float cursorY;
    private boolean controlsVisible;

    private final Runnable fadeBubbleTask = () -> {
        if (bubbleOverlay != null) {
            bubbleOverlay.animate()
                    .alpha(BUBBLE_IDLE_ALPHA)
                    .setDuration(260L)
                    .start();
        }
    };

    private final Runnable autoHideControlsTask = this::hideControls;

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
            instance.mainHandler.post(instance::scheduleControlsAutoHide);
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
        Toast.makeText(this, "GBA 全域控制已連線", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service intentionally does not inspect or read content from other apps.
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
        updateGamepadLayout();
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
        hideControls();
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
        bubbleOverlay.setContentDescription("GBA 全域控制喚醒球，點擊展開或收起，拖曳可移動");
        bubbleOverlay.setBackground(circleBackground(0xe6192a20, Color.WHITE, 1));
        bubbleOverlay.setElevation(dp(8));

        int size = dp(56);
        bubbleParams = baseOverlayParams(size, size, "Amin GBA Wake Bubble");
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = preferences(this).getInt(
                KEY_BUBBLE_X,
                Math.max(0, screenWidth - size - dp(8))
        );
        bubbleParams.y = preferences(this).getInt(
                KEY_BUBBLE_Y,
                Math.max(dp(72), screenHeight / 3)
        );
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
                        markControlsActivity();
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
                            toggleControls();
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

    private void ensureGamepadControls() {
        if (windowManager == null || dpadOverlay != null) {
            return;
        }

        createFixedDpadOverlay();
        createActionOverlay();
        createShoulderOverlays();
        createCenterOverlay();
        ensureCursor();
        updateGamepadLayout();
        setControlsVisibility(View.GONE);
    }

    /**
     * Uses a fixed 3 x 3 FrameLayout instead of nested wrap-content LinearLayouts.
     * This prevents Android accessibility overlays from measuring the D-pad as a
     * single narrow column and clipping the left/right keys on some Samsung builds.
     */
    private void createFixedDpadOverlay() {
        dpadOverlay = new FrameLayout(this);

        Button up = dpadButton("▲", "短按游標向上，長按頁面向上捲動");
        bindShortLongPress(
                up,
                () -> moveCursor(0, -CURSOR_STEP_DP),
                () -> swipeVertical(false)
        );
        dpadOverlay.addView(up, dpadCell(1, 0));

        Button left = dpadButton("◀", "短按游標向左，長按切換上一個頁面");
        bindShortLongPress(
                left,
                () -> moveCursor(-CURSOR_STEP_DP, 0),
                () -> swipeHorizontal(false)
        );
        dpadOverlay.addView(left, dpadCell(0, 1));

        View center = new View(this);
        center.setBackground(roundedBackground(0xb84a4a4a, 8f, 0x88ffffff, 1));
        dpadOverlay.addView(center, dpadCell(1, 1));

        Button right = dpadButton("▶", "短按游標向右，長按切換下一個頁面");
        bindShortLongPress(
                right,
                () -> moveCursor(CURSOR_STEP_DP, 0),
                () -> swipeHorizontal(true)
        );
        dpadOverlay.addView(right, dpadCell(2, 1));

        Button down = dpadButton("▼", "短按游標向下，長按頁面向下捲動");
        bindShortLongPress(
                down,
                () -> moveCursor(0, CURSOR_STEP_DP),
                () -> swipeVertical(true)
        );
        dpadOverlay.addView(down, dpadCell(1, 2));

        dpadParams = baseOverlayParams(
                dp(DPAD_SIZE_DP),
                dp(DPAD_SIZE_DP),
                "Amin GBA Fixed D-pad"
        );
        dpadParams.gravity = Gravity.BOTTOM | Gravity.START;
        windowManager.addView(dpadOverlay, dpadParams);
    }

    private FrameLayout.LayoutParams dpadCell(int column, int row) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(DPAD_KEY_DP),
                dp(DPAD_KEY_DP)
        );
        params.leftMargin = dp(DPAD_KEY_DP * column);
        params.topMargin = dp(DPAD_KEY_DP * row);
        return params;
    }

    private void createActionOverlay() {
        actionOverlay = new FrameLayout(this);

        Button b = roundActionButton("B", "短按返回，長按回到桌面");
        bindShortLongPress(
                b,
                () -> performSystemAction(GLOBAL_ACTION_BACK, "無法執行返回"),
                () -> performSystemAction(GLOBAL_ACTION_HOME, "無法回到桌面")
        );
        FrameLayout.LayoutParams bParams = new FrameLayout.LayoutParams(dp(64), dp(64));
        bParams.leftMargin = 0;
        bParams.topMargin = dp(38);
        actionOverlay.addView(b, bParams);

        Button a = roundActionButton("A", "短按點擊游標，長按游標位置");
        bindShortLongPress(a, this::tapCursor, this::holdCursor);
        FrameLayout.LayoutParams aParams = new FrameLayout.LayoutParams(dp(64), dp(64));
        aParams.leftMargin = dp(66);
        aParams.topMargin = 0;
        actionOverlay.addView(a, aParams);

        actionParams = baseOverlayParams(dp(130), dp(102), "Amin GBA A B Buttons");
        actionParams.gravity = Gravity.BOTTOM | Gravity.END;
        windowManager.addView(actionOverlay, actionParams);
    }

    private void createShoulderOverlays() {
        leftShoulderOverlay = shoulderButton("L", "頁面向上捲動");
        bindClick(leftShoulderOverlay, () -> swipeVertical(false));
        leftShoulderParams = baseOverlayParams(dp(86), dp(52), "Amin GBA L Button");
        leftShoulderParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(leftShoulderOverlay, leftShoulderParams);

        rightShoulderOverlay = shoulderButton("R", "頁面向下捲動");
        bindClick(rightShoulderOverlay, () -> swipeVertical(true));
        rightShoulderParams = baseOverlayParams(dp(86), dp(52), "Amin GBA R Button");
        rightShoulderParams.gravity = Gravity.TOP | Gravity.END;
        windowManager.addView(rightShoulderOverlay, rightShoulderParams);
    }

    private void createCenterOverlay() {
        centerOverlay = new LinearLayout(this);
        centerOverlay.setOrientation(LinearLayout.HORIZONTAL);
        centerOverlay.setGravity(Gravity.CENTER);

        Button select = pillButton("Select", "開啟最近使用的應用程式");
        bindClick(select, () -> performSystemAction(GLOBAL_ACTION_RECENTS, "無法開啟最近使用"));
        centerOverlay.addView(select, centerButtonParams());

        Button start = pillButton("Start", "短按回到桌面，長按收起全部按鍵");
        bindShortLongPress(
                start,
                () -> performSystemAction(GLOBAL_ACTION_HOME, "無法回到桌面"),
                this::hideControls
        );
        centerOverlay.addView(start, centerButtonParams());

        centerParams = baseOverlayParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                "Amin GBA Start Select"
        );
        centerParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        windowManager.addView(centerOverlay, centerParams);
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
    }

    private void updateGamepadLayout() {
        boolean landscape = screenWidth > screenHeight;

        if (dpadParams != null) {
            dpadParams.x = dp(landscape ? 18 : 10);
            dpadParams.y = dp(landscape ? 28 : 62);
            updateViewLayoutSafely(dpadOverlay, dpadParams);
        }
        if (actionParams != null) {
            actionParams.x = dp(landscape ? 18 : 10);
            actionParams.y = dp(landscape ? 30 : 66);
            updateViewLayoutSafely(actionOverlay, actionParams);
        }
        if (leftShoulderParams != null) {
            leftShoulderParams.x = dp(landscape ? 22 : 14);
            leftShoulderParams.y = dp(landscape ? 58 : 86);
            updateViewLayoutSafely(leftShoulderOverlay, leftShoulderParams);
        }
        if (rightShoulderParams != null) {
            rightShoulderParams.x = dp(landscape ? 22 : 14);
            rightShoulderParams.y = dp(landscape ? 58 : 86);
            updateViewLayoutSafely(rightShoulderOverlay, rightShoulderParams);
        }
        if (centerParams != null) {
            centerParams.y = dp(landscape ? 18 : 24);
            updateViewLayoutSafely(centerOverlay, centerParams);
        }
    }

    private void showControls() {
        if (!isOverlayEnabled(this)) {
            return;
        }
        showBubble();
        ensureGamepadControls();
        setControlsVisibility(View.VISIBLE);
        controlsVisible = true;
        wakeBubble();
        scheduleControlsAutoHide();
    }

    private void hideControls() {
        mainHandler.removeCallbacks(autoHideControlsTask);
        setControlsVisibility(View.GONE);
        controlsVisible = false;
        scheduleBubbleFade();
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void setControlsVisibility(int visibility) {
        setVisibility(dpadOverlay, visibility);
        setVisibility(actionOverlay, visibility);
        setVisibility(leftShoulderOverlay, visibility);
        setVisibility(rightShoulderOverlay, visibility);
        setVisibility(centerOverlay, visibility);
        setVisibility(cursorOverlay, visibility);
    }

    private void setVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private void markControlsActivity() {
        wakeBubble();
        if (controlsVisible) {
            scheduleControlsAutoHide();
        }
    }

    private void scheduleControlsAutoHide() {
        mainHandler.removeCallbacks(autoHideControlsTask);
        if (!controlsVisible) {
            return;
        }
        int seconds = getAutoHideSeconds(this);
        if (seconds > 0) {
            mainHandler.postDelayed(autoHideControlsTask, seconds * 1000L);
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
        updateViewLayoutSafely(bubbleOverlay, bubbleParams);
    }

    private void clampBubbleCoordinates() {
        if (bubbleParams == null) {
            return;
        }
        int width = bubbleParams.width > 0 ? bubbleParams.width : dp(56);
        int height = bubbleParams.height > 0 ? bubbleParams.height : dp(56);
        bubbleParams.x = Math.max(0, Math.min(bubbleParams.x, Math.max(0, screenWidth - width)));
        bubbleParams.y = Math.max(
                dp(24),
                Math.min(bubbleParams.y, Math.max(dp(24), screenHeight - height - dp(24)))
        );
    }

    private WindowManager.LayoutParams baseOverlayParams(int width, int height, String title) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.setTitle(title);
        return params;
    }

    private Button dpadButton(String label, String description) {
        Button button = baseButton(label, description, 20f);
        button.setBackground(roundedBackground(0xb84a4a4a, 8f, 0x88ffffff, 1));
        return button;
    }

    private Button roundActionButton(String label, String description) {
        Button button = baseButton(label, description, 28f);
        button.setBackground(circleBackground(0xc84d4d4d, 0xccffffff, 1));
        return button;
    }

    private Button shoulderButton(String label, String description) {
        Button button = baseButton(label, description, 28f);
        button.setBackground(roundedBackground(0xc84d4d4d, 10f, 0xccffffff, 1));
        return button;
    }

    private Button pillButton(String label, String description) {
        Button button = baseButton(label, description, 14f);
        button.setBackground(roundedBackground(0xc84d4d4d, 12f, 0xccffffff, 1));
        return button;
    }

    private Button baseButton(String label, String description, float textSizeSp) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(textSizeSp);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setContentDescription(description);
        return button;
    }

    private LinearLayout.LayoutParams centerButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(78), dp(42));
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private void bindClick(View view, Runnable action) {
        view.setOnClickListener(touched -> {
            markControlsActivity();
            action.run();
            if (controlsVisible) {
                scheduleControlsAutoHide();
            }
        });
    }

    private void bindShortLongPress(View view, Runnable shortAction, Runnable longAction) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private boolean longTriggered;
            private final Runnable longPressTask = () -> {
                longTriggered = true;
                longAction.run();
                markControlsActivity();
            };

            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        longTriggered = false;
                        touched.setPressed(true);
                        markControlsActivity();
                        mainHandler.postDelayed(longPressTask, LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_UP:
                        mainHandler.removeCallbacks(longPressTask);
                        touched.setPressed(false);
                        if (!longTriggered) {
                            shortAction.run();
                        }
                        touched.performClick();
                        markControlsActivity();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(longPressTask);
                        touched.setPressed(false);
                        markControlsActivity();
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
        updateViewLayoutSafely(cursorOverlay, cursorParams);
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

    private void updateViewLayoutSafely(View view, WindowManager.LayoutParams params) {
        if (windowManager == null || view == null || params == null) {
            return;
        }
        try {
            windowManager.updateViewLayout(view, params);
        } catch (IllegalArgumentException ignored) {
            // The system may already be removing the accessibility overlay.
        }
    }

    private void removeOverlays() {
        mainHandler.removeCallbacks(fadeBubbleTask);
        mainHandler.removeCallbacks(autoHideControlsTask);

        removeViewSafely(dpadOverlay);
        removeViewSafely(actionOverlay);
        removeViewSafely(leftShoulderOverlay);
        removeViewSafely(rightShoulderOverlay);
        removeViewSafely(centerOverlay);
        removeViewSafely(cursorOverlay);
        removeViewSafely(bubbleOverlay);

        dpadOverlay = null;
        dpadParams = null;
        actionOverlay = null;
        actionParams = null;
        leftShoulderOverlay = null;
        leftShoulderParams = null;
        rightShoulderOverlay = null;
        rightShoulderParams = null;
        centerOverlay = null;
        centerParams = null;
        cursorOverlay = null;
        cursorParams = null;
        bubbleOverlay = null;
        bubbleParams = null;
        controlsVisible = false;
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

    private GradientDrawable roundedBackground(
            int fill,
            float radiusDp,
            int stroke,
            int strokeDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), stroke);
        }
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
