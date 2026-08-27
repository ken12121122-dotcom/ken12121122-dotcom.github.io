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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Neural Flow POC: a live, observable routing sandbox.
 *
 * It mirrors the production priority order (Node Registry -> legacy command -> LLM)
 * but does not execute matched phone-control actions. General chat really calls the
 * configured LLM so the canvas can display an actual request/response path safely.
 */
public final class NeuralFlowActivity extends Activity implements NeuralFlowTrace.Listener {
    private FlowCanvas flowCanvas;
    private TextView statusView;
    private EditText inputView;
    private NodeMetadataStore nodeMetadataStore;
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private String activeTurnId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nodeMetadataStore = new NodeMetadataStore(this);
        configureWindow();
        buildUi();
        NeuralFlowTrace.addListener(this);
        renderLatestTrace();
    }

    @Override protected void onDestroy() {
        NeuralFlowTrace.removeListener(this);
        super.onDestroy();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(0xfff7f8f7);
        getWindow().setNavigationBarColor(0xfff7f8f7);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xfff7f8f7);

        flowCanvas = new FlowCanvas();
        root.addView(flowCanvas, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(8));
        header.setBackgroundColor(0xf7f7f8f7);

        TextView title = text("Neural Flow", 20f, true, 0xff17211b);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = text("Live router → LLM trace · POC sandbox", 12f, false, 0xff6b746e);
        header.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(-1, -2);
        headerParams.gravity = Gravity.TOP;
        root.addView(header, headerParams);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(14), dp(12), dp(14), dp(12));
        composer.setBackgroundColor(0xf7ffffff);

        inputView = new EditText(this);
        inputView.setSingleLine(false);
        inputView.setMinLines(1);
        inputView.setMaxLines(3);
        inputView.setTextSize(14f);
        inputView.setHint("輸入一句話，觀察它經過哪些分流…");
        inputView.setTextColor(0xff17211b);
        inputView.setHintTextColor(0xff8a948e);
        composer.addView(inputView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        composer.addView(row, rowParams);

        statusView = text("待命 · 輸入訊號後按送出", 12f, false, 0xff657068);
        row.addView(statusView, new LinearLayout.LayoutParams(0, -2, 1f));

        Button send = new Button(this);
        send.setText("送出訊號");
        send.setAllCaps(false);
        send.setTextSize(13f);
        send.setOnClickListener(v -> runTrace(inputView.getText().toString()));
        row.addView(send, new LinearLayout.LayoutParams(dp(108), dp(46)));

        FrameLayout.LayoutParams composerParams = new FrameLayout.LayoutParams(-1, -2);
        composerParams.gravity = Gravity.BOTTOM;
        composerParams.leftMargin = dp(12);
        composerParams.rightMargin = dp(12);
        composerParams.bottomMargin = dp(14);
        root.addView(composer, composerParams);

        setContentView(root);
    }

    private void runTrace(String raw) {
        final String spoken = raw == null ? "" : raw.trim();
        if (spoken.isEmpty()) {
            statusView.setText("請先輸入一句話");
            return;
        }

        activeTurnId = NeuralFlowTrace.beginTurn(shorten(spoken, 56));
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.ROUTER, "enter", "priority routing");
        statusView.setText("正在分流…");

        NodeRegistry.Match nodeMatch = NodeRegistry.matchVoice(this, nodeMetadataStore, spoken);
        if (nodeMatch != null) {
            NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "matched", nodeMatch.alias);
            NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.ROUTER, "complete", "node_registry");
            statusView.setText("Node Registry 命中 · POC 不執行跳轉");
            return;
        }
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "no_match", "continue");

        VoiceCommandParser.Result parsed = parser.parse(spoken, 1.0d);
        if (parsed.getStatus() == VoiceCommandParser.Result.Status.MATCHED) {
            NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.COMMAND, "matched", "legacy command");
            NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.ROUTER, "complete", "command");
            statusView.setText("舊指令命中 · POC 僅顯示路徑，不實際控制手機");
            return;
        }
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.COMMAND, "no_match", "fallback to LLM");
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.ROUTER, "complete", "llm");

        if (!LlmConfigStore.hasApiKey(this)) {
            NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.LLM_ERROR, "blocked", "API Key not configured");
            statusView.setText("LLM 路徑成立，但尚未設定 API Key");
            return;
        }

        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.LLM_REQUEST, "sending", LlmConfigStore.label(this));
        statusView.setText("訊號已送往 " + LlmConfigStore.label(this));
        ArrayList<LlmClient.Message> messages = new ArrayList<>();
        messages.add(new LlmClient.Message("user", spoken));
        final String turnId = activeTurnId;
        LlmClient.send(this, messages, new LlmClient.Callback() {
            @Override public void onSuccess(String text) {
                runOnUiThread(() -> {
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_RESPONSE, "received", shorten(text, 72));
                    statusView.setText("LLM 已回覆 · 本回合 Trace 完成");
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_ERROR, "error", shorten(message, 72));
                    statusView.setText("LLM 錯誤 · 可點節點看 Trace 狀態");
                });
            }
        });
    }

    @Override public void onTraceEvent(NeuralFlowTrace.Event event) {
        runOnUiThread(() -> {
            if (activeTurnId.isEmpty() || event.turnId.equals(activeTurnId)) {
                activeTurnId = event.turnId;
                flowCanvas.accept(event);
            }
        });
    }

    private void renderLatestTrace() {
        List<NeuralFlowTrace.Event> events = NeuralFlowTrace.latestTurnEvents();
        if (events.isEmpty()) return;
        activeTurnId = events.get(events.size() - 1).turnId;
        for (NeuralFlowTrace.Event event : events) flowCanvas.accept(event);
    }

    private final class FlowCanvas extends View {
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activeEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cardStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint signal = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF inputRect = new RectF();
        private final RectF routerRect = new RectF();
        private final RectF nodeRect = new RectF();
        private final RectF commandRect = new RectF();
        private final RectF llmRect = new RectF();
        private NeuralFlowTrace.Stage currentStage;
        private NeuralFlowTrace.Stage previousStage;
        private String currentStatus = "idle";
        private String currentDetail = "";
        private float signalProgress = 1f;
        private ValueAnimator animator;

        FlowCanvas() {
            super(NeuralFlowActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            grid.setColor(0xffe7ebe8);
            grid.setStrokeWidth(dp(1));
            edge.setColor(0xffb5c0b9);
            edge.setStrokeWidth(dp(2));
            edge.setStyle(Paint.Style.STROKE);
            activeEdge.setColor(0xff16a05d);
            activeEdge.setStrokeWidth(dp(3));
            activeEdge.setStyle(Paint.Style.STROKE);
            card.setColor(Color.WHITE);
            cardStroke.setColor(0xffd7ded9);
            cardStroke.setStyle(Paint.Style.STROKE);
            cardStroke.setStrokeWidth(dp(1));
            title.setColor(0xff17211b);
            title.setTextSize(sp(14));
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            meta.setColor(0xff6b746e);
            meta.setTextSize(sp(10));
            signal.setColor(0xff16a05d);
            signal.setShadowLayer(dp(5), 0f, 0f, 0x6616a05d);
        }

        void accept(NeuralFlowTrace.Event event) {
            previousStage = currentStage;
            currentStage = event.stage;
            currentStatus = event.status;
            currentDetail = event.detail;
            animateSignal();
        }

        private void animateSignal() {
            if (animator != null) animator.cancel();
            signalProgress = 0f;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(420L);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                signalProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (animator != null) animator.cancel();
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawGrid(canvas);
            layoutNodes();
            drawEdges(canvas);

            drawCard(canvas, inputRect, "INPUT", "Voice / text", 0xffedf7f1, isActive(NeuralFlowTrace.Stage.INPUT));
            drawCard(canvas, routerRect, "ROUTER", "Priority decision", 0xfffff6e9, isActive(NeuralFlowTrace.Stage.ROUTER));
            drawCard(canvas, nodeRect, "NODE", "Node Registry", 0xfff4efff, isActive(NeuralFlowTrace.Stage.NODE_REGISTRY));
            drawCard(canvas, commandRect, "COMMAND", "Legacy command", 0xfffff0f0, isActive(NeuralFlowTrace.Stage.COMMAND));
            drawCard(canvas, llmRect, "LLM", "Gemini / OpenAI / Claude", 0xffeef3ff,
                    isActive(NeuralFlowTrace.Stage.LLM_REQUEST)
                            || isActive(NeuralFlowTrace.Stage.LLM_RESPONSE)
                            || isActive(NeuralFlowTrace.Stage.LLM_ERROR));

            drawMovingSignal(canvas);
            if (currentStage != null) {
                canvas.drawText(currentStage.name() + " · " + currentStatus,
                        dp(18), getHeight() - dp(138), title);
                canvas.drawText(shorten(currentDetail, 48),
                        dp(18), getHeight() - dp(118), meta);
            }
        }

        private void layoutNodes() {
            float w = getWidth();
            float centerX = w * 0.5f;
            float mainW = Math.min(dp(220), w - dp(52));
            float branchW = Math.min(dp(142), (w - dp(48)) / 2f);
            float h = dp(68);
            float left = centerX - mainW / 2f;
            float top = dp(94);
            inputRect.set(left, top, left + mainW, top + h);
            routerRect.set(left, top + dp(118), left + mainW, top + dp(118) + h);
            nodeRect.set(dp(18), top + dp(236), dp(18) + branchW, top + dp(236) + h);
            commandRect.set(w - dp(18) - branchW, top + dp(236), w - dp(18), top + dp(236) + h);
            llmRect.set(left, top + dp(354), left + mainW, top + dp(354) + h);
        }

        private void drawEdges(Canvas canvas) {
            float cx = routerRect.centerX();
            canvas.drawLine(inputRect.centerX(), inputRect.bottom, routerRect.centerX(), routerRect.top, edge);
            canvas.drawLine(cx, routerRect.bottom, nodeRect.centerX(), nodeRect.top, edge);
            canvas.drawLine(cx, routerRect.bottom, commandRect.centerX(), commandRect.top, edge);
            Path llmPath = new Path();
            llmPath.moveTo(nodeRect.centerX(), nodeRect.bottom);
            llmPath.lineTo(llmRect.centerX(), llmRect.top);
            llmPath.moveTo(commandRect.centerX(), commandRect.bottom);
            llmPath.lineTo(llmRect.centerX(), llmRect.top);
            canvas.drawPath(llmPath, edge);
        }

        private void drawMovingSignal(Canvas canvas) {
            if (currentStage == null) return;
            RectF target = rectFor(currentStage);
            RectF source = previousStage == null ? target : rectFor(previousStage);
            if (source == null || target == null) return;
            float sx = source.centerX();
            float sy = source.centerY();
            float tx = target.centerX();
            float ty = target.centerY();
            float x = sx + (tx - sx) * signalProgress;
            float y = sy + (ty - sy) * signalProgress;
            canvas.drawCircle(x, y, dp(7), signal);
        }

        private RectF rectFor(NeuralFlowTrace.Stage stage) {
            if (stage == NeuralFlowTrace.Stage.INPUT) return inputRect;
            if (stage == NeuralFlowTrace.Stage.ROUTER) return routerRect;
            if (stage == NeuralFlowTrace.Stage.NODE_REGISTRY) return nodeRect;
            if (stage == NeuralFlowTrace.Stage.COMMAND) return commandRect;
            if (stage == NeuralFlowTrace.Stage.LLM_REQUEST
                    || stage == NeuralFlowTrace.Stage.LLM_RESPONSE
                    || stage == NeuralFlowTrace.Stage.LLM_ERROR) return llmRect;
            return llmRect;
        }

        private boolean isActive(NeuralFlowTrace.Stage stage) {
            return currentStage == stage;
        }

        private void drawGrid(Canvas canvas) {
            int step = dp(24);
            for (int x = 0; x < getWidth(); x += step) {
                for (int y = 0; y < getHeight(); y += step) canvas.drawCircle(x, y, dp(1), grid);
            }
        }

        private void drawCard(Canvas canvas, RectF rect, String name, String description, int tint, boolean active) {
            Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tintPaint.setColor(tint);
            canvas.drawRoundRect(rect, dp(15), dp(15), card);
            canvas.drawRoundRect(rect, dp(15), dp(15), active ? activeEdge : cardStroke);
            RectF badge = new RectF(rect.left + dp(12), rect.top + dp(11), rect.left + dp(56), rect.top + dp(31));
            canvas.drawRoundRect(badge, dp(10), dp(10), tintPaint);
            canvas.drawText(name, rect.left + dp(65), rect.top + dp(27), title);
            canvas.drawText(description, rect.left + dp(14), rect.top + dp(50), meta);
        }
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
