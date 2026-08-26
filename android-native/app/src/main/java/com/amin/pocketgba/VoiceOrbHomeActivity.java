package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/** Full-screen green voice orb + continuous LLM chat. */
public final class VoiceOrbHomeActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_RECORD_AUDIO = 6501;
    private static final long SILENCE_TIMEOUT_MS = 8000L;
    private static final int MAX_ACTIVE_MESSAGES = 20;
    private static final String SPEAK_ID = "amin_llm_reply";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable silenceTimeout = this::enterIdleState;
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private final ArrayList<LlmClient.Message> chatHistory = new ArrayList<>();

    private VoiceOrbView orbView;
    private TextView statusView;
    private TextView modelView;
    private TextView chatView;
    private ScrollView chatScroll;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextToSpeech textToSpeech;
    private boolean listening;
    private boolean firstResume = true;
    private boolean launchedFeature;
    private boolean speaking;
    private NodeMetadataStore nodeMetadataStore;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        nodeMetadataStore = new NodeMetadataStore(this);
        getWindow().setStatusBarColor(0xff08130e);
        getWindow().setNavigationBarColor(0xff08130e);
        buildUi();
        prepareRecognizer();
        ensureTts();
    }

    @Override protected void onResume() {
        super.onResume();
        UniversalControlAccessibilityService.setVoiceBubbleEnabled(this, false);
        refreshModelLabel();
        if (firstResume) {
            firstResume = false;
            handler.postDelayed(() -> speakThenListen("我在。", false), 180L);
        } else if (launchedFeature) {
            launchedFeature = false;
            handler.postDelayed(this::startListeningWithPermission, 300L);
        } else if (!speaking) {
            handler.postDelayed(this::startListeningWithPermission, 250L);
        }
    }

    @Override protected void onPause() {
        stopListeningQuietly();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); textToSpeech = null; }
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff08130e);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(18));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(top, wrap());

        TextView brand = text("AMIN AI VOICE", 13, true, 0xff59e39b);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        Button settings = button("LLM 設定");
        settings.setOnClickListener(v -> { launchedFeature = true; startActivity(new Intent(this, LlmSettingsActivity.class)); });
        top.addView(settings, new LinearLayout.LayoutParams(dp(108), dp(44)));

        modelView = text("", 12, false, 0xff8eaaa0);
        modelView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams modelParams = wrap(); modelParams.topMargin = dp(4); content.addView(modelView, modelParams);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatView = text("聊天紀錄會顯示在這裡。", 14, false, 0xffb9c8c0);
        chatView.setLineSpacing(0f, 1.25f);
        chatScroll.addView(chatView, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(-1, dp(170));
        chatParams.topMargin = dp(8);
        content.addView(chatScroll, chatParams);

        orbView = new VoiceOrbView(this);
        orbView.setOnClickListener(v -> { if (listening) stopAndProcess(); else if (!speaking) startListeningWithPermission(); });
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        orbParams.topMargin = dp(4);
        content.addView(orbView, orbParams);

        statusView = text("準備中", 18, true, Color.WHITE);
        statusView.setGravity(Gravity.CENTER);
        content.addView(statusView, wrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = wrap(); actionParams.topMargin = dp(8); content.addView(actions, actionParams);

        Button graph = button("功能地圖");
        graph.setOnClickListener(v -> openSystemFeatureMap());
        actions.addView(graph, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button back = button("回控制台");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(0, dp(48), 1f); backParams.leftMargin = dp(8); actions.addView(back, backParams);

        setContentView(root);
    }

    private void refreshModelLabel() {
        if (modelView == null) return;
        modelView.setText((LlmConfigStore.hasApiKey(this) ? "🟢 " : "⚪ ") + LlmConfigStore.label(this));
    }

    private void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status("沒有可用的語音服務", VoiceOrbView.Phase.ERROR); return; }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TAIWAN.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.TAIWAN.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    private void ensureTts() {
        if (textToSpeech != null) return;
        textToSpeech = new TextToSpeech(this, result -> {
            if (result != TextToSpeech.SUCCESS) return;
            textToSpeech.setLanguage(Locale.TAIWAN);
            textToSpeech.setSpeechRate(0.98f);
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) {
                    if (!SPEAK_ID.equals(utteranceId)) return;
                    handler.post(() -> {
                        speaking = false;
                        if (!isFinishing() && !isDestroyed()) handler.postDelayed(VoiceOrbHomeActivity.this::startListeningWithPermission, 180L);
                    });
                }
                @Override public void onError(String utteranceId) {
                    handler.post(() -> { speaking = false; if (!isFinishing() && !isDestroyed()) startListeningWithPermission(); });
                }
            });
        });
    }

    private void speakThenListen(String text, boolean addAssistant) {
        if (addAssistant) appendMessage("assistant", text);
        ensureTts();
        speaking = true;
        status("正在回覆", VoiceOrbView.Phase.SUCCESS);
        if (textToSpeech == null) { speaking = false; startListeningWithPermission(); return; }
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, SPEAK_ID);
        if (result == TextToSpeech.ERROR) { speaking = false; handler.postDelayed(this::startListeningWithPermission, 160L); }
    }

    private void startListeningWithPermission() {
        if (speaking || isFinishing() || isDestroyed()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        startListening();
    }

    private void startListening() {
        if (recognizer == null || listening || speaking) return;
        listening = true;
        status("正在聆聽", VoiceOrbView.Phase.LISTENING);
        handler.removeCallbacks(silenceTimeout);
        handler.postDelayed(silenceTimeout, SILENCE_TIMEOUT_MS);
        try { recognizer.startListening(recognizerIntent); }
        catch (RuntimeException error) { enterIdleState(); }
    }

    private void stopAndProcess() {
        if (!listening || recognizer == null) return;
        handler.removeCallbacks(silenceTimeout);
        listening = false;
        status("正在理解", VoiceOrbView.Phase.PROCESSING);
        recognizer.stopListening();
    }

    private void stopListeningQuietly() {
        handler.removeCallbacks(silenceTimeout);
        if (recognizer != null && listening) recognizer.cancel();
        listening = false;
    }

    private void enterIdleState() {
        stopListeningQuietly();
        status("待命中 · 點綠色語音球可重新開始", VoiceOrbView.Phase.IDLE);
    }

    private void handleTranscript(String transcript, double confidence) {
        String spoken = transcript == null ? "" : transcript.trim();
        if (spoken.isEmpty()) { enterIdleState(); return; }
        appendMessage("user", spoken);

        if (spoken.contains("LLM設定") || spoken.contains("模型設定") || spoken.contains("API設定")) {
            launchedFeature = true;
            startActivity(new Intent(this, LlmSettingsActivity.class));
            return;
        }
        if (spoken.contains("功能地圖") || spoken.contains("節點地圖")) { openSystemFeatureMap(); return; }
        if (spoken.equals("關閉") || spoken.equals("回控制台") || spoken.contains("關閉語音球")) { finish(); return; }

        NodeRegistry.Match nodeMatch = NodeRegistry.matchVoice(this, nodeMetadataStore, spoken);
        if (nodeMatch != null) {
            JSONObject node = nodeMatch.node;
            status("已匹配「" + nodeMatch.alias + "」", VoiceOrbView.Phase.SUCCESS);
            openGraph(node.optString("rawId", ""), node.optString("route", ""));
            return;
        }

        VoiceCommandParser.Result parsed = parser.parse(spoken, confidence);
        if (parsed.getStatus() == VoiceCommandParser.Result.Status.MATCHED) {
            status("正在執行既有指令", VoiceOrbView.Phase.PROCESSING);
            AminActionDispatcher.DispatchResult result = AminActionDispatcher.dispatch(this, parsed.getAction());
            if (result.isSuccess()) {
                launchedFeature = true;
                status(result.getMessage(), VoiceOrbView.Phase.SUCCESS);
                return;
            }
        }

        sendToLlm();
    }

    private void sendToLlm() {
        if (!LlmConfigStore.hasApiKey(this)) {
            status("尚未設定 API Key", VoiceOrbView.Phase.ERROR);
            appendSystemLine("請先按右上角「LLM 設定」貼上 API Key。", 0xfff0c36a);
            handler.postDelayed(this::enterIdleState, 1500L);
            return;
        }
        status("LLM 思考中 · " + LlmConfigStore.label(this), VoiceOrbView.Phase.PROCESSING);
        final ArrayList<LlmClient.Message> snapshot = new ArrayList<>(chatHistory);
        LlmClient.send(this, snapshot, new LlmClient.Callback() {
            @Override public void onSuccess(String text) {
                runOnUiThread(() -> {
                    String reply = text == null || text.trim().isEmpty() ? "我沒有取得有效回覆。" : text.trim();
                    appendMessage("assistant", reply);
                    speakThenListen(reply, false);
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    status("LLM 連線失敗", VoiceOrbView.Phase.ERROR);
                    appendSystemLine("錯誤：" + message, 0xffff9a8f);
                    handler.postDelayed(VoiceOrbHomeActivity.this::startListeningWithPermission, 1200L);
                });
            }
        });
    }

    private void appendMessage(String role, String text) {
        chatHistory.add(new LlmClient.Message(role, text));
        while (chatHistory.size() > MAX_ACTIVE_MESSAGES) chatHistory.remove(0);
        renderChat();
    }

    private void appendSystemLine(String text, int color) {
        if (chatView == null) return;
        chatView.append("\n\n⚙ " + text);
        chatView.setTextColor(color);
        scrollChatBottom();
        handler.postDelayed(() -> chatView.setTextColor(0xffb9c8c0), 600L);
    }

    private void renderChat() {
        StringBuilder builder = new StringBuilder();
        for (LlmClient.Message message : chatHistory) {
            if (builder.length() > 0) builder.append("\n\n");
            builder.append("assistant".equals(message.role) ? "AI：" : "你：").append(message.text);
        }
        chatView.setText(builder.length() == 0 ? "聊天紀錄會顯示在這裡。" : builder.toString());
        scrollChatBottom();
    }

    private void scrollChatBottom() {
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void openSystemFeatureMap() {
        stopListeningQuietly();
        launchedFeature = true;
        startActivity(new Intent(this, SystemGraphActivity.class));
    }

    private void openGraph(String focusRawId, String route) {
        stopListeningQuietly();
        Intent intent = new Intent(this, WikiGraphActivity.class);
        if (focusRawId != null && !focusRawId.isEmpty()) intent.putExtra("focus_node", focusRawId);
        if (route != null && !route.isEmpty()) intent.putExtra("voice_open_route", route);
        launchedFeature = true;
        startActivity(intent);
    }

    private void status(String value, VoiceOrbView.Phase phase) {
        if (statusView != null) statusView.setText(value);
        if (orbView != null) orbView.setPhase(phase);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening();
        else status("未取得麥克風權限", VoiceOrbView.Phase.ERROR);
    }

    @Override public void onReadyForSpeech(Bundle params) { status("請開始說話", VoiceOrbView.Phase.LISTENING); }
    @Override public void onBeginningOfSpeech() { handler.removeCallbacks(silenceTimeout); status("已聽到聲音", VoiceOrbView.Phase.LISTENING); }
    @Override public void onRmsChanged(float rmsdB) { if (orbView != null) orbView.setAmplitude(rmsdB); }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { handler.removeCallbacks(silenceTimeout); listening = false; status("正在理解", VoiceOrbView.Phase.PROCESSING); }
    @Override public void onError(int error) {
        handler.removeCallbacks(silenceTimeout);
        listening = false;
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) { enterIdleState(); return; }
        status("語音辨識失敗", VoiceOrbView.Phase.ERROR);
        handler.postDelayed(this::enterIdleState, 900L);
    }
    @Override public void onResults(Bundle results) {
        handler.removeCallbacks(silenceTimeout);
        listening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (matches == null || matches.isEmpty()) { enterIdleState(); return; }
        handleTranscript(matches.get(0), confidence != null && confidence.length > 0 ? confidence[0] : -1d);
    }
    @Override public void onPartialResults(Bundle partialResults) {
        handler.removeCallbacks(silenceTimeout);
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) status("聽到：「" + matches.get(0) + "」", VoiceOrbView.Phase.LISTENING);
        handler.postDelayed(silenceTimeout, SILENCE_TIMEOUT_MS);
    }
    @Override public void onEvent(int eventType, Bundle params) { }

    private TextView text(String value, float size, boolean bold, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view; }
    private Button button(String label) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); return button; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
