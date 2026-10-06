package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
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
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Talk through the GEN2 runs that wait for the OWNER. The fox briefs each run,
 * answers questions with the configured LLM, and when the OWNER says what to
 * do it reads the command back; only an explicit spoken (or tapped) yes posts
 * it. Opened from the GEN2 notification, amin-gen2://run/N, the approval page
 * or by asking the fox for pending work.
 */
public final class Gen2VoiceActivity extends Activity implements RecognitionListener {
    static final String EXTRA_ISSUE = Gen2RunsActivity.EXTRA_ISSUE;
    private static final int REQUEST_RECORD_AUDIO = 6511;
    private static final long SILENCE_TIMEOUT_MS = 9000L;
    private static final int MAX_HISTORY = 16;
    private static final String SPEAK_ID = "amin_gen2_voice";
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|Bearer\\s+[A-Za-z0-9._~-]+|sk-[A-Za-z0-9_-]+)");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable silenceTimeout = this::enterIdle;
    private final ArrayList<LlmClient.Message> history = new ArrayList<>();
    private final List<Gen2RunView> queue = new ArrayList<>();

    private Gen2RunRepository repository;
    private VoiceOrbView orbView;
    private TextView statusView;
    private TextView chatView;
    private ScrollView chatScroll;
    private LinearLayout confirmBar;
    private TextView confirmText;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private boolean listening;
    private boolean speaking;
    private boolean busy;
    private boolean destroyed;
    private boolean endAfterSpeech;
    private int targetIssue;
    private Gen2VoiceBrain.Proposal proposal;
    private final StringBuilder transcript = new StringBuilder();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new Gen2RunRepository(this);
        targetIssue = issueFrom(getIntent());
        getWindow().setStatusBarColor(0xff08130e);
        getWindow().setNavigationBarColor(0xff08130e);
        buildUi();
        prepareRecognizer();
        prepareTts();
        load(true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        targetIssue = issueFrom(intent);
        stopSpeaking();
        stopListening();
        clearProposal();
        load(true);
    }

    @Override
    protected void onPause() {
        stopListening();
        stopSpeaking();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        super.onDestroy();
    }

    static int issueFrom(Intent intent) {
        if (intent == null) return 0;
        int extra = intent.getIntExtra(EXTRA_ISSUE, 0);
        if (extra > 0) return extra;
        Uri data = intent.getData();
        return data == null ? 0 : Gen2VoiceBrain.issueFromUri(data.getScheme(), data.getHost(), data.getPath());
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff08130e);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(18));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView brand = text("狐狸 · GEN2 語音核准", 13, true, 0xff59e39b);
        content.addView(brand, full());
        TextView model = text((LlmConfigStore.hasApiKey(this) ? "🟢 " : "⚪ 未設定 LLM，只聽選項名稱 · ")
                + LlmConfigStore.label(this) + " · 待審內容會送到這個 AI", 11, false, 0xff8eaaa0);
        content.addView(model, top(4));

        chatScroll = new ScrollView(this);
        chatView = text("", 15, false, 0xffd7e4dc);
        chatView.setLineSpacing(0f, 1.3f);
        chatScroll.addView(chatView, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(-1, 0, 1.2f);
        chatParams.topMargin = dp(10);
        content.addView(chatScroll, chatParams);

        confirmBar = new LinearLayout(this);
        confirmBar.setOrientation(LinearLayout.VERTICAL);
        confirmBar.setPadding(dp(14), dp(12), dp(14), dp(12));
        confirmBar.setBackgroundColor(0xff1d3a2a);
        confirmBar.setVisibility(View.GONE);
        confirmText = text("", 14, true, Color.WHITE);
        confirmBar.addView(confirmText, full());
        LinearLayout confirmRow = new LinearLayout(this);
        confirmRow.setOrientation(LinearLayout.HORIZONTAL);
        Button yes = button("確定送出");
        yes.setContentDescription("gen2-voice-confirm");
        yes.setOnClickListener(v -> confirm());
        Button no = button("取消");
        no.setContentDescription("gen2-voice-cancel");
        no.setOnClickListener(v -> cancelProposal(true));
        confirmRow.addView(yes, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams noParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        noParams.leftMargin = dp(8);
        confirmRow.addView(no, noParams);
        confirmBar.addView(confirmRow, top(8));
        content.addView(confirmBar, top(8));

        orbView = new VoiceOrbView(this);
        orbView.setContentDescription("gen2-voice-orb");
        orbView.setOnClickListener(v -> {
            if (speaking) { stopSpeaking(); startListeningWithPermission(); }
            else if (listening) recognizer.stopListening();
            else if (!busy) startListeningWithPermission();
        });
        content.addView(orbView, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusView = text("準備中", 17, true, Color.WHITE);
        statusView.setGravity(Gravity.CENTER);
        content.addView(statusView, full());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button page = button("用按的");
        page.setContentDescription("gen2-voice-open-page");
        page.setOnClickListener(v -> openPage());
        actions.addView(page, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button close = button("結束");
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        closeParams.leftMargin = dp(8);
        actions.addView(close, closeParams);
        content.addView(actions, top(8));
        setContentView(root);
    }

    private void line(String who, String value) {
        if (transcript.length() > 0) transcript.append("\n\n");
        transcript.append(who).append(value);
        chatView.setText(transcript);
        chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void status(String value, VoiceOrbView.Phase phase) {
        statusView.setText(value);
        orbView.setPhase(phase);
    }

    // ---------------------------------------------------------------- runs

    private void load(boolean brief) {
        busy = true;
        status("讀取待辦…", VoiceOrbView.Phase.PROCESSING);
        executor.execute(() -> {
            try {
                List<Gen2RunView> runs = repository.refresh(false);
                handler.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    queue.clear();
                    queue.addAll(Gen2VoiceBrain.queue(runs, targetIssue));
                    history.clear();
                    if (brief) {
                        String text = Gen2VoiceBrain.briefing(queue);
                        if (queue.isEmpty()) endAfterSpeech = true;
                        say(text);
                    }
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    String message = new BrainAuthSession(this).hasSession()
                            ? "讀不到 GEN2 流程：" + safe(error)
                            : "還沒連結 GitHub，請先到 AMIN Brain 連結。";
                    status(message, VoiceOrbView.Phase.ERROR);
                    line("⚙ ", message);
                    speak(message, false);
                });
            }
        });
    }

    private Gen2RunView current() { return queue.isEmpty() ? null : queue.get(0); }

    private void nextRun(String lead) {
        clearProposal();
        if (!queue.isEmpty()) queue.remove(0);
        history.clear();
        if (queue.isEmpty()) {
            endAfterSpeech = true;
            say(lead + "都處理好了，沒有其他等你的流程。");
        } else {
            say(lead + "下一筆，" + Gen2VoiceBrain.describe(queue.get(0)));
        }
    }

    /** Instrumentation hooks: show these runs and treat text as heard speech. */
    void useRuns(List<Gen2RunView> runs) {
        queue.clear();
        queue.addAll(Gen2VoiceBrain.queue(runs, targetIssue));
        history.clear();
        clearProposal();
    }

    void hear(String text) { handle(text); }

    String proposedCommand() { return proposal == null ? null : proposal.command; }

    // ---------------------------------------------------------------- turns

    private void handle(String spoken) {
        String text = spoken == null ? "" : spoken.trim();
        if (text.isEmpty()) { enterIdle(); return; }
        line("你：", text);
        switch (Gen2VoiceBrain.local(text, proposal != null)) {
            case CONFIRM: confirm(); return;
            case CANCEL: cancelProposal(true); return;
            case OPEN_PAGE: openPage(); return;
            case NEXT: nextRun(""); return;
            case END: endAfterSpeech = true; say("好，先這樣。"); return;
            case LIST: listAll(); return;
            default: break;
        }
        clearProposal();
        Gen2RunView run = current();
        if (run == null) { endAfterSpeech = true; say("目前沒有等你處理的流程。"); return; }
        if (!LlmConfigStore.hasApiKey(this)) { fallback(run, text); return; }
        history.add(new LlmClient.Message("user", text));
        while (history.size() > MAX_HISTORY) history.remove(0);
        busy = true;
        status("狐狸思考中…", VoiceOrbView.Phase.PROCESSING);
        List<LlmClient.Message> snapshot = new ArrayList<>(history);
        LlmClient.send(this, Gen2VoiceBrain.systemPrompt(run, queue), snapshot, new LlmClient.Callback() {
            @Override public void onSuccess(String reply) { handler.post(() -> onModelReply(run, reply)); }
            @Override public void onError(String message) {
                handler.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    history.remove(history.size() - 1);
                    say("AI 連線失敗：" + SENSITIVE.matcher(message == null ? "" : message).replaceAll("[已隱藏]")
                            + "。你可以直接說選項名稱。");
                });
            }
        });
    }

    private void onModelReply(Gen2RunView run, String raw) {
        if (destroyed) return;
        busy = false;
        Gen2VoiceBrain.Reply reply = Gen2VoiceBrain.parseReply(raw);
        history.add(new LlmClient.Message("assistant", reply.say));
        if (run != current()) return; // the queue moved on meanwhile
        String type = reply.action == null ? "" : reply.action.optString("type", "");
        if ("next".equals(type)) { nextRun(reply.say.isEmpty() ? "" : reply.say + " "); return; }
        if ("end".equals(type)) { endAfterSpeech = true; say(reply.say.isEmpty() ? "好，先這樣。" : reply.say); return; }
        offer(run, reply.action, reply.say);
    }

    private void fallback(Gen2RunView run, String text) {
        JSONObject action = Gen2VoiceBrain.fallbackAction(run, text);
        if (action == null) {
            say(run.pending != null && run.pending.isGate()
                    ? "沒有設定 AI，我只聽得懂選項名稱。請說：" + options(run) + "。"
                    : "這一筆在等 Agent，沒有要你做的事。你可以說「下一筆」。");
            return;
        }
        offer(run, action, "");
    }

    private void offer(Gen2RunView run, JSONObject action, String lead) {
        try {
            Gen2VoiceBrain.Proposal next = Gen2VoiceBrain.propose(run, action);
            if (next == null) { say(lead.isEmpty() ? "嗯。" : lead); return; }
            proposal = next;
            confirmText.setText(next.label + "\n" + Gen2VoiceBrain.clip(next.command, 300));
            confirmBar.setVisibility(View.VISIBLE);
            say((lead.isEmpty() ? "" : lead + " ") + next.readBack);
        } catch (RuntimeException error) {
            say((lead.isEmpty() ? "" : lead + " ") + "還不能送出：" + safe(error));
        }
    }

    private String options(Gen2RunView run) {
        StringBuilder text = new StringBuilder();
        for (Gen2RunView.Choice choice : run.pending.options) {
            if (text.length() > 0) text.append("、");
            text.append(choice.label);
        }
        return text.toString();
    }

    private void listAll() {
        if (queue.isEmpty()) { say("目前沒有等你處理的流程。"); return; }
        StringBuilder text = new StringBuilder("等你處理的有 " + queue.size() + " 筆：");
        for (Gen2RunView run : queue) {
            text.append(run.number).append(" 號 ").append(run.workflowId)
                .append(run.pending == null ? "" : " " + run.pending.step).append("；");
        }
        say(text.append("目前在看 ").append(queue.get(0).number).append(" 號。").toString());
    }

    private void confirm() {
        Gen2VoiceBrain.Proposal sending = proposal;
        if (sending == null) { say("目前沒有要送出的東西。"); return; }
        clearProposal();
        busy = true;
        status("送出中…", VoiceOrbView.Phase.PROCESSING);
        executor.execute(() -> {
            try {
                repository.post(sending.run, sending.command);
                handler.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    Gen2NotificationCenter.cancel(this, sending.run.number);
                    line("⚙ ", "已送出 #" + sending.run.number + "：" + sending.label);
                    if (current() == sending.run) nextRun("送出了，GitHub 會在半分鐘內處理。");
                    else say("送出了。");
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    say("送出失敗：" + safe(error));
                });
            }
        });
    }

    private void cancelProposal(boolean speak) {
        boolean had = proposal != null;
        clearProposal();
        if (speak) say(had ? "好，不送。你想怎麼改？" : "好。");
    }

    private void clearProposal() {
        proposal = null;
        if (confirmBar != null) confirmBar.setVisibility(View.GONE);
    }

    private void openPage() {
        stopListening();
        stopSpeaking();
        Gen2RunView run = current();
        Intent intent = new Intent(this, Gen2RunsActivity.class);
        if (run != null) intent.putExtra(Gen2RunsActivity.EXTRA_ISSUE, run.number);
        startActivity(intent);
    }

    // ---------------------------------------------------------------- speech

    private void say(String text) {
        line("狐狸：", text);
        speak(text, true);
    }

    private void prepareTts() {
        tts = new TextToSpeech(this, result -> {
            if (result != TextToSpeech.SUCCESS || tts == null) return;
            tts.setLanguage(Locale.TAIWAN);
            tts.setSpeechRate(1.02f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { handler.post(() -> afterSpeech(id)); }
                @Override public void onError(String id) { handler.post(() -> afterSpeech(id)); }
            });
            ttsReady = true;
            if (pendingSpeech != null) { String text = pendingSpeech; pendingSpeech = null; speak(text, true); }
        });
    }

    private void speak(String text, boolean listenAfter) {
        if (destroyed) return;
        stopListening();
        if (!ttsReady) { pendingSpeech = listenAfter ? text : null; return; }
        speaking = true;
        status(proposal != null ? "說「確定」送出，或「取消」" : "狐狸說話中 · 點球可插話", VoiceOrbView.Phase.SUCCESS);
        Bundle params = new Bundle();
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, listenAfter ? SPEAK_ID : SPEAK_ID + "_end") == TextToSpeech.ERROR) {
            afterSpeech(listenAfter ? SPEAK_ID : "");
        }
    }

    private void afterSpeech(String id) {
        if (destroyed) return;
        speaking = false;
        if (endAfterSpeech) { endAfterSpeech = false; handler.postDelayed(this::finish, 600L); return; }
        if (SPEAK_ID.equals(id) && !isFinishing()) handler.postDelayed(this::startListeningWithPermission, 200L);
        else enterIdle();
    }

    private void stopSpeaking() {
        if (tts != null && speaking) tts.stop();
        speaking = false;
    }

    private void prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TAIWAN.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    }

    private void startListeningWithPermission() {
        if (destroyed || speaking || busy || isFinishing()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        if (recognizer == null || listening) { if (recognizer == null) status("這支手機沒有語音辨識服務，請用「用按的」", VoiceOrbView.Phase.ERROR); return; }
        listening = true;
        status(proposal != null ? "說「確定」送出，或「取消」" : "正在聽…", VoiceOrbView.Phase.LISTENING);
        handler.removeCallbacks(silenceTimeout);
        handler.postDelayed(silenceTimeout, SILENCE_TIMEOUT_MS);
        try { recognizer.startListening(recognizerIntent); } catch (RuntimeException error) { enterIdle(); }
    }

    private void stopListening() {
        handler.removeCallbacks(silenceTimeout);
        if (recognizer != null && listening) recognizer.cancel();
        listening = false;
    }

    private void enterIdle() {
        stopListening();
        if (!busy) status(proposal != null ? "點球後說「確定」，或按下方按鈕" : "待命中 · 點球繼續說", VoiceOrbView.Phase.IDLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListeningWithPermission();
        else status("沒有麥克風權限，請用「用按的」", VoiceOrbView.Phase.ERROR);
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { handler.removeCallbacks(silenceTimeout); }
    @Override public void onRmsChanged(float rmsdB) { orbView.setAmplitude(rmsdB); }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { listening = false; status("理解中…", VoiceOrbView.Phase.PROCESSING); }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onError(int error) {
        listening = false;
        enterIdle();
    }

    @Override
    public void onResults(Bundle results) {
        handler.removeCallbacks(silenceTimeout);
        listening = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        handle(matches == null || matches.isEmpty() ? "" : matches.get(0));
    }

    @Override
    public void onPartialResults(Bundle partial) {
        ArrayList<String> matches = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) statusView.setText("「" + matches.get(0) + "」");
        handler.removeCallbacks(silenceTimeout);
        handler.postDelayed(silenceTimeout, SILENCE_TIMEOUT_MS);
    }

    // ---------------------------------------------------------------- helpers

    private String safe(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error == null ? "未知錯誤" : error.getClass().getSimpleName();
        return SENSITIVE.matcher(message.trim()).replaceAll("[已隱藏]");
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = full();
        params.topMargin = dp(margin);
        return params;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
