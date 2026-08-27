package com.amin.pocketgba;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SystemGraphActivity extends Activity {
    private NodeMetadataStore store;
    private SystemGraphView graph;
    private Button editButton;
    private boolean editMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new NodeMetadataStore(this);
        getWindow().setStatusBarColor(0xff07110d);
        getWindow().setNavigationBarColor(0xff07110d);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff07110d);

        graph = new SystemGraphView();
        root.addView(graph, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(12), dp(14), dp(12), dp(6));
        TextView hint = new TextView(this);
        hint.setText("功能節點地圖 · 點節點開啟 · 自訂節點可編輯／刪除");
        hint.setTextColor(0xffb9c9c0);
        hint.setTextSize(13f);
        hint.setGravity(Gravity.CENTER);
        top.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        editButton = new Button(this);
        editButton.setText("編輯節點：關");
        editButton.setAllCaps(false);
        editButton.setOnClickListener(v -> {
            editMode = !editMode;
            editButton.setText(editMode ? "編輯節點：開" : "編輯節點：關");
            Toast.makeText(this, editMode ? "點選節點進入編輯" : "已回到開啟功能模式", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, dp(44));
        editParams.topMargin = dp(4);
        top.addView(editButton, editParams);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2);
        topParams.gravity = Gravity.TOP;
        root.addView(top, topParams);

        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (graph != null) graph.reload();
    }

    private void openNode(Node node) {
        if (node.custom) {
            showCustomNodeEditor(node.id);
            return;
        }
        if (editMode) {
            Intent inspector = new Intent(this, NodeInspectorActivity.class);
            inspector.putExtra(NodeInspectorActivity.EXTRA_NODE_ID, "app:" + node.id);
            startActivity(inspector);
            return;
        }
        Intent intent;
        switch (node.id) {
            case "original_home": intent = new Intent(this, ControlCenterActivity.class); break;
            case "gba": intent = new Intent(this, MainActivity.class); break;
            case "prompt": intent = new Intent(this, PromptKeyboardSetupActivity.class); break;
            case "knowledge": intent = new Intent(this, WikiGraphActivity.class); break;
            case "control": intent = new Intent(this, UniversalControlSetupActivity.class); break;
            case "settings": intent = new Intent(this, PermissionCenterActivity.class); break;
            default: return;
        }
        startActivity(intent);
    }

    private void showCustomNodeEditor(String nodeId) {
        JSONObject node = store.customNode(nodeId);
        if (node == null) {
            Toast.makeText(this, "找不到自訂節點", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(4));
        EditText name = new EditText(this);
        name.setHint("節點名稱");
        name.setText(node.optString("name", node.optString("title", "")));
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));
        EditText description = new EditText(this);
        description.setHint("說明");
        description.setText(node.optString("description", ""));
        description.setMinLines(2);
        box.addView(description, new LinearLayout.LayoutParams(-1, -2));

        new AlertDialog.Builder(this)
                .setTitle("編輯自訂節點")
                .setView(box)
                .setPositiveButton("儲存", (d, w) -> {
                    try {
                        JSONObject voice = node.optJSONObject("voice");
                        if (voice == null) voice = new JSONObject();
                        voice.put("enabled", true);
                        JSONArray aliases = new JSONArray();
                        aliases.put(name.getText().toString().trim());
                        voice.put("aliases", aliases);
                        store.saveEditable(nodeId, name.getText().toString(), description.getText().toString(),
                                node.optString("parent_id", "app:app-core"), voice, node.optJSONObject("storage"));
                        graph.reload();
                    } catch (Exception ignored) { }
                })
                .setNeutralButton("刪除", (d, w) -> confirmDelete(nodeId))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(String nodeId) {
        new AlertDialog.Builder(this)
                .setTitle("刪除此節點？")
                .setMessage("只會刪除這個自訂節點與它的本機 Edge；內建核心節點不受影響。")
                .setPositiveButton("確定刪除", (d, w) -> {
                    store.removeCustomNode(nodeId);
                    graph.reload();
                    Toast.makeText(this, "自訂節點已刪除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override public void onBackPressed() { finish(); }

    private final class SystemGraphView extends View {
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Node> nodes = new ArrayList<>();
        private int width;
        private int height;

        SystemGraphView() {
            super(SystemGraphActivity.this);
            setBackgroundColor(0xff07110d);
            edgePaint.setColor(0x665ce8a4);
            edgePaint.setStrokeWidth(dp(2));
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(12));
            setContentDescription("Amin 系統功能節點地圖");
        }

        void reload() {
            if (width > 0 && height > 0) layoutNodes(width, height);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            width = w;
            height = h;
            layoutNodes(w, h);
        }

        private void layoutNodes(int w, int h) {
            nodes.clear();
            float cx = w / 2f;
            float cy = h / 2f + dp(22);
            float rx = Math.min(w * 0.34f, dp(160));
            float ry = Math.min(h * 0.25f, dp(210));
            nodes.add(new Node("core", "AMIN OS", cx, cy, dp(48), true, false));
            nodes.add(new Node("original_home", "原始首頁", cx, cy - ry, dp(42), false, false));
            nodes.add(new Node("gba", "Pocket GBA", cx + rx, cy - ry * 0.42f, dp(40), false, false));
            nodes.add(new Node("prompt", "提示詞鍵盤", cx + rx, cy + ry * 0.42f, dp(40), false, false));
            nodes.add(new Node("knowledge", "知識網路", cx, cy + ry, dp(42), false, false));
            nodes.add(new Node("control", "全域控制", cx - rx, cy + ry * 0.42f, dp(40), false, false));
            nodes.add(new Node("settings", "權限設定", cx - rx, cy - ry * 0.42f, dp(40), false, false));

            JSONArray custom = store.customNodes();
            int count = custom.length();
            float customRadius = Math.min(w * 0.44f, dp(200));
            for (int i = 0; i < count; i++) {
                JSONObject item = custom.optJSONObject(i);
                if (item == null) continue;
                double angle = -Math.PI / 2d + (Math.PI * 2d * i / Math.max(1, count));
                float x = cx + (float) Math.cos(angle) * customRadius;
                float y = cy + (float) Math.sin(angle) * Math.min(customRadius, dp(250));
                String id = item.optString("node_id", item.optString("nodeId", ""));
                String name = item.optString("name", item.optString("title", "自訂節點"));
                nodes.add(new Node(id, shorten(name, 8), x, y, dp(34), false, true));
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (nodes.isEmpty()) return;
            Node core = nodes.get(0);
            for (int i = 1; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                edgePaint.setColor(node.custom ? 0x887fd8ff : 0x665ce8a4);
                canvas.drawLine(core.x, core.y, node.x, node.y, edgePaint);
            }
            for (Node node : nodes) {
                nodePaint.setColor(node.core ? 0xff1fa765 : node.custom ? 0xff27536a : 0xff173c29);
                canvas.drawCircle(node.x, node.y, node.radius, nodePaint);
                Paint.FontMetrics metrics = textPaint.getFontMetrics();
                float baseline = node.y - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(node.label, node.x, baseline, textPaint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float x = event.getX(), y = event.getY();
            for (Node node : nodes) {
                if (node.core) continue;
                float dx = x - node.x, dy = y - node.y;
                if (dx * dx + dy * dy <= node.radius * node.radius) {
                    openNode(node);
                    performClick();
                    return true;
                }
            }
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }
    }

    private static final class Node {
        final String id, label;
        final float x, y, radius;
        final boolean core, custom;
        Node(String id, String label, float x, float y, float radius, boolean core, boolean custom) {
            this.id = id; this.label = label; this.x = x; this.y = y; this.radius = radius;
            this.core = core; this.custom = custom;
        }
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        String value = text.trim();
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
