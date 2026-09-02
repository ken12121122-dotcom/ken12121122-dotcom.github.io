package com.amin.pocketgba;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Floating voice + scrollable chat overlay for live Neural Flow observation. */
final class FloatingVoiceController implements RecognitionListener {
    private static final String PREFS = "amin_floating_voice";
    private static final String KEY_X = "voice_bubble_x";
    private static final String KEY_Y = "voice_bubble_y";
    private static final String WAKE_WORD = "狐狸";
    private static final long LISTENING_TIMEOUT_MS = 8000L;
    private static final long SCAN_STEP_MS = 45L;
    private static final long IDLE_CHECKIN_MS = 15L * 60L * 1000L;
    private static final long IDLE_DISMISS_MS = 60L * 1000L;

    private enum PresenceState { ACTIVE, IDLE_WAIT, DORMANT }

    private final UniversalControlAccessibilityService service;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private final NodeMetadataStore nodeMetadataStore;
    private final ArrayList<String> chatHistory = new ArrayList<>();
    private final Runnable listeningTimeout = this::stopListeningToIdle;
    private final Runnable idleCheckIn = this::showIdleCheckIn;
    private final Runnable dormantTimeout = this::enterDormant;

    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView statusView;
    private TextView scanView;
    private TextView chatView;
    private ScrollView chatScroll;
    private LinearLayout gatePanel;
    private TextView gateTitle;
    private Button gateStop;
    private Button gatePass;
    private Runnable pendingGatePass;
    private Runnable pendingGateBlock;
    private String pendingGateTurnId = "";
    private NeuralFlowTrace.Stage pendingGateStage;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean listening;
    private boolean processing;
    private boolean wakeWordExpected;
    private PresenceState presenceState = PresenceState.ACTIVE;
    private int screenWidth;
    private int screenHeight;

    FloatingVoiceController(UniversalControlAccessibilityService service, WindowManager windowManager) {
        this.service = service;
        this.windowManager = windowManager;
        this.nodeMetadataStore = new NodeMetadataStore(service);
        refreshScreenBounds();
    }

    void show() {
        if (windowManager == null || bubble != null) return;
        refreshScreenBounds();
        createPanel();
        createBubble();
        markActive();
    }

    boolean isVisible() { return bubble != null; }

    void hide() {
        cancelPresenceTimers();
        stopRecognizer(true);
        clearPendingGate();
        removeView(panel);
        removeView(bubble);
        panel = null;
        panelParams = null;
        statusView = null;
        scanView = null;
        chatView = null;
        chatScroll = null;
        gatePanel = null;
        gateTitle = null;
        gateStop = null;
        gatePass = null;
        bubble = null;
        bubbleParams = null;
        wakeWordExpected = false;
        presenceState = PresenceState.DORMANT;
    }

    void destroy() { hide(); }

    void onConfigurationChanged() {
        refreshScreenBounds();
        if (bubbleParams != null) {
            clampBubble();
            updateView(bubble, bubbleParams);
        }
        if (panelParams != null) {
            panelParams.width = Math.min(Math.max(dp(240), screenWidth - dp(24)), dp(420));
            updateView(panel, panelParams);
        }
    }

