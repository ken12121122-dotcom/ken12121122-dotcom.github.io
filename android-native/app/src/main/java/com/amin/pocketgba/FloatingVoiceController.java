package com.amin.pocketgba;

import android.Manifest;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Floating voice + chat overlay used to observe Neural Flow while speaking.
 *
 * This is intentionally a POC observer/producer: it follows the same visible
 * routing priority as NeuralFlowActivity (Node Registry -> legacy command -> LLM),
 * emits NeuralFlowTrace events, and never executes matched phone-control actions.
 */
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
    private final ArrayList<LlmClient.Message> chatHistory = new ArrayList<>();
    private final Runnable listeningTimeout = this::stopListeningToIdle;

    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView statusView;
    private TextView chatView;
    private ScrollView chatScroll;
    private SpeechRecognizer recognizer;
    private android.content.Intent recognizerIntent;
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

    boolean isVisible() {
        return bubble != null;
    }

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

    void destroy() {
        hide();
    }

    void onConfigurationChanged() {
        refreshScreenBounds();
        if (bubbleParams != null) {
            clampBubble();
            updateView(bubble, bubbleParams);
        }
        if (panelParams != null) {
            panelParams.width = Math.min(Math.max(dp(220), screenWidth - dp(32)), dp(390));
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
        bubbleParams.y = prefs.getInt(KEY_Y, Math.max(dp(170), screenHeight / 2));
        clampBubble();

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            int startX;
            int startY;
            boolean dragging;

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = bubbleParams.x;
                        startY = bubbleParams.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(7) || Math.abs(dy) > dp(7)) dragging = true;
                        if (dragging) {
                            bubbleParams.x = startX + Math.round(dx);
                            bubbleParams.y = startY + Math.round(dy);
                            clampBubble();
                            updateView(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) saveBubblePosition(); else view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return true;
                }
            }
        });
        windowManager.addView(bubble, bubbleParams);
    }

    private void createPanel() {
        panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(roundRect(0xd914201a, 16f, 0x44ffffff));

        TextView title = text("LIVE VOICE · Neural Flow", 12f, true, 0xff83e9b1);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        statusView = text("待命 · 點右側 🎙 開始說話", 13f, true, Color.WHITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(3);
        panel.addView(statusView, statusParams);

        chatScroll = new ScrollView(service);
        chatScroll.setVerticalScrollBarEnabled(true);
        chatView = text("這裡會顯示你的語音與 LLM 回覆。", 13f, false, 0xffd7e4dc);
        chatView.setLineSpacing(0f, 1.18f);
        chatScroll.addView(chatView, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(116));
        scrollParams.topMargin = dp(5);
        panel.addView(chatScroll, scrollParams);

        panelParams = overlayParams(
                Math.min(Math.max(dp(220), screenWidth - dp(32)), dp(390)),
                dp(168),
                true,
                "Amin Neural Flow Voice Chat"
        );
        panelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelParams.y = dp(58);
        windowManager.addView(panel, panelParams);
    }

    private void toggleListening() {
        if (processing) {
            status("上一個訊號仍在處理中");
            return;
        }
        if (listening) {
            stopAndProcess();
        } else {
            startListening();
        }
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
        try {
            recognizer.startListening(recognizerIntent);
        } catch (RuntimeException error) {
            listening = false;
            bubble.setText("🎙");
            status("語音辨識啟動失敗");
        }
    }

    private boolean prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(service)) {
            status("此裝置沒有可用的語音辨識服務");
            return false;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(service);
            recognizer.setRecognitionListener(this);
            recognizerIntent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
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
        listening = false;
        processing = true;
        bubble.setText("…");
        status("正在取得完整語音…");
        recognizer.stopListening();
    }

    private void stopListeningToIdle() {
        if (recognizer != null && listening) recognizer.cancel();
        listening = false;
        processing = false;
        if (bubble != null) bubble.setText("🎙");
        status("待命 · 點 🎙 開始說話");
    }

    private void stopRecognizer(boolean destroy) {
        handler.removeCallbacks(listeningTimeout);
        if (recognizer != null) {
            recognizer.cancel();
            if (destroy) {
                recognizer.destroy();
                recognizer = null;
                recognizerIntent = null;
            }
        }
        listening = false;
        processing = false;
    }

    private void routeTranscript(String spoken, double confidence) {
        appendChat("你：" + spoken);
        status("Router 分流中…");

        String turnId = NeuralFlowTrace.latestTurnId();
        if (turnId == null || turnId.isEmpty()) {
            turnId = NeuralFlowTrace.beginTurn(shorten(spoken, 56));
        } else {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.INPUT, "received", shorten(spoken, 56));
        }
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "enter", "floating voice priority routing");

        NodeRegistry.Match nodeMatch = NodeRegistry.matchVoice(service, nodeMetadataStore, spoken);
        if (nodeMatch != null) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "matched", nodeMatch.alias);
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "complete", "node_registry");
            appendChat("系統：Node Registry 命中「" + nodeMatch.alias + "」；POC 不執行跳轉。");
            finishTurn("Node Registry 命中");
            return;
        }
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.NODE_REGISTRY, "no_match", "continue");

        VoiceCommandParser.Result parsed = parser.parse(spoken, confidence);
        if (parsed.getStatus() == VoiceCommandParser.Result.Status.MATCHED) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.COMMAND, "matched", "legacy command");
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "complete", "command");
            appendChat("系統：COMMAND 命中；Neural Flow POC 不控制手機。");
            finishTurn("COMMAND 命中");
            return;
        }
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.COMMAND, "no_match", "fallback to LLM");
        NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.ROUTER, "complete", "llm");

        if (!LlmConfigStore.hasApiKey(service)) {
            NeuralFlowTrace.emit(turnId, NeuralFlowTrace.Stage.LLM_ERROR, "blocked", "API Key not configured");
            appendChat("系統：尚未設定 API Key。");
            finishTurn("LLM 未設定");
            return;
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
                    NeuralFlowTrace.emit(callbackTurnId, NeuralFlowTrace.Stage.MEMORY_JUDGE, "skip", "floating live-link test: memory write disabled");
                    finishTurn("完成 · Memory 測試暫不寫入");
                });
            }

            @Override public void onError(String message) {
                handler.post(() -> {
                    NeuralFlowTrace.emit(callbackTurnId, NeuralFlowTrace.Stage.LLM_ERROR, "error", shorten(message, 72));
                    appendChat("系統：LLM 錯誤 · " + message);
                    finishTurn("LLM 錯誤");
                });
            }
        });
    }

    private void finishTurn(String value) {
        processing = false;
        listening = false;
        if (bubble != null) bubble.setText("🎙");
        status(value + " · 可繼續說下一句");
    }

    private void appendChat(String line) {
        chatHistory.add(new LlmClient.Message("log", line));
        while (chatHistory.size() > 12) chatHistory.remove(0);
        StringBuilder builder = new StringBuilder();
        for (LlmClient.Message message : chatHistory) {
            if (builder.length() > 0) builder.append("\n\n");
            builder.append(message.text);
        }
        if (chatView != null) chatView.setText(builder.toString());
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void status(String value) {
        if (statusView != null) statusView.setText(value);
    }

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
        listening = false;
        processing = true;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (matches == null || matches.isEmpty()) {
            finishTurn("沒有辨識到文字");
            return;
        }
        String spoken = matches.get(0) == null ? "" : matches.get(0).trim();
        double confidence = confidences != null && confidences.length > 0 ? confidences[0] : 1.0d;
        if (spoken.isEmpty()) {
            finishTurn("沒有辨識到文字");
            return;
        }
        routeTranscript(spoken, confidence);
    }

    private WindowManager.LayoutParams overlayParams(int width, int height, boolean clickThrough, String title) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (clickThrough) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.setTitle(title);
        return params;
    }

    private GradientDrawable circle(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable roundRect(int fill, float radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView text(String value, float sizeSp, boolean bold, int color) {
        TextView view = new TextView(service);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private void refreshScreenBounds() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
    }

    private void clampBubble() {
        if (bubbleParams == null) return;
        int size = dp(58);
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

    private int dp(float value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, Math.max(1, max - 1)) + "…";
    }
}
