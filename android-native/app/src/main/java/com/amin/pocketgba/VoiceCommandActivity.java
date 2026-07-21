package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public final class VoiceCommandActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_RECORD_AUDIO = 6401;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private Button listenButton;
    private TextView statusView;
    private TextView transcriptView;
    private boolean listening;
    private final VoiceCommandParser parser = new VoiceCommandParser();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VoiceCommandActivityLauncher.acknowledgeLaunch(getIntent());
        buildUi();
        prepareRecognizer();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        VoiceCommandActivityLauncher.acknowledgeLaunch(intent);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xfff4f7f5);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(40));
        root.setBackgroundColor(0xfff4f7f5);
        scroll.addView(root, matchWrap());

        TextView title = new TextView(this);
        title.setText("Amin 語音指令");
        title.setTextSize(28f);
        title.setTextColor(0xff16231b);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("按住麥克風說話，放開後辨識並執行。第一版不會在背景持續監聽。");
        description.setTextSize(15f);
        description.setTextColor(0xff68766e);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = dp(12);
        root.addView(description, descriptionParams);

        TextView commandSummary = new TextView(this);
        commandSummary.setText(
                "目前支援 " + VoiceCommandCatalog.getCommandCount() + " 個動作 · "
                        + VoiceCommandCatalog.getPhraseCount() + " 種可說法"
        );
        commandSummary.setTextSize(16f);
        commandSummary.setTextColor(0xff19794b);
        commandSummary.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams commandSummaryParams = matchWrap();
        commandSummaryParams.topMargin = dp(16);
        root.addView(commandSummary, commandSummaryParams);

        Button catalogButton = new Button(this);
        catalogButton.setText("📖 查看全部 " + VoiceCommandCatalog.getCommandCount() + " 個指令");
        catalogButton.setTextSize(16f);
        catalogButton.setAllCaps(false);
        catalogButton.setContentDescription("查看全部語音指令");
        catalogButton.setOnClickListener(
                view -> startActivity(new Intent(this, VoiceCommandCatalogActivity.class))
        );
        LinearLayout.LayoutParams catalogParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        catalogParams.topMargin = dp(14);
        root.addView(catalogButton, catalogParams);

        listenButton = new Button(this);
        listenButton.setText("🎤 按住說話");
        listenButton.setTextSize(18f);
        listenButton.setAllCaps(false);
        listenButton.setContentDescription("按住開始語音辨識，放開後執行指令");
        listenButton.setOnClickListener(view -> {
            // Touch handles push-to-talk. Click remains for accessibility semantics.
        });
        listenButton.setOnTouchListener((view, event) -> handleTalkTouch(event));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        buttonParams.topMargin = dp(18);
        root.addView(listenButton, buttonParams);

        statusView = new TextView(this);
        statusView.setText("尚未啟動");
        statusView.setTextSize(15f);
        statusView.setTextColor(0xff19794b);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(20);
        root.addView(statusView, statusParams);

        transcriptView = new TextView(this);
        transcriptView.setText(
                "可以先試：" + VoiceCommandCatalog.getQuickExamples(9)
                        + "\n完整清單與其他說法請點上方「查看全部指令」。"
        );
        transcriptView.setTextSize(16f);
        transcriptView.setTextColor(Color.DKGRAY);
        transcriptView.setGravity(Gravity.CENTER);
        transcriptView.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams transcriptParams = matchWrap();
        transcriptParams.topMargin = dp(16);
        root.addView(transcriptView, transcriptParams);

        setContentView(scroll);
    }

    private boolean handleTalkTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                listenButton.setPressed(true);
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    setStatus("請先允許麥克風權限", false);
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                    return true;
                }
                startListening();
                return true;
            case MotionEvent.ACTION_UP:
                listenButton.setPressed(false);
                finishSpeechInput();
                listenButton.performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                listenButton.setPressed(false);
                cancelListening("已取消聆聽");
                return true;
            default:
                return true;
        }
    }

    private void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("此裝置沒有可用的語音辨識服務", false);
            listenButton.setEnabled(false);
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TAIWAN.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.TAIWAN.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        setStatus("準備完成，請按住麥克風", true);
    }

    private void startListening() {
        if (speechRecognizer == null || listening) return;
        listening = true;
        listenButton.setText("放開後辨識");
        setStatus("正在聆聽…", true);
        transcriptView.setText("請說出指令");
        speechRecognizer.startListening(recognizerIntent);
    }

    private void finishSpeechInput() {
        if (speechRecognizer == null || !listening) {
            resetTalkButton();
            return;
        }
        speechRecognizer.stopListening();
        resetTalkButton();
        setStatus("正在辨識…", true);
    }

    private void cancelListening(String message) {
        if (speechRecognizer != null && listening) speechRecognizer.cancel();
        listening = false;
        resetTalkButton();
        setStatus(message, true);
    }

    private void resetTalkButton() {
        if (listenButton != null) listenButton.setText("🎤 按住說話");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setStatus("麥克風權限已開啟，請再次按住說話", true);
        } else {
            setStatus("未取得麥克風權限，語音功能不會啟動", false);
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { setStatus("請說出指令", true); }
    @Override public void onBeginningOfSpeech() { setStatus("已聽到聲音", true); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { setStatus("正在辨識…", true); }

    @Override
    public void onError(int error) {
        listening = false;
        resetTalkButton();
        setStatus(errorMessage(error), false);
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        resetTalkButton();
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (matches == null || matches.isEmpty()) {
            setStatus("沒有辨識到指令", false);
            return;
        }
        double confidence = confidences != null && confidences.length > 0 ? confidences[0] : -1d;
        handleTranscript(matches.get(0), confidence);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) transcriptView.setText(matches.get(0));
    }

    @Override public void onEvent(int eventType, Bundle params) { }

    private void handleTranscript(String transcript, double confidence) {
        transcriptView.setText("你說：" + transcript);
        VoiceCommandParser.Result parsed = parser.parse(transcript, confidence);
        if (parsed.getStatus() != VoiceCommandParser.Result.Status.MATCHED) {
            setStatus(parsed.getMessage(), false);
            return;
        }
        AminActionDispatcher.DispatchResult result = AminActionDispatcher.dispatch(this, parsed.getAction());
        setStatus(result.getMessage(), result.isSuccess());
    }

    private String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "麥克風音訊錯誤";
            case SpeechRecognizer.ERROR_CLIENT: return "語音服務已取消";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "沒有麥克風權限";
            case SpeechRecognizer.ERROR_NETWORK: return "語音服務網路錯誤";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "語音服務逾時";
            case SpeechRecognizer.ERROR_NO_MATCH: return "沒有辨識到符合的語句";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "語音服務忙碌，請稍後再試";
            case SpeechRecognizer.ERROR_SERVER: return "語音服務暫時無法使用";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "沒有聽到語音";
            default: return "語音辨識失敗（" + error + "）";
        }
    }

    private void setStatus(String message, boolean success) {
        statusView.setText(message);
        statusView.setTextColor(success ? 0xff19794b : 0xff9a3d25);
    }

    @Override
    protected void onPause() {
        if (speechRecognizer != null && listening) speechRecognizer.cancel();
        listening = false;
        resetTalkButton();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
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
