package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public final class VoiceOrbHomeActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_RECORD_AUDIO = 6501;
    private static final long SILENCE_TIMEOUT_MS = 8000L;
    private static final String RELEASE_MANIFEST_URL =
            "https://ken12121122-dotcom.github.io/amin-vault/native-release-manifest.json";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private final Runnable silenceTimeout = this::enterIdleState;

    private VoiceOrbView orbView;
    private TextView statusView;
    private TextView transcriptView;
    private TextView updateLink;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean listening;
    private boolean launchedFeature;
    private boolean firstResume = true;
    private volatile boolean destroyed;
    private UpdateInfo availableUpdate;

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
        UniversalControlAccessibilityService.setVoiceBubbleEnabled(this, false);
        checkForUpdate();
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
        destroyed = true;
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

        updateLink = text("下載更新", 14f, true, 0xff59e39b);
        updateLink.setGravity(Gravity.CENTER);
        updateLink.setPadding(dp(10), dp(8), dp(10), dp(8));
        updateLink.setVisibility(View.GONE);
        updateLink.setOnClickListener(view -> showUpdateConfirmation());
        FrameLayout.LayoutParams updateParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        updateParams.gravity = Gravity.TOP | Gravity.END;
        updateParams.topMargin = dp(14);
        updateParams.rightMargin = dp(14);
        root.addView(updateLink, updateParams);

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

    private void checkForUpdate() {
        availableUpdate = null;
        if (updateLink != null) updateLink.setVisibility(View.GONE);

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(RELEASE_MANIFEST_URL + "?t=" + System.currentTimeMillis());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");

                StringBuilder json = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                }

                JSONObject manifest = new JSONObject(json.toString());
                boolean enabled = manifest.optBoolean("enabled", false);
                String packageId = manifest.optString("packageId", "");
                int latestCode = manifest.optInt("latestVersionCode", 0);
                String latestName = manifest.optString("latestVersionName", "");
                String apkUrl = manifest.optString("apkUrl", "");
                JSONArray notes = manifest.optJSONArray("releaseNotes");

                if (!enabled
                        || !getPackageName().equals(packageId)
                        || latestCode <= BuildConfig.VERSION_CODE
                        || latestName.isBlank()
                        || !apkUrl.startsWith("https://")) {
                    return;
                }

                StringBuilder noteText = new StringBuilder();
                if (notes != null) {
                    for (int index = 0; index < notes.length(); index++) {
                        String note = notes.optString(index, "").trim();
                        if (!note.isEmpty()) {
                            if (noteText.length() > 0) noteText.append('\n');
                            noteText.append("• ").append(note);
                        }
                    }
                }

                UpdateInfo update = new UpdateInfo(latestCode, latestName, apkUrl, noteText.toString());
                handler.post(() -> {
                    if (destroyed || isFinishing()) return;
                    availableUpdate = update;
                    updateLink.setText("下載更新");
                    updateLink.setVisibility(View.VISIBLE);
                });
            } catch (Exception ignored) {
                // Update discovery is optional. Network or malformed-manifest failures stay silent.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "amin-update-check").start();
    }

    private void showUpdateConfirmation() {
        UpdateInfo update = availableUpdate;
        if (update == null) {
            if (updateLink != null) updateLink.setVisibility(View.GONE);
            return;
        }

        stopListeningQuietly();
        StringBuilder message = new StringBuilder();
        message.append("目前版本：").append(BuildConfig.VERSION_NAME)
                .append("\n最新版本：").append(update.versionName);
        if (!update.releaseNotes.isBlank()) {
            message.append("\n\n更新內容\n").append(update.releaseNotes);
        }

        new AlertDialog.Builder(this)
                .setTitle("確認更新")
                .setMessage(message.toString())
                .setNegativeButton("取消", (dialog, which) -> enterIdleState())
                .setPositiveButton("下載更新", (dialog, which) -> openUpdateDownload(update))
                .show();
    }

    private void openUpdateDownload(UpdateInfo update) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "無法開啟更新下載頁面", Toast.LENGTH_LONG).show();
            enterIdleState();
        }
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
            enterIdleState();
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

    private void enterIdleState() {
        stopListeningQuietly();
        status("待命中", VoiceOrbView.Phase.IDLE);
        transcriptView.setText("點一下語音球重新開始監聽");
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
            handler.postDelayed(this::enterIdleState, 1300L);
            return;
        }
        status("正在開啟功能", VoiceOrbView.Phase.PROCESSING);
        AminActionDispatcher.DispatchResult result = AminActionDispatcher.dispatch(this, parsed.getAction());
        if (result.isSuccess()) {
            launchedFeature = true;
            status(result.getMessage(), VoiceOrbView.Phase.SUCCESS);
        } else {
            status(result.getMessage(), VoiceOrbView.Phase.ERROR);
            handler.postDelayed(this::enterIdleState, 1300L);
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
            enterIdleState();
            return;
        }
        status("語音辨識失敗", VoiceOrbView.Phase.ERROR);
        handler.postDelayed(this::enterIdleState, 1300L);
    }

    @Override
    public void onResults(Bundle results) {
        handler.removeCallbacks(silenceTimeout);
        listening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (matches == null || matches.isEmpty()) {
            enterIdleState();
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

    private static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String releaseNotes;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String releaseNotes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.releaseNotes = releaseNotes;
        }
    }
}
