package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class SystemGraphActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff07110d);
        getWindow().setNavigationBarColor(0xff07110d);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff07110d);

        SystemGraphView graph = new SystemGraphView();
        root.addView(graph, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        TextView hint = new TextView(this);
        hint.setText("功能節點地圖 · 點選節點開啟");
        hint.setTextColor(0xffb9c9c0);
        hint.setTextSize(14f);
        hint.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        hintParams.gravity = android.view.Gravity.TOP;
        hintParams.topMargin = dp(22);
        root.addView(hint, hintParams);

        setContentView(root);
    }

    private void openNode(String id) {
        Intent intent;
        switch (id) {
            case "original_home":
                intent = new Intent(this, ControlCenterActivity.class);
                break;
            case "gba":
                intent = new Intent(this, MainActivity.class);
                break;
            case "prompt":
                intent = new Intent(this, PromptKeyboardSetupActivity.class);
                break;
            case "knowledge":
                intent = new Intent(this, WikiGraphActivity.class);
                break;
            case "control":
                intent = new Intent(this, UniversalControlSetupActivity.class);
                break;
            case "settings":
                intent = new Intent(this, PermissionCenterActivity.class);
                break;
            default:
                return;
        }
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private final class SystemGraphView extends View {
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Node> nodes = new ArrayList<>();

        SystemGraphView() {
            super(SystemGraphActivity.this);
            setBackgroundColor(0xff07110d);
            edgePaint.setColor(0x665ce8a4);
            edgePaint.setStrokeWidth(dp(2));
            nodePaint.setColor(0xff173c29);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(14));
            setContentDescription("Amin 系統功能節點地圖");
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            nodes.clear();
            float cx = w / 2f;
            float cy = h / 2f;
            float rx = Math.min(w * 0.36f, dp(170));
            float ry = Math.min(h * 0.31f, dp(245));
            nodes.add(new Node("core", "AMIN OS", cx, cy, dp(54), true));
            nodes.add(new Node("original_home", "原始首頁", cx, cy - ry, dp(48), false));
            nodes.add(new Node("gba", "Pocket GBA", cx + rx, cy - ry * 0.42f, dp(46), false));
            nodes.add(new Node("prompt", "提示詞鍵盤", cx + rx, cy + ry * 0.42f, dp(46), false));
            nodes.add(new Node("knowledge", "知識網路", cx, cy + ry, dp(48), false));
            nodes.add(new Node("control", "全域控制", cx - rx, cy + ry * 0.42f, dp(46), false));
            nodes.add(new Node("settings", "權限設定", cx - rx, cy - ry * 0.42f, dp(46), false));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (nodes.isEmpty()) return;
            Node core = nodes.get(0);
            for (int i = 1; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                canvas.drawLine(core.x, core.y, node.x, node.y, edgePaint);
            }
            for (Node node : nodes) {
                nodePaint.setColor(node.core ? 0xff1fa765 : 0xff173c29);
                canvas.drawCircle(node.x, node.y, node.radius, nodePaint);
                Paint.FontMetrics metrics = textPaint.getFontMetrics();
                float baseline = node.y - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(node.label, node.x, baseline, textPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float x = event.getX();
            float y = event.getY();
            for (Node node : nodes) {
                if (node.core) continue;
                float dx = x - node.x;
                float dy = y - node.y;
                if (dx * dx + dy * dy <= node.radius * node.radius) {
                    openNode(node.id);
                    performClick();
                    return true;
                }
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private static final class Node {
        final String id;
        final String label;
        final float x;
        final float y;
        final float radius;
        final boolean core;

        Node(String id, String label, float x, float y, float radius, boolean core) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.core = core;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
