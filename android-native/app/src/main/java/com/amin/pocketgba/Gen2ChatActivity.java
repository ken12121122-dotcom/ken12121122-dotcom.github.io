package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * The fox assistant: chat (typed or spoken) about GEN2 work. The AI decides
 * what the OWNER means; there are no command words. New tasks are scoped in
 * conversation and started as GEN2 runs; the GEN2 Agent then works on them
 * and the OWNER is called back by push and in Knowledge World.
 *
 * Anything that changes GitHub is shown as a card first, written by the app
 * from the validated action, and runs only after a later OWNER message the AI
 * judges as agreement (or a tap on the card).
 */
public final class Gen2ChatActivity extends Activity implements RecognitionListener {
    static final String EXTRA_ISSUE = Gen2RunsActivity.EXTRA_ISSUE;
    private static final int REQUEST_RECORD_AUDIO = 6512;
    private static final int MAX_HISTORY = 20;
    private static final int START_POLLS = 24;
    private static final long START_POLL_MS = 5000L;
    private static final String SPEAK_ID = "amin_gen2_chat";
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|Bearer\\s+[A-Za-z0-9._~-]+|sk-[A-Za-z0-9_-]+)");
    private static final int INK = 0xff1f1b33, PAPER = 0xfff4f1e6, ROOM = 0xff14121c, SHELL = 0xff4b3fa6, RED = 0xffc8325a;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<LlmClient.Message> history = new ArrayList<>();
    private final List<Gen2RunView> runs = new ArrayList<>();
    private final List<Gen2WorkflowCatalog.Workflow> workflows = new ArrayList<>();

    private Gen2RunRepository repository;
    private LinearLayout transcript;
    private ScrollView transcriptScroll;
    private LinearLayout card;
    private TextView cardText;
    private EditText input;
    private TextView status;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean voiceMode;
    private boolean busy;
    private boolean destroyed;
    private volatile boolean loaded;
    private int focusIssue;
    private Gen2ChatBrain.Proposal waiting;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new Gen2RunRepository(this);
        focusIssue = Gen2VoiceActivity.issueFrom(getIntent());
        getWindow().setStatusBarColor(ROOM);
        getWindow().setNavigationBarColor(ROOM);
        buildUi();
        load();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int issue = Gen2VoiceActivity.issueFrom(intent);
        if (issue > 0 && issue != focusIssue) { focusIssue = issue; clearCard(); load(); }
    }

    @Override
    protected void onPause() {
        if (recognizer != null) recognizer.cancel();
        if (tts != null) tts.stop();
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

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ROOM);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(12));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("🦊 狐狸", 18, true, PAPER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button close = button("回到世界", false);
        close.setOnClickListener(v -> finish());
        head.addView(close, new LinearLayout.LayoutParams(-2, dp(40)));
        content.addView(head, full());
        TextView model = text((LlmConfigStore.hasApiKey(this) ? "🟢 " : "⚪ 未設定 AI · ")
                + LlmConfigStore.label(this) + " · 對話內容會送到這個 AI", 11, false, 0xff8c86b5);
        content.addView(model, top(2));

        transcriptScroll = new ScrollView(this);
        transcript = new LinearLayout(this);
        transcript.setOrientation(LinearLayout.VERTICAL);
        transcriptScroll.addView(transcript, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = dp(10);
        content.addView(transcriptScroll, scrollParams);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(box(PAPER, INK));
        card.setContentDescription("gen2-chat-card");
        card.setVisibility(View.GONE);
        cardText = text("", 14, false, INK);
        card.addView(cardText, full());
        LinearLayout cardRow = new LinearLayout(this);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        Button yes = button("就這樣做", true);
        yes.setContentDescription("gen2-chat-card-yes");
        yes.setOnClickListener(v -> runWaiting(""));
        Button no = button("先不要", false);
        no.setContentDescription("gen2-chat-card-no");
        no.setOnClickListener(v -> { clearCard(); fox("好，不做。想怎麼改再跟我說。", false); });
        cardRow.addView(yes, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams noParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        noParams.leftMargin = dp(8);
        cardRow.addView(no, noParams);
        card.addView(cardRow, top(8));
        content.addView(card, top(8));

        status = text("", 12, false, 0xff8c86b5);
        content.addView(status, top(6));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setHint("跟狐狸說…");
        input.setHintTextColor(0xff8c86b5);
        input.setTextColor(PAPER);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(4);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setContentDescription("gen2-chat-input");
        input.setOnEditorActionListener((v, id, event) -> { if (id == EditorInfo.IME_ACTION_SEND) { sendTyped(); return true; } return false; });
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        Button mic = button("🎤", false);
        mic.setContentDescription("gen2-chat-mic");
        mic.setOnClickListener(v -> listen());
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(dp(52), dp(48));
        micParams.leftMargin = dp(6);
        row.addView(mic, micParams);
        Button send = button("送", true);
        send.setContentDescription("gen2-chat-send");
        send.setOnClickListener(v -> sendTyped());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(52), dp(48));
        sendParams.leftMargin = dp(6);
        row.addView(send, sendParams);
        content.addView(row, top(6));
        setContentView(root);
    }

    private void bubble(String who, String value, boolean fox) {
        TextView view = text((who.isEmpty() ? "" : who + "\n") + value, 15, false, fox ? INK : PAPER);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setBackground(fox ? box(PAPER, INK) : box(SHELL, SHELL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = dp(8);
        params.gravity = fox ? Gravity.START : Gravity.END;
        if (fox) params.rightMargin = dp(36); else params.leftMargin = dp(36);
        transcript.addView(view, params);
        transcriptScroll.post(() -> transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void fox(String value, boolean speakIt) {
        bubble("狐狸", value, true);
        if (speakIt && voiceMode) speak(value);
    }

    private void setStatus(String value) { status.setText(value == null ? "" : value); }

    // ---------------------------------------------------------------- data

    private void load() {
        busy = true;
        setStatus("狐狸在看任務…");
        executor.execute(() -> {
            String problem = null;
            List<Gen2RunView> fresh = new ArrayList<>();
            List<Gen2WorkflowCatalog.Workflow> catalog = new ArrayList<>();
            try { fresh = repository.refresh(false); }
            catch (Exception error) {
                problem = new BrainAuthSession(this).hasSession() ? "讀不到 GEN2 流程：" + safe(error) : "還沒連結 GitHub，請先到 AMIN Brain 連結。";
            }
            if (problem == null) {
                try { catalog = repository.workflows(false); }
                catch (Exception error) { problem = "讀不到可啟動的流程：" + safe(error); }
            }
            final String note = problem;
            final List<Gen2RunView> loadedRuns = fresh;
            final List<Gen2WorkflowCatalog.Workflow> loadedWorkflows = catalog;
            handler.post(() -> {
                if (destroyed) return;
                busy = false;
                loaded = true;
                setStatus("");
                useState(loadedRuns, loadedWorkflows);
                fox(Gen2ChatBrain.greeting(runs, Gen2ChatBrain.findRun(runs, focusIssue)), true);
                if (note != null) bubble("⚙", note, true);
                if (!LlmConfigStore.hasApiKey(this)) bubble("⚙", "還沒設定 AI，狐狸聽不懂聊天。請到語音球設定 AI 和 API Key。", true);
            });
        });
    }

    boolean isLoaded() { return loaded; }

    /** Instrumentation hook and load result: the runs and workflows the fox knows about. */
    void useState(List<Gen2RunView> openRuns, List<Gen2WorkflowCatalog.Workflow> catalog) {
        runs.clear();
        if (openRuns != null) runs.addAll(openRuns);
        workflows.clear();
        if (catalog != null) workflows.addAll(catalog);
    }

    // ---------------------------------------------------------------- turns

    private void sendTyped() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || busy) return;
        input.setText("");
        voiceMode = false;
        hear(text);
    }

    /** One OWNER message, typed or spoken. */
    void hear(String spoken) {
        String text = spoken == null ? "" : spoken.trim();
        if (text.isEmpty()) return;
        bubble("你", text, false);
        if (!LlmConfigStore.hasApiKey(this)) {
            fox("我還沒有 AI 可以用，聽不懂聊天。請到語音球設定 AI；卡片上的按鈕還是可以按。", true);
            return;
        }
        history.add(new LlmClient.Message("user", text));
        while (history.size() > MAX_HISTORY) history.remove(0);
        busy = true;
        setStatus("狐狸在想…");
        String prompt = Gen2ChatBrain.systemPrompt(workflows, runs, Gen2ChatBrain.findRun(runs, focusIssue), waiting);
        LlmClient.send(this, prompt, new ArrayList<>(history), new LlmClient.Callback() {
            @Override public void onSuccess(String reply) { handler.post(() -> modelReply(reply)); }
            @Override public void onError(String message) {
                handler.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    setStatus("");
                    if (!history.isEmpty()) history.remove(history.size() - 1);
                    fox("AI 連線失敗：" + SENSITIVE.matcher(message == null ? "" : message).replaceAll("[已隱藏]"), true);
                });
            }
        });
    }

    /** Applies the model's reply (also an instrumentation hook). */
    void modelReply(String raw) {
        if (destroyed) return;
        busy = false;
        setStatus("");
        Gen2ChatBrain.Reply reply = Gen2ChatBrain.parseReply(raw);
        history.add(new LlmClient.Message("assistant", reply.say));
        Gen2ChatBrain.Proposal before = waiting;
        if (before != null && Boolean.TRUE.equals(reply.confirm)) {
            // Agreement to what was already on the card; any new action in this reply is ignored.
            runWaiting(reply.say);
            return;
        }
        if (before != null && Boolean.FALSE.equals(reply.confirm)) clearCard();
        Gen2ChatBrain.Proposal next = null;
        String problem = null;
        try { next = Gen2ChatBrain.propose(reply.action, workflows, runs); }
        catch (RuntimeException error) { problem = safe(error); }
        String say = reply.say.isEmpty() && next != null ? next.readBack + "。要我這樣做嗎？" : reply.say;
        if (problem != null) say = (say.isEmpty() ? "" : say + " ") + problem;
        if (next != null) showCard(next);
        if (!say.isEmpty()) fox(say, true);
    }

    private void showCard(Gen2ChatBrain.Proposal proposal) {
        waiting = proposal;
        cardText.setText(proposal.title + "\n" + proposal.readBack);
        card.setVisibility(View.VISIBLE);
    }

    private void clearCard() {
        waiting = null;
        if (card != null) card.setVisibility(View.GONE);
    }

    String waitingSummary() { return waiting == null ? null : waiting.type + ":" + (waiting.isStart() ? waiting.workflow.id : waiting.command); }

    private void runWaiting(String lead) {
        Gen2ChatBrain.Proposal sending = waiting;
        if (sending == null || busy) return;
        clearCard();
        busy = true;
        if (!lead.isEmpty()) fox(lead, false);
        setStatus(sending.isStart() ? "交給 GitHub 開始…" : "送出中…");
        executor.execute(() -> {
            try {
                String done = sending.isStart() ? start(sending) : post(sending);
                handler.post(() -> { if (destroyed) return; busy = false; setStatus(""); fox(done, true); });
            } catch (Exception error) {
                handler.post(() -> { if (destroyed) return; busy = false; setStatus(""); fox("沒有成功：" + safe(error), true); });
            }
        });
    }

    private String post(Gen2ChatBrain.Proposal proposal) throws Exception {
        repository.post(proposal.run, proposal.command);
        Gen2NotificationCenter.cancel(this, proposal.run.number);
        return "送出了（#" + proposal.run.number + " " + proposal.title + "）。GitHub 半分鐘內會處理，有下一步我再來找你。";
    }

    /** Starts the run, waits for its Issue, and hands over the first-step material. */
    private String start(Gen2ChatBrain.Proposal proposal) throws Exception {
        int before = Gen2ChatBrain.highest(repository.refresh(false));
        repository.startRun(proposal.workflow.id, proposal.scope);
        Gen2RunView run = null;
        for (int i = 0; i < START_POLLS && run == null && !destroyed; i++) {
            Thread.sleep(START_POLL_MS);
            run = Gen2ChatBrain.startedRun(repository.refresh(false), proposal.workflow.id, before);
        }
        if (run == null) return "已經請 GitHub 開始「" + proposal.workflow.title + "」，但還沒看到新的任務。等一下到任務告示板看看。";
        if (!proposal.material.isEmpty() && run.pending != null && run.pending.isInput()) {
            repository.post(run, Gen2Command.input(run, proposal.material));
            return "開始了，是 #" + run.number + "。資料也交給它了，我先去處理，好了或需要你的時候會來找你。";
        }
        return "開始了，是 #" + run.number + "。我先去處理，需要你的時候會來找你。";
    }

    // ---------------------------------------------------------------- voice

    private void listen() {
        if (busy) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { fox("這支手機沒有語音辨識，請用打字。", false); return; }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        }
        if (tts != null) tts.stop();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW");
        setStatus("在聽…");
        recognizer.startListening(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_RECORD_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) listen();
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { setStatus("聽懂中…"); }
    @Override public void onEvent(int eventType, Bundle params) { }
    @Override public void onPartialResults(Bundle partial) { }
    @Override public void onError(int error) { setStatus(error == SpeechRecognizer.ERROR_NO_MATCH ? "沒聽清楚，再按一次 🎤" : ""); }

    @Override
    public void onResults(Bundle results) {
        setStatus("");
        ArrayList<String> heard = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (heard == null || heard.isEmpty()) return;
        voiceMode = true;
        hear(heard.get(0));
    }

    private void speak(String value) {
        if (tts == null) {
            tts = new TextToSpeech(this, code -> {
                ttsReady = code == TextToSpeech.SUCCESS;
                if (ttsReady) { tts.setLanguage(Locale.TAIWAN); speak(value); }
            });
            return;
        }
        if (ttsReady) tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, SPEAK_ID);
    }

    // ---------------------------------------------------------------- helpers

    private String safe(Throwable error) {
        String message = error == null || error.getMessage() == null ? "未知錯誤" : error.getMessage().trim();
        return SENSITIVE.matcher(message).replaceAll("[已隱藏]");
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.25f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextColor(primary ? Color.WHITE : INK);
        view.setBackground(box(primary ? RED : PAPER, INK));
        return view;
    }

    private GradientDrawable box(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(6));
        drawable.setStroke(dp(2), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = full();
        params.topMargin = dp(margin);
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    /** Lets other screens point the fox at a run (0 = just talk). */
    static Intent intent(android.content.Context context, int issue) {
        Intent intent = new Intent(context, Gen2ChatActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (issue > 0) intent.putExtra(EXTRA_ISSUE, issue);
        return intent;
    }

}
