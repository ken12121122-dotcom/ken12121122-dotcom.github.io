package com.amin.pocketgba;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import java.util.Locale;

/** Floating voice + scrollable chat overlay for live Neural Flow observation. */
final class FloatingVoiceController implements RecognitionListener {
    private static final String PREFS = "amin_floating_voice";
    private static final String KEY_X = "voice_bubble_x";
    private static final String KEY_Y = "voice_bubble_y";
    private static final long LISTENING_TIMEOUT_MS = 8000L;

    private final UniversalControlAccessibilityService service;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private final NodeMetadataStore nodeMetadataStore;
    private final ArrayList<String> chatHistory = new ArrayList<>();
    private final Runnable listeningTimeout = this::stopListeningToIdle;

    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView statusView;
    private TextView chatView;
    private ScrollView chatScroll;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean listening;
    private boolean processing;
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
    }

    boolean isVisible() { return bubble != null; }

    void hide() {
        stopRecognizer(true);
        removeView(panel);
        removeView(bubble);
        panel = null;
        panelParams = null;
        statusView = null;
        chatView = null;
        chatScroll = null;
        bubble = null;
        bubbleParams = null;
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
        bubble.setContentDescription("Amin Neural Flow 語音按鈕，點一下開始或停止，拖曳可移動");
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
                dp(210),
                false,
                "Amin Neural Flow Voice Chat"
        );
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
        if (processing) { status("上一個訊號仍在處理中"); return; }
        if (listening) stopAndProcess(); else startListening();
    }

    private void startListening() {
        if (service.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status("需要麥克風權限");
            Toast.makeText(service, "請先開啟 Amin 麥克風權限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!prepareRecognizer()) return;
        listening = true;
        processing = false;
        bubble.setText("■");
        status("正在聆聽… 再點一次可送出");
        handler.removeCallbacks(listeningTimeout);
        handler.postDelayed(listeningTimeout, LISTENING_TIMEOUT_MS);
        try { recognizer.startListening(recognizerIntent); }
        catch (RuntimeException error) {
            listening = false; bubble.setText("🎙"); status("語音辨識啟動失敗");
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
        if (bubble != null) bubble.setText("🎙");
        status("待命 · 點 🎙 開始說話");
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

        String turnId = NeuralFlowTrace.beginTurn(shorten(spoken, 56));
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "enter", "floating voice priority routing");

        NodeRegistry.Match nodeMatch = NodeRegistry.matchVoice(service, nodeMetadataStore, spoken);
        if (nodeMatch != null && !createRequested) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "matched", nodeMatch.alias);
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "complete", "node_registry");
            appendChat("系統：Node Registry 命中「" + nodeMatch.alias + "」。");
            finishTurn("Node Registry 命中"); return;
        }
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "no_match", createRequested ? "create intent continues to LLM" : "continue");

        VoiceCommandParser.Result parsed = parser.parse(spoken, confidence);
        if (parsed.getStatus() == VoiceCommandParser.Result.Status.MATCHED && !createRequested) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.COMMAND, "matched", "legacy command");
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "complete", "command");
            appendChat("系統：COMMAND 命中；Neural Flow POC 不控制手機。");
            finishTurn("COMMAND 命中"); return;
        }
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.COMMAND, "no_match", "fallback to LLM");
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "complete", "llm");

        if (!LlmConfigStore.hasApiKey(service)) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_ERROR, "blocked", "API Key not configured");
            appendChat("系統：尚未設定 API Key。"); finishTurn("LLM 未設定"); return;
        }

        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_REQUEST, "sending", LlmConfigStore.label(service));
        status("LLM 思考中 · " + LlmConfigStore.label(service));
        ArrayList<LlmClient.Message> messages = new ArrayList<>();
        messages.add(new LlmClient.Message("user", spoken));
        final String callbackTurnId = turnId;
        LlmClient.send(service, messages, new LlmClient.Callback() {
            @Override public void onSuccess(String text) {
                handler.post(() -> {
                    String reply = text == null || text.trim().isEmpty() ? "我沒有取得有效回覆。" : text.trim();
                    NeuralFlowTrace.emit(callbackTurnId, NeuralFlowTrace.Stage.LLM_RESPONSE, "received", shorten(reply, 72));
                    appendChat("AI：" + reply);
                    if (createRequested) createNodeAfterReply(callbackTurnId, requestedNodeName, spoken);
                    NeuralFlowTrace.emit(callbackTurnId, NeuralFlowTrace.Stage.MEMORY_JUDGE, "skip", "floating live-link test: memory write disabled");
                    finishTurn(createRequested ? "完成 · 已執行節點建立分支" : "完成 · Memory 測試暫不寫入");
                });
            }
            @Override public void onError(String message) {
                handler.post(() -> {
                    NeuralFlowTrace.emit(callbackTurnId, NeuralFlowTrace.Stage.LLM_ERROR, "error", shorten(message, 72));
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
        processing = false; listening = false;
        if (bubble != null) bubble.setText("🎙");
        status(value + " · 可繼續說下一句");
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

    @Override public void onReadyForSpeech(Bundle params) { status("請開始說話"); }
    @Override public void onBeginningOfSpeech() { handler.removeCallbacks(listeningTimeout); status("已聽到聲音…"); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { handler.removeCallbacks(listeningTimeout); listening = false; processing = true; if (bubble != null) bubble.setText("…"); status("正在理解…"); }
    @Override public void onError(int error) { handler.removeCallbacks(listeningTimeout); processing = false; listening = false; if (bubble != null) bubble.setText("🎙"); status("語音辨識失敗 · 再試一次"); }
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
        if (matches == null || matches.isEmpty()) { finishTurn("沒有辨識到文字"); return; }
        String spoken = matches.get(0) == null ? "" : matches.get(0).trim();
        double confidence = confidences != null && confidences.length > 0 ? confidences[0] : 1.0d;
        if (spoken.isEmpty()) { finishTurn("沒有辨識到文字"); return; }
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
