package com.amin.pocketgba;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NeuralFlowActivity extends Activity implements NeuralFlowTrace.Listener {
    private FlowCanvas flowCanvas;
    private TextView statusView;
    private TextView turnView;
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
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xfff7f8f7);
        flowCanvas = new FlowCanvas();
        root.addView(flowCanvas, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(8));
        header.setBackgroundColor(0xf7f7f8f7);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text("Neural Flow", 20f, true, 0xff17211b), new LinearLayout.LayoutParams(0, -2, 1f));
        Button featureMap = smallButton("功能地圖");
        featureMap.setOnClickListener(v -> openFeatureMap());
        titleRow.addView(featureMap, new LinearLayout.LayoutParams(dp(96), dp(42)));
        header.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        header.addView(text("Live router → LLM → memory trace · 拖動畫布 · 點節點看 MD", 12f, false, 0xff6b746e),
                new LinearLayout.LayoutParams(-1, -2));
        turnView = text("TURN —", 11f, true, 0xff19794b);
        LinearLayout.LayoutParams turnParams = new LinearLayout.LayoutParams(-1, -2);
        turnParams.topMargin = dp(3);
        header.addView(turnView, turnParams);

        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setGravity(Gravity.CENTER_VERTICAL);
        Button center = smallButton("回到中心");
        center.setOnClickListener(v -> flowCanvas.resetViewport());
        toolsRow.addView(center, new LinearLayout.LayoutParams(0, dp(40), 1f));
        Button recentMd = smallButton("最近節點 MD");
        recentMd.setOnClickListener(v -> showLatestNodeMarkdown());
        LinearLayout.LayoutParams recentParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        recentParams.leftMargin = dp(4);
        toolsRow.addView(recentMd, recentParams);
        Button clear = smallButton("清除 Trace");
        clear.setOnClickListener(v -> clearVisualTrace());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        clearParams.leftMargin = dp(4);
        toolsRow.addView(clear, clearParams);
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, -2);
        toolsParams.topMargin = dp(4);
        header.addView(toolsRow, toolsParams);
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
        button.setTextSize(11f);
        return button;
    }

    private void openFeatureMap() { startActivity(new Intent(this, SystemGraphActivity.class)); }

    private void showLatestNodeMarkdown() {
        String key = flowCanvas.latestKey();
        if (key == null || key.isEmpty()) { statusView.setText("目前沒有最近節點可開啟"); return; }
        showNodeMarkdown(key, flowCanvas.latestEvent(key));
    }

    private void clearVisualTrace() {
        flowCanvas.clearVisualTrace();
        if (turnView != null) turnView.setText("TURN — · 畫面 Trace 已清除");
        if (statusView != null) statusView.setText("已清除畫面 Trace；Runtime 歷史未刪除");
    }

    private void runTrace(String raw) {
        final String spoken = raw == null ? "" : raw.trim();
        if (spoken.isEmpty()) { statusView.setText("請先輸入一句話"); return; }
        hideCandidate();
        activeSourceText = spoken;
        activeAssistantReply = "";
        activeTurnId = NeuralFlowTrace.beginTurn(shorten(spoken, 56));
        if (turnView != null) turnView.setText(activeTurnId);
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.ROUTER, "enter", "forced gate routing");
        statusView.setText("正在分流…");

        final NodeRegistry.Match nodeMatch = NodeRegistry.matchVoice(this, nodeMetadataStore, spoken);
        final boolean nodeMatched = nodeMatch != null;
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.NODE_REGISTRY,
                nodeMatched ? "matched" : "no_match", nodeMatched ? nodeMatch.alias : "continue");
        gateActivity(NodeProtocolGateStore.NODE, activeTurnId, NeuralFlowTrace.Stage.NODE_REGISTRY, () -> {
            if (nodeMatched) {
                NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.ROUTER, "complete", "node_registry");
                statusView.setText("Node Registry 命中 · POC 不執行跳轉");
                return;
            }
            runCommandAfterNode(spoken);
        });
    }

    private void runCommandAfterNode(String spoken) {
        final VoiceCommandParser.Result parsed = parser.parse(spoken, 1.0d);
        final boolean commandMatched = parsed.getStatus() == VoiceCommandParser.Result.Status.MATCHED;
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.COMMAND,
                commandMatched ? "matched" : "no_match", commandMatched ? "legacy command" : "fallback candidate");
        gateActivity(NodeProtocolGateStore.COMMAND, activeTurnId, NeuralFlowTrace.Stage.COMMAND, () -> {
            if (commandMatched) {
                NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.ROUTER, "complete", "command");
                statusView.setText("舊指令命中 · POC 僅顯示路徑，不控制手機");
                return;
            }
            NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.ROUTER, "complete", "llm_after_required_gates");
            sendTraceToLlm(spoken);
        });
    }

    private void gateActivity(String key, String turnId, NeuralFlowTrace.Stage stage, Runnable onPass) {
        if (NodeProtocolGateStore.isAuto(this, key)) {
            NeuralFlowTrace.emit(turnId, stage, "gate_auto_pass", key);
            onPass.run();
            return;
        }
        NeuralFlowTrace.emit(turnId, stage, "gate_waiting", key);
        statusView.setText("等待節點通行");
        new AlertDialog.Builder(this)
                .setTitle(nodeTitle(key))
                .setNegativeButton("停止", (d, which) -> {
                    NeuralFlowTrace.emit(turnId, stage, "gate_blocked", "manual stop");
                    statusView.setText("本輪已停止");
                })
                .setPositiveButton("通行", (d, which) -> {
                    NeuralFlowTrace.emit(turnId, stage, "gate_passed", "manual pass");
                    onPass.run();
                })
                .show();
    }

    private void sendTraceToLlm(String spoken) {
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
            @Override public void onSuccess(String text) { runOnUiThread(() -> handleMemoryAnalysis(turnId, text)); }
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
        if (summary.isEmpty() || activeTurnId.isEmpty()) { statusView.setText("候選記憶不可為空"); return; }
        NeuralMemoryCandidateStore.saveApproved(this, activeTurnId, candidateType, summary,
                activeSourceText, activeAssistantReply);
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.MEMORY_APPROVAL, "approved", shorten(summary, 72));
        NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.MEMORY_STORE, "saved", "POC approved-candidate store only");
        hideCandidate();
        statusView.setText("已批准 · 只存入 Neural Flow POC 候選庫，尚未進正式長期記憶");
    }
    private void rejectCandidate() {
        if (!activeTurnId.isEmpty()) NeuralFlowTrace.emit(activeTurnId, NeuralFlowTrace.Stage.MEMORY_APPROVAL, "rejected", "user rejected candidate");
        hideCandidate();
        statusView.setText("候選記憶已拒絕 · 未保存");
    }

    @Override public void onTraceEvent(NeuralFlowTrace.Event event) {
        runOnUiThread(() -> {
            if (activeTurnId.isEmpty() || event.turnId.equals(activeTurnId)) {
                activeTurnId = event.turnId;
                if (turnView != null) turnView.setText(event.turnId + " · " + event.stage.name());
                flowCanvas.accept(event);
            }
        });
    }

    private void renderLatestTrace() {
        List<NeuralFlowTrace.Event> events = NeuralFlowTrace.latestTurnEvents();
        if (events.isEmpty()) return;
        activeTurnId = events.get(events.size() - 1).turnId;
        for (NeuralFlowTrace.Event event : events) flowCanvas.accept(event);
        NeuralFlowTrace.Event latest = events.get(events.size() - 1);
        if (turnView != null) turnView.setText(latest.turnId + " · " + latest.stage.name());
    }

    private void showNodeMarkdown(String key, NeuralFlowTrace.Event lastEvent) {
        String markdown = markdownFor(key, lastEvent);
        TextView body = text(markdown, 13f, false, 0xff17211b);
        body.setTextIsSelectable(true);
        body.setPadding(dp(16), dp(12), dp(16), dp(24));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(nodeTitle(key) + " · MD")
                .setView(scroll)
                .setPositiveButton("關閉", null)
                .create();
        dialog.setOnShowListener(d -> {
            int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.72f);
            scroll.getLayoutParams().height = maxHeight;
            scroll.requestLayout();
        });
        dialog.show();
    }

    private String markdownFor(String key, NeuralFlowTrace.Event event) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(nodeTitle(key)).append("\n\n");
        md.append("## Node ID\n").append(nodeId(key)).append("\n\n");
        md.append("## 作用\n").append(nodePurpose(key)).append("\n\n");
        md.append("## Input\n").append(nodeInput(key)).append("\n\n");
        md.append("## Output\n").append(nodeOutput(key)).append("\n\n");
        md.append("## 前後流程\n").append(nodeFlow(key)).append("\n\n");
        md.append("## 狀態定義\n").append(nodeStatuses(key)).append("\n\n");
        md.append("## 執行屬性\n").append(nodeExecutionMode(key)).append("\n\n");
        md.append("---\n\n## 最近一次執行暫存\n");
        if (event == null) { md.append("尚無執行紀錄。\n"); return md.toString(); }
        md.append("- turn_id: ").append(event.turnId).append("\n");
        md.append("- timestamp: ").append(formatTime(event.timestampMs)).append("\n");
        md.append("- stage: ").append(event.stage.name()).append("\n");
        md.append("- status: ").append(event.status).append("\n\n");
        md.append("### Input Snapshot\nsource_text: ").append(activeSourceText == null || activeSourceText.isEmpty() ? "—" : activeSourceText).append("\n\n");
        md.append("### Output Snapshot\nstatus: ").append(event.status).append("\n");
        md.append("detail: ").append(event.detail == null || event.detail.isEmpty() ? "—" : event.detail).append("\n");
        if ("llm".equals(key)) md.append("reply: ").append(activeAssistantReply == null || activeAssistantReply.isEmpty() ? "—" : activeAssistantReply).append("\n");
        if ("router".equals(key)) {
            md.append("route_result: ").append(event.detail == null || event.detail.isEmpty() ? "尚未形成結構化 routes[]" : event.detail).append("\n");
            md.append("route_count: 目前 runtime 尚未提供結構化多路分流計數\n");
        }
        md.append("\n> 此區只保留此節點最近一次經過的 runtime snapshot；下一次經過會直接覆蓋。\n");
        return md.toString();
    }

    private String nodeTitle(String key) {
        if ("input".equals(key)) return "INPUT";
        if ("router".equals(key)) return "ROUTER";
        if ("node".equals(key)) return "NODE REGISTRY";
        if ("command".equals(key)) return "COMMAND";
        if ("llm".equals(key)) return "LLM";
        if ("judge".equals(key)) return "MEMORY JUDGE";
        if ("compress".equals(key)) return "MEMORY COMPRESS";
        if ("approval".equals(key)) return "MEMORY APPROVAL";
        if ("store".equals(key)) return "MEMORY STORE";
        return key.toUpperCase(Locale.ROOT);
    }
    private String nodeId(String key) { return "neural:" + key; }
    private String nodePurpose(String key) {
        if ("input".equals(key)) return "接收本輪文字／語音訊號並建立 turn。";
        if ("router".equals(key)) return "控制目前可用的路由。";
        if ("node".equals(key)) return "比對 Node Registry 的語音 alias。";
        if ("command".equals(key)) return "比對既有 Legacy Voice Command。";
        if ("llm".equals(key)) return "一般對話 fallback。";
        if ("judge".equals(key)) return "判斷是否形成候選記憶。";
        if ("compress".equals(key)) return "壓縮候選記憶。";
        if ("approval".equals(key)) return "等待使用者批准或拒絕。";
        if ("store".equals(key)) return "寫入已批准候選。";
        return "Neural Flow 節點。";
    }
    private String nodeInput(String key) {
        if ("input".equals(key)) return "- voice/text payload";
        if ("router".equals(key)) return "- current turn\n- user input";
        if ("node".equals(key)) return "- normalized user input\n- Node Registry\n- voice aliases";
        if ("command".equals(key)) return "- normalized user input\n- legacy command parser";
        if ("llm".equals(key)) return "- user message\n- configured provider/model";
        if ("judge".equals(key)) return "- user text\n- assistant reply";
        if ("compress".equals(key)) return "- memory candidate";
        if ("approval".equals(key)) return "- compressed candidate";
        if ("store".equals(key)) return "- approved candidate";
        return "- runtime payload";
    }
    private String nodeOutput(String key) {
        if ("input".equals(key)) return "- turn_id\n- INPUT trace";
        if ("router".equals(key)) return "- selected route detail\n- ROUTER trace";
        if ("node".equals(key) || "command".equals(key)) return "- matched / no_match / gate_waiting / gate_passed / gate_auto_pass / gate_blocked";
        if ("llm".equals(key)) return "- reply / error";
        if ("judge".equals(key)) return "- skip / candidate / error";
        if ("compress".equals(key)) return "- compressed summary";
        if ("approval".equals(key)) return "- approved / rejected / waiting_user";
        if ("store".equals(key)) return "- saved / error";
        return "- trace event";
    }
    private String nodeFlow(String key) {
        if ("input".equals(key)) return "INPUT → ROUTER";
        if ("router".equals(key)) return "INPUT → ROUTER → NODE → COMMAND → LLM";
        if ("node".equals(key)) return "ROUTER → NODE REGISTRY → COMMAND 或結束";
        if ("command".equals(key)) return "NODE REGISTRY → COMMAND → LLM 或結束";
        if ("llm".equals(key)) return "COMMAND → LLM → MEMORY JUDGE";
        if ("judge".equals(key)) return "LLM → MEMORY JUDGE → MEMORY COMPRESS 或結束";
        if ("compress".equals(key)) return "MEMORY JUDGE → COMPRESS → APPROVAL";
        if ("approval".equals(key)) return "COMPRESS → APPROVAL → STORE 或結束";
        if ("store".equals(key)) return "APPROVAL → STORE";
        return "依 Neural Flow trace。";
    }
    private String nodeStatuses(String key) {
        if ("node".equals(key) || "command".equals(key)) return "- matched\n- no_match\n- gate_waiting\n- gate_passed\n- gate_auto_pass\n- gate_blocked";
        if ("llm".equals(key)) return "- sending\n- received\n- blocked\n- error";
        if ("judge".equals(key)) return "- analyzing\n- skip\n- candidate\n- error\n- invalid_json";
        if ("approval".equals(key)) return "- waiting_user\n- approved\n- rejected";
        if ("store".equals(key)) return "- saved";
        return "- received / enter / complete（依 stage）";
    }
    private String nodeExecutionMode(String key) {
        if ("node".equals(key) || "command".equals(key)) return "通行燈亮時自動通行；暗時必須人工確認。Gate 不可 bypass。";
        if ("store".equals(key)) return "實際寫入，但僅限隔離的 Neural Flow POC store。";
        return "此節點依目前 runtime 實際 trace 顯示；MD 不改變路由決策。";
    }
    private String formatTime(long timeMs) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.TAIWAN).format(new Date(timeMs));
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
        private final Paint gateOn = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gateOff = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF inputRect = new RectF(), routerRect = new RectF(), nodeRect = new RectF(), commandRect = new RectF(), llmRect = new RectF(), judgeRect = new RectF(), compressRect = new RectF(), approvalRect = new RectF(), storeRect = new RectF();
        private final Map<String, NeuralFlowTrace.Event> latestRuntime = new HashMap<>();
        private NeuralFlowTrace.Stage currentStage, previousStage;
        private String currentStatus = "idle", currentDetail = "", latestKey = "";
        private float signalProgress = 1f, offsetX, offsetY, contentWidth, contentHeight, downX, downY, startOffsetX, startOffsetY;
        private ValueAnimator animator;
        private boolean dragging, initialPositioned;

        FlowCanvas() {
            super(NeuralFlowActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setClickable(true);
            grid.setColor(0xffe7ebe8); grid.setStrokeWidth(dp(1));
            edge.setColor(0xffb5c0b9); edge.setStrokeWidth(dp(2)); edge.setStyle(Paint.Style.STROKE);
            activeEdge.setColor(0xff16a05d); activeEdge.setStrokeWidth(dp(3)); activeEdge.setStyle(Paint.Style.STROKE);
            card.setColor(Color.WHITE);
            cardStroke.setColor(0xffd7ded9); cardStroke.setStyle(Paint.Style.STROKE); cardStroke.setStrokeWidth(dp(1));
            title.setColor(0xff17211b); title.setTextSize(sp(12)); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            meta.setColor(0xff6b746e); meta.setTextSize(sp(9));
            signal.setColor(0xff16a05d); signal.setShadowLayer(dp(5), 0f, 0f, 0x6616a05d);
            gateOn.setColor(0xff16a05d); gateOn.setShadowLayer(dp(4), 0f, 0f, 0x6616a05d);
            gateOff.setColor(0xff9da6a0);
        }

        void accept(NeuralFlowTrace.Event event) {
            previousStage = currentStage; currentStage = event.stage; currentStatus = event.status; currentDetail = event.detail;
            latestKey = keyFor(event.stage); latestRuntime.put(latestKey, event); animateSignal();
        }
        String latestKey() { return latestKey; }
        NeuralFlowTrace.Event latestEvent(String key) { return latestRuntime.get(key); }
        void clearVisualTrace() {
            if (animator != null) animator.cancel();
            latestRuntime.clear(); latestKey = ""; previousStage = null; currentStage = null; currentStatus = "idle"; currentDetail = ""; signalProgress = 1f; invalidate();
        }
        void resetViewport() {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            offsetX = (getWidth() - contentWidth) / 2f; offsetY = -dp(36); clampOffset(); invalidate();
        }
        private void animateSignal() {
            if (animator != null) animator.cancel();
            signalProgress = 0f; animator = ValueAnimator.ofFloat(0f, 1f); animator.setDuration(420L); animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> { signalProgress = (float) a.getAnimatedValue(); invalidate(); }); animator.start();
        }
        @Override protected void onDetachedFromWindow() { if (animator != null) animator.cancel(); super.onDetachedFromWindow(); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            contentWidth = Math.max(dp(620), w + dp(260)); contentHeight = Math.max(dp(920), h + dp(260));
            if (!initialPositioned) { offsetX = (w - contentWidth) / 2f; offsetY = -dp(36); clampOffset(); initialPositioned = true; } else clampOffset();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.translate(offsetX, offsetY); drawGrid(canvas); layoutNodes(); drawEdges(canvas);
            drawCard(canvas, inputRect, "INPUT", "Voice / text", 0xffedf7f1, isActive(NeuralFlowTrace.Stage.INPUT), "input");
            drawCard(canvas, routerRect, "ROUTER", "Priority", 0xfffff6e9, isActive(NeuralFlowTrace.Stage.ROUTER), "router");
            drawCard(canvas, nodeRect, "NODE", "Registry", 0xfff4efff, isActive(NeuralFlowTrace.Stage.NODE_REGISTRY), "node");
            drawCard(canvas, commandRect, "COMMAND", "Legacy", 0xfffff0f0, isActive(NeuralFlowTrace.Stage.COMMAND), "command");
            drawCard(canvas, llmRect, "LLM", "Reply", 0xffeef3ff, isActive(NeuralFlowTrace.Stage.LLM_REQUEST) || isActive(NeuralFlowTrace.Stage.LLM_RESPONSE) || isActive(NeuralFlowTrace.Stage.LLM_ERROR), "llm");
            drawCard(canvas, judgeRect, "MEM JUDGE", "Keep?", 0xfffff7df, isActive(NeuralFlowTrace.Stage.MEMORY_JUDGE) || isActive(NeuralFlowTrace.Stage.MEMORY_ERROR), "judge");
            drawCard(canvas, compressRect, "COMPRESS", "Candidate", 0xffedf7f1, isActive(NeuralFlowTrace.Stage.MEMORY_COMPRESS), "compress");
            drawCard(canvas, approvalRect, "APPROVAL", "You decide", 0xfffff0f0, isActive(NeuralFlowTrace.Stage.MEMORY_APPROVAL), "approval");
            drawCard(canvas, storeRect, "STORE", "POC only", 0xffeef3ff, isActive(NeuralFlowTrace.Stage.MEMORY_STORE), "store");
            drawMovingSignal(canvas);
            if (currentStage != null) {
                canvas.drawText(currentStage.name() + " · " + currentStatus, dp(18), contentHeight - dp(82), title);
                canvas.drawText(shorten(currentDetail, 60), dp(18), contentHeight - dp(62), meta);
            }
            canvas.restore();
        }
        private void layoutNodes() {
            float centerX = contentWidth * 0.5f, mainW = dp(210), branchW = dp(154), h = dp(54), left = centerX - mainW / 2f, top = dp(118);
            inputRect.set(left, top, left + mainW, top + h);
            routerRect.set(left, top + dp(88), left + mainW, top + dp(88) + h);
            nodeRect.set(centerX - dp(186), top + dp(176), centerX - dp(186) + branchW, top + dp(176) + h);
            commandRect.set(centerX + dp(32), top + dp(176), centerX + dp(32) + branchW, top + dp(176) + h);
            llmRect.set(left, top + dp(264), left + mainW, top + dp(264) + h);
            judgeRect.set(centerX - dp(186), top + dp(352), centerX - dp(186) + branchW, top + dp(352) + h);
            compressRect.set(centerX + dp(32), top + dp(352), centerX + dp(32) + branchW, top + dp(352) + h);
            approvalRect.set(centerX - dp(186), top + dp(440), centerX - dp(186) + branchW, top + dp(440) + h);
            storeRect.set(centerX + dp(32), top + dp(440), centerX + dp(32) + branchW, top + dp(440) + h);
        }
        private void drawEdges(Canvas canvas) {
            float cx = routerRect.centerX();
            canvas.drawLine(inputRect.centerX(), inputRect.bottom, routerRect.centerX(), routerRect.top, edge);
            canvas.drawLine(cx, routerRect.bottom, nodeRect.centerX(), nodeRect.top, edge);
            canvas.drawLine(cx, routerRect.bottom, commandRect.centerX(), commandRect.top, edge);
            Path llmPath = new Path(); llmPath.moveTo(nodeRect.centerX(), nodeRect.bottom); llmPath.lineTo(llmRect.centerX(), llmRect.top); llmPath.moveTo(commandRect.centerX(), commandRect.bottom); llmPath.lineTo(llmRect.centerX(), llmRect.top); canvas.drawPath(llmPath, edge);
            canvas.drawLine(llmRect.centerX(), llmRect.bottom, judgeRect.centerX(), judgeRect.top, edge);
            canvas.drawLine(judgeRect.centerX(), judgeRect.bottom, compressRect.centerX(), compressRect.top, edge);
            canvas.drawLine(compressRect.centerX(), compressRect.bottom, approvalRect.centerX(), approvalRect.top, edge);
            canvas.drawLine(approvalRect.centerX(), approvalRect.bottom, storeRect.centerX(), storeRect.top, edge);
        }
        private void drawMovingSignal(Canvas canvas) {
            if (currentStage == null) return;
            RectF target = rectFor(currentStage), source = previousStage == null ? target : rectFor(previousStage);
            if (source == null || target == null) return;
            float x = source.centerX() + (target.centerX() - source.centerX()) * signalProgress;
            float y = source.centerY() + (target.centerY() - source.centerY()) * signalProgress;
            canvas.drawCircle(x, y, dp(7), signal);
        }
        private RectF rectFor(NeuralFlowTrace.Stage stage) {
            if (stage == NeuralFlowTrace.Stage.INPUT) return inputRect;
            if (stage == NeuralFlowTrace.Stage.ROUTER) return routerRect;
            if (stage == NeuralFlowTrace.Stage.NODE_REGISTRY) return nodeRect;
            if (stage == NeuralFlowTrace.Stage.COMMAND) return commandRect;
            if (stage == NeuralFlowTrace.Stage.LLM_REQUEST || stage == NeuralFlowTrace.Stage.LLM_RESPONSE || stage == NeuralFlowTrace.Stage.LLM_ERROR) return llmRect;
            if (stage == NeuralFlowTrace.Stage.MEMORY_JUDGE || stage == NeuralFlowTrace.Stage.MEMORY_ERROR) return judgeRect;
            if (stage == NeuralFlowTrace.Stage.MEMORY_COMPRESS) return compressRect;
            if (stage == NeuralFlowTrace.Stage.MEMORY_APPROVAL) return approvalRect;
            if (stage == NeuralFlowTrace.Stage.MEMORY_STORE) return storeRect;
            return llmRect;
        }
        private boolean isActive(NeuralFlowTrace.Stage stage) { return currentStage == stage; }
        private void drawGrid(Canvas canvas) {
            int step = dp(24); for (int x = 0; x < contentWidth; x += step) for (int y = 0; y < contentHeight; y += step) canvas.drawCircle(x, y, dp(1), grid);
        }
        private void drawCard(Canvas canvas, RectF rect, String name, String description, int tint, boolean active, String key) {
            Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG); tintPaint.setColor(tint);
            canvas.drawRoundRect(rect, dp(13), dp(13), card); canvas.drawRoundRect(rect, dp(13), dp(13), active ? activeEdge : cardStroke);
            RectF badge = new RectF(rect.left + dp(9), rect.top + dp(8), rect.left + dp(42), rect.top + dp(24));
            canvas.drawRoundRect(badge, dp(8), dp(8), tintPaint);
            canvas.drawText(name, rect.left + dp(47), rect.top + dp(21), title); canvas.drawText(description, rect.left + dp(10), rect.top + dp(41), meta);
            canvas.drawCircle(rect.right - dp(12), rect.top + dp(12), dp(5), NodeProtocolGateStore.isAuto(NeuralFlowActivity.this, key) ? gateOn : gateOff);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downX = event.getX(); downY = event.getY(); startOffsetX = offsetX; startOffsetY = offsetY; dragging = false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX, dy = event.getY() - downY;
                    if (Math.abs(dx) > dp(7) || Math.abs(dy) > dp(7)) dragging = true;
                    if (dragging) { offsetX = startOffsetX + dx; offsetY = startOffsetY + dy; clampOffset(); invalidate(); }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) { performClick(); handleNodeTap(event.getX() - offsetX, event.getY() - offsetY); }
                    return true;
                case MotionEvent.ACTION_CANCEL: return true;
                default: return true;
            }
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        private void clampOffset() {
            float minX = Math.min(0f, getWidth() - contentWidth), minY = Math.min(0f, getHeight() - contentHeight);
            offsetX = Math.max(minX, Math.min(0f, offsetX)); offsetY = Math.max(minY, Math.min(0f, offsetY));
        }
        private void handleNodeTap(float x, float y) {
            layoutNodes(); String key = null; RectF rect = null;
            if (inputRect.contains(x, y)) { key = "input"; rect = inputRect; }
            else if (routerRect.contains(x, y)) { key = "router"; rect = routerRect; }
            else if (nodeRect.contains(x, y)) { key = "node"; rect = nodeRect; }
            else if (commandRect.contains(x, y)) { key = "command"; rect = commandRect; }
            else if (llmRect.contains(x, y)) { key = "llm"; rect = llmRect; }
            else if (judgeRect.contains(x, y)) { key = "judge"; rect = judgeRect; }
            else if (compressRect.contains(x, y)) { key = "compress"; rect = compressRect; }
            else if (approvalRect.contains(x, y)) { key = "approval"; rect = approvalRect; }
            else if (storeRect.contains(x, y)) { key = "store"; rect = storeRect; }
            if (key == null || rect == null) return;
            float gx = rect.right - dp(12), gy = rect.top + dp(12), ddx = x - gx, ddy = y - gy;
            if (ddx * ddx + ddy * ddy <= dp(15) * dp(15)) {
                NodeProtocolGateStore.toggle(NeuralFlowActivity.this, key); invalidate(); return;
            }
            showNodeMarkdown(key, latestRuntime.get(key));
        }
        private String keyFor(NeuralFlowTrace.Stage stage) {
            if (stage == NeuralFlowTrace.Stage.INPUT) return "input";
            if (stage == NeuralFlowTrace.Stage.ROUTER) return "router";
            if (stage == NeuralFlowTrace.Stage.NODE_REGISTRY) return "node";
            if (stage == NeuralFlowTrace.Stage.COMMAND) return "command";
            if (stage == NeuralFlowTrace.Stage.LLM_REQUEST || stage == NeuralFlowTrace.Stage.LLM_RESPONSE || stage == NeuralFlowTrace.Stage.LLM_ERROR) return "llm";
            if (stage == NeuralFlowTrace.Stage.MEMORY_JUDGE || stage == NeuralFlowTrace.Stage.MEMORY_ERROR) return "judge";
            if (stage == NeuralFlowTrace.Stage.MEMORY_COMPRESS) return "compress";
            if (stage == NeuralFlowTrace.Stage.MEMORY_APPROVAL) return "approval";
            if (stage == NeuralFlowTrace.Stage.MEMORY_STORE) return "store";
            return "llm";
        }
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return view;
    }
    private String shorten(String value, int max) {
        if (value == null) return ""; String clean = value.replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