    private void createBubble() {
        bubble = new TextView(service);
        bubble.setText("🎙");
        bubble.setTextSize(22f);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(Color.WHITE);
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setContentDescription("Amin Neural Flow 語音按鈕");
        bubble.setBackground(circle(0xe61f7a4d, 0xffffffff));
        bubble.setElevation(dp(8));
        bubble.setOnClickListener(v -> toggleListening());

        int size = dp(58);
        bubbleParams = overlayParams(size, size, false, "Amin Neural Flow Voice");
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences prefs = service.getSharedPreferences(PREFS, UniversalControlAccessibilityService.MODE_PRIVATE);
        bubbleParams.x = prefs.getInt(KEY_X, Math.max(0, screenWidth - size - dp(10)));
        bubbleParams.y = prefs.getInt(KEY_Y, Math.max(dp(190), screenHeight / 2));
        clampBubble();

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        startX = bubbleParams.x; startY = bubbleParams.y; dragging = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(7) || Math.abs(dy) > dp(7)) dragging = true;
                        if (dragging) {
                            bubbleParams.x = startX + Math.round(dx);
                            bubbleParams.y = startY + Math.round(dy);
                            clampBubble(); updateView(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) saveBubblePosition(); else view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL: return true;
                    default: return true;
                }
            }
        });
        windowManager.addView(bubble, bubbleParams);
    }

    private void createPanel() {
        panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(9), dp(12), dp(9));
        panel.setBackground(roundRect(0xe314201a, 16f, 0x44ffffff));

        LinearLayout titleRow = new LinearLayout(service);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("LIVE VOICE · Neural Flow", 12f, true, 0xff83e9b1);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button map = new Button(service);
        map.setText("功能地圖");
        map.setAllCaps(false);
        map.setTextSize(11f);
        map.setOnClickListener(v -> openFeatureMap());
        titleRow.addView(map, new LinearLayout.LayoutParams(dp(92), dp(40)));
        panel.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        statusView = text("待命 · 點右側 🎙 開始說話", 12f, true, Color.WHITE);
        panel.addView(statusView, new LinearLayout.LayoutParams(-1, -2));
        scanView = text("—", 11f, false, 0xffa9bbb1);
        scanView.setSingleLine(true);
        scanView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        panel.addView(scanView, new LinearLayout.LayoutParams(-1, dp(24)));

        gatePanel = new LinearLayout(service);
        gatePanel.setOrientation(LinearLayout.HORIZONTAL);
        gatePanel.setGravity(Gravity.CENTER_VERTICAL);
        gatePanel.setPadding(0, dp(4), 0, dp(4));
        gatePanel.setVisibility(View.GONE);
        gateTitle = text("", 12f, true, 0xffffd166);
        gatePanel.addView(gateTitle, new LinearLayout.LayoutParams(0, dp(40), 1f));
        gateStop = new Button(service);
        gateStop.setText("停止");
        gateStop.setAllCaps(false);
        gateStop.setTextSize(11f);
        gateStop.setOnClickListener(v -> resolveGate(false));
        gatePanel.addView(gateStop, new LinearLayout.LayoutParams(dp(76), dp(40)));
        gatePass = new Button(service);
        gatePass.setText("通行");
        gatePass.setAllCaps(false);
        gatePass.setTextSize(11f);
        gatePass.setOnClickListener(v -> resolveGate(true));
        LinearLayout.LayoutParams passParams = new LinearLayout.LayoutParams(dp(76), dp(40));
        passParams.leftMargin = dp(4);
        gatePanel.addView(gatePass, passParams);
        panel.addView(gatePanel, new LinearLayout.LayoutParams(-1, -2));

        chatScroll = new ScrollView(service);
        chatScroll.setVerticalScrollBarEnabled(true);
        chatScroll.setFillViewport(false);
        chatScroll.setSmoothScrollingEnabled(true);
        chatView = text("這裡會顯示你的語音與 LLM 回覆。\n可用手指上下滾動。", 13f, false, 0xffd7e4dc);
        chatView.setLineSpacing(0f, 1.18f);
        chatScroll.addView(chatView, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(132));
        scrollParams.topMargin = dp(4);
        panel.addView(chatScroll, scrollParams);

        panelParams = overlayParams(
                Math.min(Math.max(dp(240), screenWidth - dp(24)), dp(420)),
                dp(274), false, "Amin Neural Flow Voice Chat");
        panelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelParams.y = dp(46);
        windowManager.addView(panel, panelParams);
    }

    private void openFeatureMap() {
        Intent intent = new Intent(service, SystemGraphActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        service.startActivity(intent);
    }

    private void toggleListening() {
        if (presenceState == PresenceState.DORMANT) {
            beginWakeWordListening();
            return;
        }
        if (pendingGatePass != null) { status("先完成目前節點確認"); return; }
        if (processing) { status("上一個訊號仍在處理中"); return; }
        if (presenceState == PresenceState.IDLE_WAIT) markActive();
        if (listening) stopAndProcess(); else startListening();
    }

    private void beginWakeWordListening() {
        if (pendingGatePass != null || processing) return;
        cancelPresenceTimers();
        wakeWordExpected = true;
        if (panel != null) panel.setVisibility(View.VISIBLE);
        if (bubble != null) bubble.setText("🦊");
        status("請說「狐狸」喚醒");
        startListening();
    }

    private void startListening() {
        if (service.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status("需要麥克風權限");
            Toast.makeText(service, "請先開啟 Amin 麥克風權限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!prepareRecognizer()) return;
        cancelPresenceTimers();
        listening = true;
        processing = false;
        bubble.setText("■");
        status(wakeWordExpected ? "正在等你說「狐狸」…" : "正在聆聽… 再點一次可送出");
        handler.removeCallbacks(listeningTimeout);
        handler.postDelayed(listeningTimeout, LISTENING_TIMEOUT_MS);
        try { recognizer.startListening(recognizerIntent); }
        catch (RuntimeException error) {
            listening = false;
            if (bubble != null) bubble.setText(wakeWordExpected ? "🦊" : "🎙");
            status("語音辨識啟動失敗");
        }
    }

    private boolean prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(service)) {
            status("此裝置沒有可用的語音辨識服務"); return false;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(service);
            recognizer.setRecognitionListener(this);
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TAIWAN.toLanguageTag());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.TAIWAN.toLanguageTag());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        }
        return true;
    }

    private void stopAndProcess() {
        if (!listening || recognizer == null) return;
        handler.removeCallbacks(listeningTimeout);
        listening = false; processing = true; bubble.setText("…"); status("正在取得完整語音…");
        recognizer.stopListening();
    }

    private void stopListeningToIdle() {
        if (recognizer != null && listening) recognizer.cancel();
        listening = false; processing = false;
        if (wakeWordExpected) {
            wakeWordExpected = false;
            enterDormant();
            return;
        }
        if (bubble != null) bubble.setText("🎙");
        status("待命 · 點 🎙 開始說話");
        scheduleIdleCheckIn();
    }

    private void stopRecognizer(boolean destroy) {
        handler.removeCallbacks(listeningTimeout);
        if (recognizer != null) {
            recognizer.cancel();
            if (destroy) { recognizer.destroy(); recognizer = null; recognizerIntent = null; }
        }
        listening = false; processing = false;
    }

    private void routeTranscript(String spoken, double confidence) {
        appendChat("你：" + spoken);
        status("Router 分流中…");
        final boolean createRequested = isCreateNodeRequest(spoken);
        final String requestedNodeName = createRequested ? extractNodeName(spoken) : "";
        final String turnId = NeuralFlowTrace.beginTurn(shorten(spoken, 56));
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "enter", "forced gate routing");

        final NodeRegistry.ScanResult scan = NodeRegistry.scanVoice(service, nodeMetadataStore, spoken);
        final NodeRegistry.Match nodeMatch = scan.match;
        final boolean nodeMatched = nodeMatch != null && !createRequested;
        playScan(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "NODE", scan.candidates,
                nodeMatched ? nodeMatch.alias : "NO MATCH", () -> {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY,
                    nodeMatched ? "matched" : "no_match",
                    nodeMatched ? nodeDetail(nodeMatch) : (createRequested ? "create intent continues" : "continue"));
            awaitGate(NodeProtocolGateStore.NODE, turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, () -> {
                if (nodeMatched) {
                    boolean launched = executeNodeMatch(nodeMatch);
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, launched ? "complete" : "node_no_executor", nodeDetail(nodeMatch));
                    appendChat(launched
                            ? "系統：已開啟節點「" + nodeMatch.alias + "」。"
                            : "系統：Node 命中「" + nodeMatch.alias + "」，但目前沒有可執行 route。");
                    finishTurn(launched ? "NODE 已執行" : "NODE 命中但無 executor");
                    return;
                }
                runCommandGate(turnId, spoken, confidence, createRequested, requestedNodeName);
            });
        });
    }

    private void runCommandGate(String turnId, String spoken, double confidence,
                                boolean createRequested, String requestedNodeName) {
        final VoiceCommandParser.ScanResult commandScan = parser.scan(spoken, confidence);
        final VoiceCommandParser.Result parsed = commandScan.getResult();
        final boolean commandMatched = parsed.getStatus() == VoiceCommandParser.Result.Status.MATCHED && !createRequested;
        String finalName = commandMatched && parsed.getCommand() != null ? parsed.getCommand().getTitle() : "NO MATCH";
        playScan(turnId, NeuralFlowTrace.Stage.COMMAND, "COMMAND", commandScan.getCandidates(), finalName, () -> {
            String detail = commandMatched && parsed.getCommand() != null
                    ? parsed.getCommand().getId() + " · " + parsed.getAction().getAction()
                    : "fallback candidate";
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.COMMAND,
                    commandMatched ? "matched" : "no_match", detail);

            awaitGate(NodeProtocolGateStore.COMMAND, turnId, NeuralFlowTrace.Stage.COMMAND, () -> {
                if (commandMatched) {
                    AminActionDispatcher.DispatchResult dispatch = AminActionDispatcher.dispatch(service, parsed.getAction());
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.COMMAND,
                            dispatch.isSuccess() ? "executed" : "execute_failed", dispatch.getMessage());
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER,
                            dispatch.isSuccess() ? "complete" : "command_failed", detail);
                    appendChat("系統：" + dispatch.getMessage());
                    finishTurn(dispatch.isSuccess() ? "COMMAND 已執行" : "COMMAND 執行失敗");
                    return;
                }
                if (createRequested) createNodeAfterReply(turnId, requestedNodeName, spoken);
                NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "complete", "llm_after_required_gates");
                runLlmGate(turnId, spoken, createRequested, requestedNodeName);
            });
        });
    }

    private void runLlmGate(String turnId, String spoken, boolean createRequested, String requestedNodeName) {
        awaitGate(NodeProtocolGateStore.LLM, turnId, NeuralFlowTrace.Stage.LLM_REQUEST,
                () -> sendToLlm(turnId, spoken, createRequested, requestedNodeName));
    }

    private void awaitGate(String key, String turnId, NeuralFlowTrace.Stage stage, Runnable onPass) {
        if (NodeProtocolGateStore.isAuto(service, key)) {
            NeuralFlowTrace.emit(turnId, stage, "gate_auto_pass", key);
            onPass.run();
            return;
        }
        if (pendingGatePass != null) {
            NeuralFlowTrace.emit(turnId, stage, "gate_blocked", "another gate is pending");
            finishTurn("Gate 衝突");
            return;
        }
        pendingGateTurnId = turnId;
        pendingGateStage = stage;
        pendingGatePass = onPass;
        pendingGateBlock = () -> finishTurn("已停止");
        NeuralFlowTrace.emit(turnId, stage, "gate_waiting", key);
        if (gateTitle != null) gateTitle.setText(gateLabel(stage));
        if (gatePanel != null) gatePanel.setVisibility(View.VISIBLE);
        status("等待節點通行");
    }

    private String gateLabel(NeuralFlowTrace.Stage stage) {
        if (stage == NeuralFlowTrace.Stage.NODE_REGISTRY) return "NODE";
        if (stage == NeuralFlowTrace.Stage.COMMAND) return "COMMAND";
        if (stage == NeuralFlowTrace.Stage.LLM_REQUEST) return "LLM";
        return stage == null ? "" : stage.name();
    }

    private void resolveGate(boolean pass) {
        if (pendingGatePass == null) return;
        Runnable next = pass ? pendingGatePass : pendingGateBlock;
        String turnId = pendingGateTurnId;
        NeuralFlowTrace.Stage stage = pendingGateStage;
        clearPendingGate();
        if (stage != null && turnId != null && !turnId.isEmpty()) {
            NeuralFlowTrace.emit(turnId, stage, pass ? "gate_passed" : "gate_blocked", pass ? "manual pass" : "manual stop");
        }
        if (next != null) next.run();
    }

    private void clearPendingGate() {
        pendingGatePass = null;
        pendingGateBlock = null;
        pendingGateTurnId = "";
        pendingGateStage = null;
        if (gatePanel != null) gatePanel.setVisibility(View.GONE);
    }

    private void playScan(String turnId, NeuralFlowTrace.Stage stage, String prefix,
                          List<String> candidates, String finalValue, Runnable done) {
        ArrayList<String> actual = new ArrayList<>();
        if (candidates != null) actual.addAll(candidates);
        playScanStep(turnId, stage, prefix, actual, 0, finalValue, done);
    }

    private void playScanStep(String turnId, NeuralFlowTrace.Stage stage, String prefix,
                              ArrayList<String> candidates, int index, String finalValue, Runnable done) {
        if (index >= candidates.size()) {
            String end = prefix + " ▸ " + finalValue;
            if (scanView != null) scanView.setText(end);
            NeuralFlowTrace.emit(turnId, stage, "scan_complete", end);
            handler.postDelayed(done, SCAN_STEP_MS);
            return;
        }
        String line = prefix + " ▸ " + shorten(candidates.get(index), 46);
        if (scanView != null) scanView.setText(line);
        NeuralFlowTrace.emit(turnId, stage, "scan", line);
        handler.postDelayed(() -> playScanStep(turnId, stage, prefix, candidates, index + 1, finalValue, done), SCAN_STEP_MS);
    }

    private boolean executeNodeMatch(NodeRegistry.Match match) {
        if (match == null || match.node == null) return false;
        JSONObject node = match.node;
        String activity = node.optString("activity", "").trim();
        if (!activity.isEmpty()) {
            try {
                Intent intent = new Intent();
                intent.setClassName(service, activity);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                service.startActivity(intent);
                return true;
            } catch (RuntimeException ignored) { }
        }
        String route = node.optString("route", "").trim();
        if (!route.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(route));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                service.startActivity(intent);
                return true;
            } catch (RuntimeException ignored) { }
        }
        return false;
    }

    private String nodeDetail(NodeRegistry.Match match) {
        if (match == null || match.node == null) return "";
        JSONObject node = match.node;
        String id = node.optString("node_id", node.optString("nodeId", node.optString("id", "")));
        String route = node.optString("route", "");
        return id + " · alias=" + match.alias + (route.isEmpty() ? "" : " · route=" + route);
    }

    private void sendToLlm(String turnId, String spoken, boolean createRequested, String requestedNodeName) {
        if (!LlmConfigStore.hasApiKey(service)) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_ERROR, "blocked", "API Key not configured");
            appendChat("系統：尚未設定 API Key。"); finishTurn("LLM 未設定"); return;
        }

        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_REQUEST, "sending", LlmConfigStore.label(service));
        status("LLM 思考中 · " + LlmConfigStore.label(service));
        ArrayList<LlmClient.Message> messages = new ArrayList<>();
        messages.add(new LlmClient.Message("user", spoken));
        LlmClient.send(service, messages, new LlmClient.Callback() {
            @Override public void onSuccess(String text) {
                handler.post(() -> {
                    String reply = text == null || text.trim().isEmpty() ? "我沒有取得有效回覆。" : text.trim();
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_RESPONSE, "received", shorten(reply, 72));
                    appendChat("AI：" + reply);
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.MEMORY_JUDGE, "skip", "floating live-link test: memory write disabled");
                    finishTurn(createRequested ? "完成 · 節點已先建立，LLM 回覆完成" : "完成 · Memory 測試暫不寫入");
                });
            }
            @Override public void onError(String message) {
                handler.post(() -> {
                    NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_ERROR, "error", shorten(message, 72));
                    appendChat("系統：LLM 錯誤 · " + message); finishTurn("LLM 錯誤");
                });
            }
        });
    }

    private void createNodeAfterReply(String turnId, String requestedName, String sourceText) {
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "create_requested", requestedName);
        JSONObject created = nodeMetadataStore.createCustomNode(requestedName, sourceText);
        if (created == null) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "create_error", requestedName);
            appendChat("系統：節點建立失敗，沒有寫入。");
            return;
        }
        String id = created.optString("node_id", "");
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "created", id);
        appendChat("系統：已新增節點「" + created.optString("name", requestedName) + "」\nID：" + id + "\n可按上方「功能地圖」確認、編輯或刪除。");
    }

    private boolean isCreateNodeRequest(String text) {
        String value = text == null ? "" : text.replace(" ", "");
        return value.contains("新增節點") || value.contains("建立節點") ||
                (value.contains("新增") && value.contains("節點")) ||
                (value.contains("建立") && value.contains("節點"));
    }

    private String extractNodeName(String text) {
        String value = text == null ? "" : text.trim();
        value = value.replace("請幫我", "").replace("幫我", "").replace("請", "");
        value = value.replace("新增一個", "").replace("建立一個", "")
                .replace("新增", "").replace("建立", "").replace("節點", "");
        value = value.replace("叫做", "").replace("叫", "").replace("名稱是", "");
        value = value.replace("「", "").replace("」", "").replace("『", "").replace("』", "")
                .replace("\"", "").replace("。", "").trim();
        return value.isEmpty() ? "聊天測試節點" : shorten(value, 24);
    }

    private void finishTurn(String value) {
        clearPendingGate();
        processing = false; listening = false;
        wakeWordExpected = false;
        markActive();
        if (bubble != null) bubble.setText("🎙");
        status(value + " · 可繼續說下一句");
    }

    private void showIdleCheckIn() {
        if (bubble == null || presenceState == PresenceState.DORMANT) return;
        if (listening || processing || pendingGatePass != null) {
            scheduleIdleCheckIn();
            return;
        }
        presenceState = PresenceState.IDLE_WAIT;
        if (panel != null) panel.setVisibility(View.VISIBLE);
        appendChat("狐狸：還有事情要處理嗎？沒有的話我先退下。");
        status("等待 60 秒 · 無回應就退下");
        Toast.makeText(service, "狐狸：還有事情要處理嗎？", Toast.LENGTH_LONG).show();
        handler.removeCallbacks(dormantTimeout);
        handler.postDelayed(dormantTimeout, IDLE_DISMISS_MS);
    }

    private void enterDormant() {
        cancelPresenceTimers();
        if (listening || processing || pendingGatePass != null) {
            scheduleIdleCheckIn();
            return;
        }
        stopRecognizer(false);
        wakeWordExpected = false;
        presenceState = PresenceState.DORMANT;
        if (panel != null) panel.setVisibility(View.GONE);
        if (bubble != null) {
            bubble.setText("🦊");
            bubble.setContentDescription("狐狸休眠中 · 點一下後說狐狸喚醒");
        }
    }

    private void markActive() {
        cancelPresenceTimers();
        presenceState = PresenceState.ACTIVE;
        if (panel != null) panel.setVisibility(View.VISIBLE);
        if (bubble != null) {
            bubble.setText("🎙");
            bubble.setContentDescription("Amin Neural Flow 語音按鈕");
        }
        scheduleIdleCheckIn();
    }

    private void scheduleIdleCheckIn() {
        if (bubble == null || presenceState == PresenceState.DORMANT || listening || processing || pendingGatePass != null) return;
        handler.removeCallbacks(idleCheckIn);
        handler.removeCallbacks(dormantTimeout);
        handler.postDelayed(idleCheckIn, IDLE_CHECKIN_MS);
    }

    private void cancelPresenceTimers() {
        handler.removeCallbacks(idleCheckIn);
        handler.removeCallbacks(dormantTimeout);
    }

    private boolean containsWakeWord(String text) {
        return text != null && text.replace(" ", "").contains(WAKE_WORD);
    }

    private String stripWakeWord(String text) {
        if (text == null) return "";
        String value = text.trim();
        int index = value.indexOf(WAKE_WORD);
        if (index < 0) return value;
        String before = value.substring(0, index).trim();
        String after = value.substring(index + WAKE_WORD.length()).trim();
        return (before + " " + after).trim();
    }

    private void appendChat(String line) {
        chatHistory.add(line);
        while (chatHistory.size() > 20) chatHistory.remove(0);
        StringBuilder builder = new StringBuilder();
        for (String message : chatHistory) {
            if (builder.length() > 0) builder.append("\n\n");
            builder.append(message);
        }
        if (chatView != null) chatView.setText(builder.toString());
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void status(String value) { if (statusView != null) statusView.setText(value); }

    @Override public void onReadyForSpeech(Bundle params) { status(wakeWordExpected ? "請說「狐狸」" : "請開始說話"); }
    @Override public void onBeginningOfSpeech() { handler.removeCallbacks(listeningTimeout); status("已聽到聲音…"); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { handler.removeCallbacks(listeningTimeout); listening = false; processing = true; if (bubble != null) bubble.setText("…"); status("正在理解…"); }
    @Override public void onError(int error) {
        handler.removeCallbacks(listeningTimeout);
        processing = false; listening = false;
        if (wakeWordExpected) {
            wakeWordExpected = false;
            enterDormant();
            return;
        }
        if (bubble != null) bubble.setText("🎙");
        status("語音辨識失敗 · 再試一次");
        scheduleIdleCheckIn();
    }
    @Override public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) status("聽到：" + shorten(matches.get(0), 28));
    }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override public void onResults(Bundle results) {
        handler.removeCallbacks(listeningTimeout);
        listening = false; processing = true;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (matches == null || matches.isEmpty()) {
            if (wakeWordExpected) { processing = false; wakeWordExpected = false; enterDormant(); }
            else finishTurn("沒有辨識到文字");
            return;
        }
        String spoken = matches.get(0) == null ? "" : matches.get(0).trim();
        double confidence = confidences != null && confidences.length > 0 ? confidences[0] : 1.0d;
        if (spoken.isEmpty()) {
            if (wakeWordExpected) { processing = false; wakeWordExpected = false; enterDormant(); }
            else finishTurn("沒有辨識到文字");
            return;
        }
        if (wakeWordExpected) {
            wakeWordExpected = false;
            processing = false;
            if (!containsWakeWord(spoken)) {
                appendChat("系統：未聽到喚醒詞「狐狸」，繼續休眠。");
                enterDormant();
                return;
            }
            String remainder = stripWakeWord(spoken);
            markActive();
            appendChat("狐狸：我在。");
            if (remainder.isEmpty()) {
                finishTurn("已喚醒");
                return;
            }
            routeTranscript(remainder, confidence);
            return;
        }
        markActive();
        routeTranscript(spoken, confidence);
    }

    private WindowManager.LayoutParams overlayParams(int width, int height, boolean clickThrough, String title) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (clickThrough) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                flags, PixelFormat.TRANSLUCENT);
        params.setTitle(title);
        return params;
    }

    private GradientDrawable circle(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill); drawable.setStroke(dp(1), stroke); return drawable;
    }
    private GradientDrawable roundRect(int fill, float radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp)); drawable.setStroke(dp(1), stroke); return drawable;
    }
    private TextView text(String value, float sizeSp, boolean bold, int color) {
        TextView view = new TextView(service); view.setText(value); view.setTextSize(sizeSp); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD); return view;
    }
    private void refreshScreenBounds() {
        DisplayMetrics metrics = new DisplayMetrics(); windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels;
    }
    private void clampBubble() {
        if (bubbleParams == null) return; int size = dp(58);
        bubbleParams.x = Math.max(0, Math.min(screenWidth - size, bubbleParams.x));
        bubbleParams.y = Math.max(dp(48), Math.min(screenHeight - size - dp(48), bubbleParams.y));
    }
    private void saveBubblePosition() {
        service.getSharedPreferences(PREFS, UniversalControlAccessibilityService.MODE_PRIVATE)
                .edit().putInt(KEY_X, bubbleParams.x).putInt(KEY_Y, bubbleParams.y).apply();
    }
    private void updateView(View view, WindowManager.LayoutParams params) {
        if (view == null || params == null) return;
        try { windowManager.updateViewLayout(view, params); } catch (RuntimeException ignored) { }
    }
    private void removeView(View view) {
        if (view == null) return;
        try { windowManager.removeView(view); } catch (RuntimeException ignored) { }
    }
    private int dp(float value) { return Math.round(value * service.getResources().getDisplayMetrics().density); }
    private static String shorten(String value, int max) {
        if (value == null) return ""; String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, Math.max(1, max - 1)) + "…";
    }
}
