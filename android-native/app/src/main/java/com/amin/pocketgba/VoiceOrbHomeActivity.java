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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public final class VoiceOrbHomeActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_RECORD_AUDIO = 6501;
    private static final long SILENCE_TIMEOUT_MS = 8000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private final Runnable silenceTimeout = this::collapseToFloatingButton;

    private VoiceOrbView orbView;
    private TextView statusView;
    private TextView transcriptView;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean listening;
    private boolean launchedFeature;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff08130e);
        getWindow().setNavigationBarColor(0xff08130e);
        buildUi();
        prepareRecognizer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            handler.postDelayed(this::startListeningWithPermission, 280L);
        } else if (launchedFeature) {
            launchedFeature = false;
            handler.postDelayed(this::startListeningWithPermission, 320L);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(silenceTimeout);
        if (listening && recognizer != null) recognizer.cancel();
        listening = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff08130e);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(30), dp(24), dp(26));
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        TextView brand = text("AMIN", 13f, true, 0xff59e39b);
        brand.setGravity(Gravity.CENTER);
        content.addView(brand, matchWrap());

        TextView title = text("語音核心", 28f, true, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        content.addView(title, titleParams);

        orbView = new VoiceOrbView(this);
        orbView.setOnClickListener(view -> {
            if (listening) stopAndProcess();
            else startListeningWithPermission();
        });
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        orbParams.topMargin = dp(8);
        content.addView(orbView, orbParams);

        statusView = text("準備中", 18f, true, Color.WHITE);
        statusView.setGravity(Gravity.CENTER);
        content.addView(statusView, matchWrap());

        transcriptView = text("請說出要開啟的功能", 15f, false, 0xffb9c8c0);
        transcriptView.setGravity(Gravity.CENTER);
        transcriptView.setMaxLines(3);
        LinearLayout.LayoutParams transcriptParams = matchWrap();
        transcriptParams.topMargin = dp(8);
        content.addView(transcriptView, transcriptParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(20);
        content.addView(actions, actionsParams);

        Button graph = actionButton("功能地圖");
        graph.setOnClickListener(view -> {
            stopListeningQuietly();
            startActivity(new Intent(this, SystemGraphActivity.class));
        });
        actions.addView(graph, weightedButton());

        Button collapse = actionButton("收合");
        collapse.setOnClickListener(view -> collapseToFloatingButton());
        LinearLayout.LayoutParams collapseParams = weightedButton();
        collapseParams.leftMargin = dp(10);
        actions.addView(collapse, collapseParams);

        setContentView(root);
    }

    private void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status("沒有可用的語音服務", VoiceOrbView.Phase.ERROR);
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TAIWAN.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.TAIWAN.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    private void startListeningWithPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        startListening();
    }

    private void startListening() {
        if (recognizer == null || listening) return;
        listening = true;
        status("正在聆聽", VoiceOrbView.Phase.LISTENING);
        transcriptView.setText("請說出功能名稱或指令");
        handler.removeCallbacks(silenceTimeout);
        handler.postDelayed(silenceTimeout, SILENCE_TIMEOUT_MS);
        try {
            recognizer.startListening(recognizerIntent);
        } catch (RuntimeException error) {
            listening = false;
            status("語音啟動失敗", VoiceOrbView.Phase.ERROR);
        }
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

    private void collapseToFloatingButton() {
        stopListeningQuietly();
        UniversalControlAccessibilityService.setVoiceBubbleEnabled(this, true);
        finishAndRemoveTask();
    }

    private void handleTranscript(String transcript, double confidence) {
        String normalized = transcript == null ? "" : transcript.trim();
        transcriptView.setText("你說：「" + normalized + "」");
        if (normalized.contains("功能地圖") || normalized.contains("節點地圖")) {
            status("開啟功能地圖", VoiceOrbView.Phase.SUCCESS);
            launchedFeature = true;
            startActivity(new Intent(this, SystemGraphActivity.class));
            return;
        }
        if (normalized.equals("關閉") || normalized.equals("收合") || normalized.contains("關閉語音球")) {
            collapseToFloatingButton();
            return;
        }
        VoiceCommandParser.Result parsed = parser.parse(normalized, confidence);
        if (parsed.getStatus() != VoiceCommandParser.Result.Status.MATCHED) {
            status(parsed.getMessage(), VoiceOrbView.Phase.ERROR);
            handler.postDelayed(this::startListeningWithPermission, 1300L);
            return;
        }
        status("正在開啟功能", VoiceOrbView.Phase.PROCESSING);
        AminActionDispatcher.DispatchResult result = AminActionDispatcher.dispatch(this, parsed.getAction());
        if (result.isSuccess()) {
            launchedFeature = true;
            status(result.getMessage(), VoiceOrbView.Phase.SUCCESS);
        } else {
            status(result.getMessage(), VoiceOrbView.Phase.ERROR);
            handler.postDelayed(this::startListeningWithPermission, 1300L);
        }
    }

    private void status(String value, VoiceOrbView.Phase phase) {
        if (statusView != null) statusView.setText(value);
        if (orbView != null) orbView.setPhase(phase);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            status("未取得麥克風權限", VoiceOrbView.Phase.ERROR);
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { status("請開始說話", VoiceOrbView.Phase.LISTENING); }
    @Override public void onBeginningOfSpeech() { handler.removeCallbacks(silenceTimeout); status("已聽到聲音", VoiceOrbView.Phase.LISTENING); }
    @Override public void onRmsChanged(float rmsdB) { if (orbView != null) orbView.setAmplitude(rmsdB); }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { handler.removeCallbacks(silenceTimeout); listening = false; status("正在理解", VoiceOrbView.Phase.PROCESSING); }

    @Override
    public void onError(int error) {
        handler.removeCallbacks(silenceTimeout);
        listening = false;
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
            collapseToFloatingButton();
            return;
        }
        status("語音辨識失敗", VoiceOrbView.Phase.ERROR);
        handler.postDelayed(this::startListeningWithPermission, 1300L);
    }

    @Override
    public void onResults(Bundle results) {
        handler.removeCallbacks(silenceTimeout);
        listening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (matches == null || matches.isEmpty()) {
            collapseToFloatingButton();
            return;
        }
        double confidence = confidences != null && confidences.length > 0 ? confidences[0] : -1d;
        handleTranscript(matches.get(0), confidence);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        handler.removeCallbacks(silenceTimeout);
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) transcriptView.setText(matches.get(0));
    }

    @Override public void onEvent(int eventType, Bundle params) { }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.2f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff173c29));
        return button;
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, dp(52), 1f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
