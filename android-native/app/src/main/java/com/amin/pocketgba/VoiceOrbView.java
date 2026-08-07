package com.amin.pocketgba;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class VoiceOrbView extends View {
    enum Phase { IDLE, LISTENING, PROCESSING, SUCCESS, ERROR }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator;
    private Phase phase = Phase.IDLE;
    private float animationProgress;
    private float amplitude;

    VoiceOrbView(Context context) {
        super(context);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1800L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            animationProgress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
        setContentDescription("Amin 動態語音球");
    }

    void setPhase(Phase value) {
        phase = value == null ? Phase.IDLE : value;
        invalidate();
    }

    void setAmplitude(float rmsDb) {
        float normalized = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 12f));
        amplitude = amplitude * 0.7f + normalized * 0.3f;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float min = Math.min(getWidth(), getHeight());
        float baseRadius = min * 0.23f;
        float pulse = phase == Phase.LISTENING ? amplitude * baseRadius * 0.22f : (float) Math.sin(animationProgress * Math.PI * 2) * baseRadius * 0.035f;
        float radius = baseRadius + pulse;

        int inner;
        int outer;
        switch (phase) {
            case LISTENING:
                inner = 0xffd8fff0;
                outer = 0xff35d98b;
                break;
            case PROCESSING:
                inner = 0xffe5f0ff;
                outer = 0xff7b8cff;
                break;
            case SUCCESS:
                inner = 0xffffffff;
                outer = 0xff5ce8a4;
                break;
            case ERROR:
                inner = 0xffffe8e8;
                outer = 0xffff6b6b;
                break;
            default:
                inner = 0xffe8fff5;
                outer = 0xff1aa866;
        }

        paint.setShader(new RadialGradient(cx - radius * 0.22f, cy - radius * 0.25f, radius * 1.25f,
                new int[]{inner, outer, 0xff0a2b1b}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);

        int rings = phase == Phase.LISTENING ? 3 : 2;
        for (int i = 0; i < rings; i++) {
            float p = (animationProgress + i / (float) rings) % 1f;
            float ringRadius = radius + dp(12) + p * baseRadius * 0.72f;
            int alpha = Math.round((1f - p) * (phase == Phase.LISTENING ? 130 : 70));
            ringPaint.setColor(Color.argb(alpha, 104, 245, 170));
            canvas.drawCircle(cx, cy, ringRadius, ringPaint);
        }
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
