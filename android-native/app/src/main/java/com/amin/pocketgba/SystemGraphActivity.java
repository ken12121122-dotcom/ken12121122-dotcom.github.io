package com.amin.pocketgba;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal conversation-node observer.
 *
 * The old feature-navigation graph is intentionally hidden. Existing features remain
 * installed and reachable through their normal screens; this view now only answers:
 * "Did my conversation create nodes?"
 */
public final class SystemGraphActivity extends Activity {
    private ConversationGraphView graphView;
    private TextView hintView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff07110d);
        getWindow().setNavigationBarColor(0xff07110d);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff07110d);

        graphView = new ConversationGraphView();
        root.addView(graphView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        hintView = new TextView(this);
        hintView.setTextColor(0xffb9c9c0);
        hintView.setTextSize(14f);
        hintView.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        hintParams.gravity = android.view.Gravity.TOP;
        hintParams.topMargin = dp(22);
        root.addView(hintView, hintParams);

        setContentView(root);
        refreshGraph();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshGraph();
    }

    private void refreshGraph() {
        if (graphView == null || hintView == null) return;
        MemoryNodeStore store = new MemoryNodeStore(this);
        List<MemoryNodeStore.MemoryNode> recent = store.getRecent(12);
        graphView.setMemoryNodes(recent);
        if (recent.isEmpty()) {
            hintView.setText("記憶節點 · 目前還沒有節點\n回到語音球聊天，成功取得 LLM 回覆後再回來看");
        } else {
            hintView.setText("記憶節點 · 已形成 " + store.count() + " 個 · 顯示最近 " + recent.size() + " 個");
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private final class ConversationGraphView extends View {
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<MemoryNodeStore.MemoryNode> memoryNodes = new ArrayList<>();

        ConversationGraphView() {
            super(SystemGraphActivity.this);
            setBackgroundColor(0xff07110d);
            edgePaint.setColor(0x665ce8a4);
            edgePaint.setStrokeWidth(dp(2));
            corePaint.setColor(0xff1fa765);
            nodePaint.setColor(0xff173c29);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(13));
            subTextPaint.setColor(0xff78998a);
            subTextPaint.setTextAlign(Paint.Align.CENTER);
            subTextPaint.setTextSize(dp(12));
            setContentDescription("Amin 對話記憶節點地圖");
        }

        void setMemoryNodes(List<MemoryNodeStore.MemoryNode> nodes) {
            memoryNodes.clear();
            if (nodes != null) memoryNodes.addAll(nodes);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f + dp(18);
            float coreRadius = dp(48);

            if (memoryNodes.isEmpty()) {
                canvas.drawCircle(cx, cy, coreRadius, corePaint);
                drawCentered(canvas, "對話", cx, cy, textPaint);
                canvas.drawText("等待形成節點", cx, cy + dp(82), subTextPaint);
                return;
            }

            float rx = Math.min(getWidth() * 0.38f, dp(170));
            float ry = Math.min(getHeight() * 0.31f, dp(245));
            int count = memoryNodes.size();

            for (int i = 0; i < count; i++) {
                double angle = -Math.PI / 2d + (Math.PI * 2d * i / count);
                float x = cx + (float) Math.cos(angle) * rx;
                float y = cy + (float) Math.sin(angle) * ry;
                canvas.drawLine(cx, cy, x, y, edgePaint);
            }

            canvas.drawCircle(cx, cy, coreRadius, corePaint);
            drawCentered(canvas, "對話", cx, cy, textPaint);

            for (int i = 0; i < count; i++) {
                MemoryNodeStore.MemoryNode node = memoryNodes.get(i);
                double angle = -Math.PI / 2d + (Math.PI * 2d * i / count);
                float x = cx + (float) Math.cos(angle) * rx;
                float y = cy + (float) Math.sin(angle) * ry;
                float radius = dp(34);
                canvas.drawCircle(x, y, radius, nodePaint);
                drawCentered(canvas, node.label, x, y, textPaint);
            }
        }

        private void drawCentered(Canvas canvas, String value, float x, float y, Paint paint) {
            String text = value == null ? "" : value;
            if (text.length() > 7) text = text.substring(0, 7) + "…";
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = y - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, x, baseline, paint);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
