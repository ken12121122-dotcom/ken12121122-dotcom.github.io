package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * GEN2 runs waiting for the OWNER. Each button posts exactly one /gen2 comment
 * as the signed-in OWNER on the run Issue in gen2-knowledge; the run engine on
 * GitHub validates it and moves the run. Nothing is decided on the phone.
 */
public final class Gen2RunsActivity extends Activity {
    static final String EXTRA_ISSUE = "gen2_issue";
    private static final int COLOR_BG = 0xfff4f7f5;
    private static final int COLOR_SURFACE = 0xffffffff;
    private static final int COLOR_SOFT = 0xffeaf3ee;
    private static final int COLOR_HIGHLIGHT = 0xfffff6e0;
    private static final int COLOR_TEXT = 0xff16231b;
    private static final int COLOR_MUTED = 0xff68766e;
    private static final int COLOR_ACCENT = 0xff19794b;
    private static final int COLOR_WARNING = 0xff9a5b00;
    private static final int COLOR_BORDER = 0xffd9e4de;
    private static final long REFRESH_AFTER_POST_MS = 30_000L;
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|Bearer\\s+[A-Za-z0-9._~-]+)");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final Runnable delayedRefresh = this::refresh;
    private BrainAuthSession session;
    private Gen2RunRepository repository;
    private TextView statusView;
    private LinearLayout listContainer;
    private LinearLayout loginCard;
    private ProgressBar progress;
    private Button refreshButton;
    private int highlightIssue;
    private boolean linked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new BrainAuthSession(this);
        repository = new Gen2RunRepository(this);
        highlightIssue = getIntent().getIntExtra(EXTRA_ISSUE, 0);
        configureWindow();
        buildUi();
        Gen2NotificationCenter.ensureChannel(this);
        applySession(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from AMIN Brain after linking GitHub: start without reopening this screen.
        if (!linked && session.hasSession()) applySession(true);
    }

    private void applySession(boolean load) {
        linked = session.hasSession();
        loginCard.setVisibility(linked ? View.GONE : View.VISIBLE);
        refreshButton.setEnabled(linked);
        if (linked) {
            requestNotificationPermission();
            BrainFeedJobService.schedule(this);
            if (load) refresh();
        } else {
            statusView.setText("尚未連結 GitHub。 ");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        highlightIssue = intent.getIntExtra(EXTRA_ISSUE, 0);
        if (session.hasSession()) refresh();
    }

    @Override
    protected void onDestroy() {
        destroyed.set(true);
        handler.removeCallbacks(delayedRefresh);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(40));
        scroll.addView(content);

        Button back = textButton("← 返回控制台");
        back.setOnClickListener(view -> finish());
        content.addView(back, wrap());
        content.addView(text("GEN2 流程", 12f, true, COLOR_ACCENT), top(12));
        content.addView(text("待核准", 28f, true, COLOR_TEXT), top(4));
        content.addView(text(
                "流程在 GitHub（gen2-knowledge）執行，跑到需要你的步驟就停下來。按鈕會用你的 GitHub 帳號送出指令，由 GitHub 端驗證後才繼續。",
                14f, false, COLOR_MUTED), top(8));

        loginCard = card(COLOR_SURFACE);
        loginCard.addView(text("尚未連結 GitHub", 18f, true, COLOR_TEXT), full());
        loginCard.addView(text("與 AMIN Brain 共用同一個 GitHub 登入。請先到 AMIN Brain 連結；GitHub App 也需要授權 gen2-knowledge。",
                13f, false, COLOR_MUTED), top(7));
        Button linkButton = primaryButton("前往 AMIN Brain 連結 GitHub");
        linkButton.setOnClickListener(view -> startActivity(new Intent(this, BrainControlActivity.class)));
        loginCard.addView(linkButton, top(10));
        content.addView(loginCard, top(18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        statusView = text("", 13f, false, COLOR_MUTED);
        statusView.setContentDescription("gen2-status");
        header.addView(statusView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        refreshButton = secondaryButton("重新整理");
        refreshButton.setOnClickListener(view -> refresh());
        header.addView(refreshButton, wrap());
        content.addView(header, top(18));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        progress.setVisibility(View.GONE);
        content.addView(progress, top(8));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, top(8));

        content.addView(text("手機只能對 gen2-knowledge 的流程 Issue 留言；不會修改知識庫、合併 PR 或寫入 Calendar。",
                12f, false, COLOR_WARNING), top(18));
        setContentView(scroll);
    }

    private void refresh() {
        if (destroyed.get()) return;
        handler.removeCallbacks(delayedRefresh);
        refreshButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        statusView.setText("讀取中…");
        executor.execute(() -> {
            try {
                List<Gen2RunView> runs = repository.refresh(false);
                runOnUiThread(() -> {
                    if (destroyed.get()) return;
                    showRuns(runs);
                    refreshButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed.get()) return;
                    statusView.setText("讀取失敗：" + safeMessage(error));
                    refreshButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                });
            }
        });
    }

    /** Renders the run list (package-private for instrumentation tests). */
    void showRuns(List<Gen2RunView> runs) {
        listContainer.removeAllViews();
        int waiting = 0;
        for (Gen2RunView run : runs) if (run.needsOwner()) waiting++;
        statusView.setText(runs.isEmpty() ? "目前沒有進行中的 GEN2 流程。 "
                : waiting + " 個等你處理 · 共 " + runs.size() + " 個進行中");
        for (Gen2RunView run : runs) listContainer.addView(runCard(run), top(10));
    }

    private LinearLayout runCard(Gen2RunView run) {
        LinearLayout card = card(run.number == highlightIssue ? COLOR_HIGHLIGHT : COLOR_SURFACE);
        card.setContentDescription("gen2-run-" + run.number);
        List<View> actions = new ArrayList<>();
        card.addView(text(run.workflowId + "｜" + run.workflowTitle, 16f, true, COLOR_TEXT), full());
        card.addView(text("#" + run.number + " · " + run.statusText(), 12f, true,
                run.needsOwner() ? COLOR_WARNING : COLOR_MUTED), top(4));

        Gen2RunView.Pending pending = run.pending;
        if (pending != null) {
            card.addView(text(pending.step + "｜" + pending.title
                    + (pending.attempt > 1 ? "（第 " + pending.attempt + " 次）" : ""), 14f, false, COLOR_TEXT), top(8));
            if (pending.isGate()) {
                if (!pending.reviewText.isEmpty()) {
                    card.addView(label("要審核的內容（" + pending.reviewTitle + "）"), top(10));
                    TextView review = text(pending.reviewText, 13f, false, COLOR_TEXT);
                    review.setTextIsSelectable(true);
                    review.setPadding(dp(10), dp(8), dp(10), dp(8));
                    review.setBackground(rounded(COLOR_SOFT, 10, COLOR_BORDER, 0));
                    card.addView(review, top(4));
                }
                EditText comment = input("說明（標示「必須寫說明」的選項一定要填）", true);
                comment.setContentDescription("gen2-comment-" + run.number);
                card.addView(comment, top(10));
                actions.add(comment);
                boolean first = true;
                for (Gen2RunView.Choice choice : pending.options) {
                    Button button = first ? primaryButton(choice.label) : secondaryButton(choice.label);
                    if (choice.commentRequired) button.setText(choice.label + "（必須寫說明）");
                    button.setContentDescription("gen2-option-" + run.number + "-" + choice.id);
                    button.setOnClickListener(view -> submit(run, actions, choice.label,
                            () -> Gen2Command.decide(run, choice.id, comment.getText().toString())));
                    card.addView(button, top(8));
                    actions.add(button);
                    first = false;
                }
            } else if (pending.isInput()) {
                if (!pending.prompt.isEmpty()) card.addView(text(pending.prompt, 13f, false, COLOR_MUTED), top(6));
                EditText material = input("在這裡貼上要提供的資料", true);
                material.setMinLines(4);
                material.setContentDescription("gen2-input-" + run.number);
                card.addView(material, top(8));
                actions.add(material);
                Button send = primaryButton("送出資料");
                send.setContentDescription("gen2-send-" + run.number);
                send.setOnClickListener(view -> submit(run, actions, "送出資料",
                        () -> Gen2Command.input(run, material.getText().toString())));
                card.addView(send, top(8));
                actions.add(send);
            } else {
                card.addView(text("等待 Agent 執行 " + pending.skill + "，完成後會自動進到下一步。", 13f, false, COLOR_MUTED), top(6));
            }
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (!run.htmlUrl.isEmpty()) {
            Button open = textButton("在 GitHub 開啟");
            open.setOnClickListener(view -> openUrl(run.htmlUrl));
            row.addView(open, wrap());
        }
        Button stop = textButton("停止這次流程");
        stop.setTextColor(COLOR_WARNING);
        stop.setContentDescription("gen2-cancel-" + run.number);
        stop.setOnClickListener(view -> submit(run, actions, "停止這次流程",
                () -> Gen2Command.cancel(run, "")));
        row.addView(stop, wrap());
        actions.add(stop);
        card.addView(row, top(6));
        return card;
    }

    private interface CommandSource { String build(); }

    private void submit(Gen2RunView run, List<View> actions, String label, CommandSource source) {
        final String command;
        try {
            command = source.build();
        } catch (Exception error) {
            statusView.setText(safeMessage(error));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("確定送出「" + label + "」？")
                .setMessage(run.workflowId + " #" + run.number + "\n\n" + preview(command))
                .setNegativeButton("取消", null)
                .setPositiveButton("送出", (dialog, which) -> post(run, actions, command))
                .show();
    }

    private void post(Gen2RunView run, List<View> actions, String command) {
        for (View view : actions) view.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        statusView.setText("送出中…");
        executor.execute(() -> {
            try {
                repository.post(run, command);
                runOnUiThread(() -> {
                    if (destroyed.get()) return;
                    progress.setVisibility(View.GONE);
                    statusView.setText("已送出 #" + run.number + "；GitHub 約 30 秒內處理，稍後自動重新整理。 ");
                    Gen2NotificationCenter.cancel(this, run.number);
                    handler.postDelayed(delayedRefresh, REFRESH_AFTER_POST_MS);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed.get()) return;
                    progress.setVisibility(View.GONE);
                    for (View view : actions) view.setEnabled(true);
                    statusView.setText("送出失敗：" + safeMessage(error));
                });
            }
        });
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception error) { statusView.setText("無法開啟：" + safeMessage(error)); }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4108);
        }
    }

    private static String preview(String text) {
        return text.length() > 400 ? text.substring(0, 400) + "…" : text;
    }

    private EditText input(String hint, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14f);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setBackgroundTintList(ColorStateList.valueOf(COLOR_ACCENT));
        input.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        return input;
    }

    private TextView label(String value) { return text(value, 13f, true, COLOR_MUTED); }

    private LinearLayout card(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(color, 18, COLOR_BORDER, 1));
        card.setElevation(dp(1));
        return card;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(50));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_ACCENT));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(COLOR_ACCENT);
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setMinHeight(dp(46));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_SOFT));
        return button;
    }

    private Button textButton(String value) {
        Button button = secondaryButton(value);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, dp(4), dp(10), dp(4));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        return button;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.25f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = full();
        params.topMargin = dp(margin);
        return params;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String safeMessage(Throwable error) {
        String message = error == null ? "未知錯誤" : error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        return SENSITIVE.matcher(message).replaceAll("[已隱藏]");
    }
}
