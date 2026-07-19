package com.amin.pocketgba;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
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
    private static final long LONG_PRESS_MS = 520L;
    private static final long TAP_DURATION_MS = 70L;
    private static final long SWIPE_DURATION_MS = 320L;
    private static final int CURSOR_STEP_DP = 48;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private LinearLayout controlOverlay;
    private View cursorOverlay;
    private WindowManager.LayoutParams cursorParams;

    private int screenWidth;
    private int screenHeight;
    private float cursorX;
    private float cursorY;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        refreshScreenBounds();
        cursorX = screenWidth * 0.5f;
        cursorY = screenHeight * 0.5f;
        showCursor();
        showControls();
        Toast.makeText(this, "全域控制盤已啟用", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // v0.1 intentionally does not inspect other apps or their content.
    }

    @Override
    public void onInterrupt() {
        // There is no spoken or haptic feedback to interrupt in v0.1.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshScreenBounds();
        cursorX = clamp(cursorX, dp(14), screenWidth - dp(14));
        cursorY = clamp(cursorY, dp(14), screenHeight - dp(14));
        updateCursorPosition();
    }

    @Override
    public void onDestroy() {
        removeOverlays();
        super.onDestroy();
    }

    private void showControls() {
        if (windowManager == null || controlOverlay != null) {
            return;
        }

        controlOverlay = new LinearLayout(this);
        controlOverlay.setOrientation(LinearLayout.HORIZONTAL);
        controlOverlay.setGravity(Gravity.CENTER_VERTICAL);
        controlOverlay.setPadding(dp(10), dp(8), dp(10), dp(8));
        controlOverlay.setBackground(roundedBackground(0xcc17231c, 20f));

        LinearLayout dpad = new LinearLayout(this);
        dpad.setOrientation(LinearLayout.VERTICAL);
        dpad.setGravity(Gravity.CENTER);

        Button up = controlButton("▲", "游標向上");
        up.setOnClickListener(view -> moveCursor(0, -CURSOR_STEP_DP));
        dpad.addView(up, buttonParams());

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setGravity(Gravity.CENTER);

        Button left = controlButton("◀", "短按游標向左，長按切換上一個桌面頁");
        bindShortLongPress(
                left,
                () -> moveCursor(-CURSOR_STEP_DP, 0),
                () -> swipeHomePage(false)
        );
        middle.addView(left, buttonParams());

        TextView center = new TextView(this);
        center.setText("＋");
        center.setTextColor(0xffa9bbb1);
        center.setTextSize(18f);
        center.setGravity(Gravity.CENTER);
        middle.addView(center, buttonParams());

        Button right = controlButton("▶", "短按游標向右，長按切換下一個桌面頁");
        bindShortLongPress(
                right,
                () -> moveCursor(CURSOR_STEP_DP, 0),
                () -> swipeHomePage(true)
        );
        middle.addView(right, buttonParams());
        dpad.addView(middle);

        Button down = controlButton("▼", "游標向下");
        down.setOnClickListener(view -> moveCursor(0, CURSOR_STEP_DP));
        dpad.addView(down, buttonParams());

        controlOverlay.addView(dpad);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionGroupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionGroupParams.leftMargin = dp(12);

        Button a = controlButton("A", "點擊游標位置");
        a.setOnClickListener(view -> tapCursor());
        actions.addView(a, actionButtonParams());

        Button b = controlButton("B", "短按返回，長按回到桌面");
        bindShortLongPress(
                b,
                () -> performSystemAction(GLOBAL_ACTION_BACK, "無法執行返回"),
                () -> performSystemAction(GLOBAL_ACTION_HOME, "無法回到桌面")
        );
        actions.addView(b, actionButtonParams());

        controlOverlay.addView(actions, actionGroupParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(18);
        params.setTitle("Amin Universal Control Pad");

        windowManager.addView(controlOverlay, params);
    }

    private void showCursor() {
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
        button.setBackground(roundedBackground(0xdd2a3b31, 16f));
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(42));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private LinearLayout.LayoutParams actionButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(56), dp(50));
        params.setMargins(dp(2), dp(4), dp(2), dp(4));
        return params;
    }

    private void bindShortLongPress(View view, Runnable shortAction, Runnable longAction) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private boolean longTriggered;
            private final Runnable longPressTask = () -> {
                longTriggered = true;
                longAction.run();
            };

            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        longTriggered = false;
                        touched.setPressed(true);
                        mainHandler.postDelayed(longPressTask, LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_UP:
                        mainHandler.removeCallbacks(longPressTask);
                        touched.setPressed(false);
                        if (!longTriggered) {
                            shortAction.run();
                        }
                        touched.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(longPressTask);
                        touched.setPressed(false);
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

    private void swipeHomePage(boolean nextPage) {
        float y = screenHeight * 0.5f;
        float startX = screenWidth * (nextPage ? 0.82f : 0.18f);
        float endX = screenWidth * (nextPage ? 0.18f : 0.82f);

        Path path = new Path();
        path.moveTo(startX, y);
        path.lineTo(endX, y);
        dispatchPath(path, SWIPE_DURATION_MS, "無法切換桌面頁");
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
        mainHandler.removeCallbacksAndMessages(null);

        if (windowManager != null && controlOverlay != null) {
            try {
                windowManager.removeView(controlOverlay);
            } catch (IllegalArgumentException ignored) {
                // Already removed by the system.
            }
        }
        if (windowManager != null && cursorOverlay != null) {
            try {
                windowManager.removeView(cursorOverlay);
            } catch (IllegalArgumentException ignored) {
                // Already removed by the system.
            }
        }
        controlOverlay = null;
        cursorOverlay = null;
        cursorParams = null;
    }

    private GradientDrawable roundedBackground(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
