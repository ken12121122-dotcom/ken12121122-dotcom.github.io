package com.amin.pocketgba;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Neural Flow POC.
 *
 * This screen is intentionally isolated from the production Voice Orb path.
 * It proves the mobile workflow-canvas interaction and the visual signal moving
 * through INPUT -> ROUTER -> LLM before Graphyn is wired into the production graph.
 */
public final class NeuralFlowActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xfff7f8f7);
        getWindow().setNavigationBarColor(0xfff7f8f7);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xfff7f8f7);

        FlowCanvas canvas = new FlowCanvas();
        root.addView(canvas, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        TextView title = new TextView(this);
        title.setText("Neural Flow");
        title.setTextSize(20f);
        title.setTextColor(0xff17211b);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(18), dp(14), dp(18), dp(10));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.gravity = Gravity.TOP | Gravity.START;
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Runtime signal canvas · isolated POC");
        subtitle.setTextSize(12f);
        subtitle.setTextColor(0xff6b746e);
        subtitle.setPadding(dp(18), dp(44), dp(18), 0);
        root.addView(subtitle, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
        canvas.start();
    }

    private final class FlowCanvas extends View {
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cardStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint signal = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF inputRect = new RectF();
        private final RectF routerRect = new RectF();
        private final RectF llmRect = new RectF();
        private float progress;
        private ValueAnimator animator;

        FlowCanvas() {
            super(NeuralFlowActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            grid.setColor(0xffe7ebe8);
            grid.setStrokeWidth(dp(1));
            edge.setColor(0xff94a29a);
            edge.setStrokeWidth(dp(2));
            edge.setStyle(Paint.Style.STROKE);
            card.setColor(Color.WHITE);
            cardStroke.setColor(0xffd7ded9);
            cardStroke.setStyle(Paint.Style.STROKE);
            cardStroke.setStrokeWidth(dp(1));
            title.setColor(0xff17211b);
            title.setTextSize(sp(15));
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            meta.setColor(0xff6b746e);
            meta.setTextSize(sp(11));
            signal.setColor(0xff16a05d);
            signal.setShadowLayer(dp(5), 0f, 0f, 0x6616a05d);
        }

        void start() {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(3600L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) animator.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawGrid(canvas);

            float w = getWidth();
            float centerX = w * 0.5f;
            float cardW = Math.min(dp(250), w - dp(56));
            float cardH = dp(82);
            float left = centerX - cardW / 2f;
            float top = dp(118);

            inputRect.set(left, top, left + cardW, top + cardH);
            routerRect.set(left, top + dp(150), left + cardW, top + dp(150) + cardH);
            llmRect.set(left, top + dp(300), left + cardW, top + dp(300) + cardH);

            Path route = new Path();
            route.moveTo(centerX, inputRect.bottom);
            route.lineTo(centerX, routerRect.top);
            route.moveTo(centerX, routerRect.bottom);
            route.lineTo(centerX, llmRect.top);
            canvas.drawPath(route, edge);

            drawCard(canvas, inputRect, "INPUT", "Voice / text signal", 0xffedf7f1);
            drawCard(canvas, routerRect, "ROUTER", "Command · Node · Chat", 0xfffff6e9);
            drawCard(canvas, llmRect, "LLM", "Gemini · OpenAI · Claude", 0xffeef3ff);

            float firstStart = inputRect.bottom;
            float firstEnd = routerRect.top;
            float secondStart = routerRect.bottom;
            float secondEnd = llmRect.top;
            float x = centerX;
            float y;
            if (progress < 0.5f) {
                float p = progress / 0.5f;
                y = firstStart + (firstEnd - firstStart) * p;
            } else {
                float p = (progress - 0.5f) / 0.5f;
                y = secondStart + (secondEnd - secondStart) * p;
            }
            canvas.drawCircle(x, y, dp(7), signal);
        }

        private void drawGrid(Canvas canvas) {
            int step = dp(24);
            for (int x = 0; x < getWidth(); x += step) {
                for (int y = 0; y < getHeight(); y += step) {
                    canvas.drawCircle(x, y, dp(1), grid);
                }
            }
        }

        private void drawCard(Canvas canvas, RectF rect, String name, String description, int tint) {
            Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tintPaint.setColor(tint);
            canvas.drawRoundRect(rect, dp(16), dp(16), card);
            canvas.drawRoundRect(rect, dp(16), dp(16), cardStroke);
            RectF badge = new RectF(rect.left + dp(14), rect.top + dp(14), rect.left + dp(64), rect.top + dp(38));
            canvas.drawRoundRect(badge, dp(12), dp(12), tintPaint);
            canvas.drawText(name, rect.left + dp(76), rect.top + dp(31), title);
            canvas.drawText(description, rect.left + dp(18), rect.top + dp(59), meta);
            canvas.drawCircle(rect.centerX(), rect.bottom, dp(4), signal);
            canvas.drawCircle(rect.centerX(), rect.top, dp(4), signal);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
