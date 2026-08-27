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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Neural Flow POC: a live, observable routing + memory sandbox.
 *
 * It mirrors the production priority order (Node Registry -> legacy command -> LLM)
 * without executing matched phone-control actions. General chat calls the configured
 * LLM. After a reply, one additional memory-analysis LLM call returns a structured
 * remember/no-remember decision plus a compressed candidate. Only user-approved
 * candidates are stored, and only inside the isolated POC store.
 */
public final class NeuralFlowActivity extends Activity implements NeuralFlowTrace.Listener {
    private FlowCanvas flowCanvas;
    private TextView statusView;
    private EditText inputView;
    private LinearLayout candidatePanel;
    private EditText candidateSummaryView;
    private TextView candidateMetaView;
    private NodeMetadataStore nodeMetadataStore;
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private String activeTurnId = "";
    private String activeSourceText = "";
    private String activeAssistantReply = "";
    private String candidateType = "other";

    @Override protected void onCreate(Bundle savedInstanceState) {
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
        header.addView(text("Neural Flow", 20f, true, 0xff17211b), new LinearLayout.LayoutParams(-1, -2));
        header.addView(text("Live router → LLM → memory trace · POC sandbox", 12f, false, 0xff6b746e),
                new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(-1, -2);
        headerParams.gravity = Gravity.TOP;
        root.addView(header, headerParams);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(14), dp(10), dp(14), dp(10));
        composer.setBackgroundColor(0xf7ffffff);

        candidatePanel = new LinearLayout(this);
        candidatePanel.setOrientation(LinearLayout.VERTICAL);
        candidatePanel.setPadding(dp(10), dp(8), dp(10), dp(8));
        candidatePanel.setBackgroundColor(0xfff2f7f4);
        candidatePanel.setVisibility(View.GONE);
        candidateMetaView = text("候選記憶", 11f, true, 0xff19794b);
        candidatePanel.addView(candidateMetaView, new LinearLayout.LayoutParams(-1, -2));
        candidateSummaryView = new EditText(this);
        candidateSummaryView.setTextSize(13f);
        candidateSummaryView.setTextColor(0xff17211b);
        candidateSummaryView.setMinLines(1);
        candidateSummaryView.setMaxLines(3);
        candidatePanel.addView(candidateSummaryView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout approvalRow = new LinearLayout(this);
        approvalRow.setOrientation(LinearLayout.HORIZONTAL);
        approvalRow.setGravity(Gravity.END);
        Button reject = smallButton("不要");
        reject.setOnClickListener(v -> rejectCandidate());
        approvalRow.addView(reject, new LinearLayout.LayoutParams(dp(76), dp(42)));
        Button approve = smallButton("批准");
        approve.setOnClickListener(v -> approveCandidate());
        LinearLayout.LayoutParams approveParams = new LinearLayout.LayoutParams(dp(76), dp(42));
        approveParams.leftMargin = dp(6);
        approvalRow.addView(approve, approveParams);
        candidatePanel.addView(approvalRow, new LinearLayout.LayoutParams(-1, -2));
        composer.addView(candidatePanel, new LinearLayout.LayoutParams(-1, -2));

        inputView = new EditText(this);
        inputView.setSingleLine(false);
        inputView.setMinLines(1);
        inputView.setMaxLines(3);
        inputView.setTextSize(14f);
        inputView.setHint("輸入一句話，觀察 Router、LLM 與 Memory…");
        inputView.setTextColor(0xff17211b);
        inputView.setHintTextColor(0xff8a948e);
        composer.addView(inputView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(4);
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

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12f);
        return button;
    }

    private void runTrace(String raw) {
        final String spoken = raw == null ? "" : raw.trim();
        if (spoken.isEmpty()) {
            statusView.setText("請先輸入一句話");
            return;
        }

        hideCandidate();
        activeSourceText = spoken;
        activeAssistantReply = "";
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
            statusView.setText("舊指令命中 · POC 僅顯示路徑，不控制手機");
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
                    String reply = text == null ? "" : text.trim();
                    activeAssistantReply = reply;
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_RESPONSE, "received", shorten(reply, 72));
                    statusView.setText("LLM 已回覆 · 正在判斷是否值得記憶");
                    analyzeMemory(turnId, spoken, reply);
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_ERROR, "error", shorten(message, 72));
                    statusView.setText("LLM 錯誤 · 本回合停止");
                });
            }
        });
    }

    private void analyzeMemory(String turnId, String userText, String assistantReply) {
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.MEMORY_JUDGE, "analyzing", "judge + compress in one call");
        ArrayList<LlmClient.Message> memoryMessages = new ArrayList<>();
        String prompt = "你是個人助理的記憶萃取器。只輸出一個 JSON 物件，不要 Markdown。"
                + "欄位必須是 remember(boolean), type(string), summary(string), reason(string)。"
                + "只有穩定偏好、明確決策、長期目標、可重用工作規則、重要個人事實才 remember=true。"
                + "一次性問題、寒暄、暫時資訊、模型回答本身不要記。"
                + "summary 必須忠實、短、可獨立理解，不可補造未提供事實。\n"
                + "USER: " + userText + "\nASSISTANT: " + assistantReply;
        memoryMessages.add(new LlmClient.Message("user", prompt));
        LlmClient.send(this, memoryMessages, new LlmClient.Callback() {
            @Override public void onSuccess(String text) {
                runOnUiThread(() -> handleMemoryAnalysis(turnId, text));
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.MEMORY_ERROR, "error", shorten(message, 72));
                    statusView.setText("回答完成，但記憶判斷失敗；不會寫入任何記憶");
                });
            }
        });
    }

    private void handleMemoryAnalysis(String turnId, String raw) {
        try {
            JSONObject result = parseJsonObject(raw);
            boolean remember = result.optBoolean("remember", false);
            String type = result.optString("type", "other").trim();
            String summary = result.optString("summary", "").trim();
            String reason = result.optString("reason", "").trim();
            if (!remember || summary.isEmpty()) {
                NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.MEMORY_JUDGE, "skip",
                        reason.isEmpty() ? "not durable memory" : reason);
                statusView.setText("回答完成 · Memory 判斷：不需要保存");
                return;
            }

            candidateType = type.isEmpty() ? "other" : type;
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.MEMORY_JUDGE, "candidate",
                    reason.isEmpty() ? candidateType : reason);
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.MEMORY_COMPRESS, "generated", shorten(summary, 72));
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.MEMORY_APPROVAL, "waiting_user", candidateType);
            showCandidate(candidateType, summary);
            statusView.setText("發現候選記憶 · 等待你的批准");
        } catch (Exception error) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.MEMORY_ERROR, "invalid_json", shorten(raw, 72));
            statusView.setText("回答完成，但 Memory JSON 無法驗證；不保存");
        }
    }

    private JSONObject parseJsonObject(String raw) throws Exception {
        if (raw == null) throw new IllegalArgumentException("empty");
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("json not found");
        return new JSONObject(raw.substring(start, end + 1));
    }

    private void showCandidate(String type, String summary) {
        candidateMetaView.setText("🧠 候選記憶 · " + type + " · 可直接修改後批准");
        candidateSummaryView.setText(summary);
        candidatePanel.setVisibility(View.VISIBLE);
    }

    private void hideCandidate() {
        if (candidatePanel != null) candidatePanel.setVisibility(View.GONE);
        if (candidateSummaryView != null) candidateSummaryView.setText("");
    }

    private void approveCandidate() {
        String summary = candidateSummaryView.getText().toString().trim();
        if (summary.isEmpty() || activeTurnId.isEmpty()) {
            statusView.setText("候選記憶不可為空");
            return;
        }
        NeuralMemoryCandidateStore.saveApproved(this, activeTurnId, candidateType, summary,
                activeSourceText, activeAssistantReply);
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.MEMORY_APPROVAL, "approved", shorten(summary, 72));
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.MEMORY_STORE, "saved", "POC approved-candidate store only");
        hideCandidate();
        statusView.setText("已批准 · 只存入 Neural Flow POC 候選庫，尚未進正式長期記憶");
    }

    private void rejectCandidate() {
        if (!activeTurnId.isEmpty()) {
            NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.MEMORY_APPROVAL, "rejected", "user rejected candidate");
        }
        hideCandidate();
        statusView.setText("候選記憶已拒絕 · 未保存");
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
        private final RectF judgeRect = new RectF();
        private final RectF compressRect = new RectF();
        private final RectF approvalRect = new RectF();
        private final RectF storeRect = new RectF();
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
            title.setTextSize(sp(12));
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            meta.setColor(0xff6b746e);
            meta.setTextSize(sp(9));
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
            drawCard(canvas, routerRect, "ROUTER", "Priority", 0xfffff6e9, isActive(NeuralFlowTrace.Stage.ROUTER));
            drawCard(canvas, nodeRect, "NODE", "Registry", 0xfff4efff, isActive(NeuralFlowTrace.Stage.NODE_REGISTRY));
            drawCard(canvas, commandRect, "COMMAND", "Legacy", 0xfffff0f0, isActive(NeuralFlowTrace.Stage.COMMAND));
            drawCard(canvas, llmRect, "LLM", "Reply", 0xffeef3ff,
                    isActive(NeuralFlowTrace.Stage.LLM_REQUEST)
                            || isActive(NeuralFlowTrace.Stage.LLM_RESPONSE)
                            || isActive(NeuralFlowTrace.Stage.LLM_ERROR));
            drawCard(canvas, judgeRect, "MEM JUDGE", "Keep?", 0xfffff7df,
                    isActive(NeuralFlowTrace.Stage.MEMORY_JUDGE) || isActive(NeuralFlowTrace.Stage.MEMORY_ERROR));
            drawCard(canvas, compressRect, "COMPRESS", "Candidate", 0xffedf7f1,
                    isActive(NeuralFlowTrace.Stage.MEMORY_COMPRESS));
            drawCard(canvas, approvalRect, "APPROVAL", "You decide", 0xfffff0f0,
                    isActive(NeuralFlowTrace.Stage.MEMORY_APPROVAL));
            drawCard(canvas, storeRect, "STORE", "POC only", 0xffeef3ff,
                    isActive(NeuralFlowTrace.Stage.MEMORY_STORE));

            drawMovingSignal(canvas);
            if (currentStage != null) {
                canvas.drawText(currentStage.name() + " · " + currentStatus,
                        dp(14), getHeight() - dp(152), title);
                canvas.drawText(shorten(currentDetail, 48),
                        dp(14), getHeight() - dp(134), meta);
            }
        }

        private void layoutNodes() {
            float w = getWidth();
            float centerX = w * 0.5f;
            float mainW = Math.min(dp(190), w - dp(48));
            float branchW = Math.min(dp(132), (w - dp(44)) / 2f);
            float h = dp(50);
            float left = centerX - mainW / 2f;
            float top = dp(70);
            inputRect.set(left, top, left + mainW, top + h);
            routerRect.set(left, top + dp(76), left + mainW, top + dp(76) + h);
            nodeRect.set(dp(14), top + dp(152), dp(14) + branchW, top + dp(152) + h);
            commandRect.set(w - dp(14) - branchW, top + dp(152), w - dp(14), top + dp(152) + h);
            llmRect.set(left, top + dp(228), left + mainW, top + dp(228) + h);
            judgeRect.set(dp(14), top + dp(304), dp(14) + branchW, top + dp(304) + h);
            compressRect.set(w - dp(14) - branchW, top + dp(304), w - dp(14), top + dp(304) + h);
            approvalRect.set(dp(14), top + dp(380), dp(14) + branchW, top + dp(380) + h);
            storeRect.set(w - dp(14) - branchW, top + dp(380), w - dp(14), top + dp(380) + h);
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
            canvas.drawLine(llmRect.centerX(), llmRect.bottom, judgeRect.centerX(), judgeRect.top, edge);
            canvas.drawLine(judgeRect.centerX(), judgeRect.bottom, compressRect.centerX(), compressRect.top, edge);
            canvas.drawLine(compressRect.centerX(), compressRect.bottom, approvalRect.centerX(), approvalRect.top, edge);
            canvas.drawLine(approvalRect.centerX(), approvalRect.bottom, storeRect.centerX(), storeRect.top, edge);
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
            if (stage == NeuralFlowTrace.Stage.MEMORY_JUDGE || stage == NeuralFlowTrace.Stage.MEMORY_ERROR) return judgeRect;
            if (stage == NeuralFlowTrace.Stage.MEMORY_COMPRESS) return compressRect;
            if (stage == NeuralFlowTrace.Stage.MEMORY_APPROVAL) return approvalRect;
            if (stage == NeuralFlowTrace.Stage.MEMORY_STORE) return storeRect;
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
            canvas.drawRoundRect(rect, dp(13), dp(13), card);
            canvas.drawRoundRect(rect, dp(13), dp(13), active ? activeEdge : cardStroke);
            RectF badge = new RectF(rect.left + dp(9), rect.top + dp(8), rect.left + dp(42), rect.top + dp(24));
            canvas.drawRoundRect(badge, dp(8), dp(8), tintPaint);
            canvas.drawText(name, rect.left + dp(47), rect.top + dp(21), title);
            canvas.drawText(description, rect.left + dp(10), rect.top + dp(39), meta);
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
