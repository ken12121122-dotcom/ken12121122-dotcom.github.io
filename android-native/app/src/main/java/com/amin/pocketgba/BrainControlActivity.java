package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class BrainControlActivity extends Activity {
    private static final int COLOR_BG = 0xfff4f7f5;
    private static final int COLOR_SURFACE = 0xffffffff;
    private static final int COLOR_SOFT = 0xffeaf3ee;
    private static final int COLOR_TEXT = 0xff16231b;
    private static final int COLOR_MUTED = 0xff68766e;
    private static final int COLOR_ACCENT = 0xff19794b;
    private static final int COLOR_WARNING = 0xff9a5b00;
    private static final int COLOR_BORDER = 0xffd9e4de;
    private static final String UI_STORE = "amin_brain_ui";
    private static final String LAST_ISSUE = "last_issue";
    private static final String LAST_TASK = "last_task";
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|sk-(?:ant|proj)-[A-Za-z0-9_-]+|"
                    + "Bearer\\s+[A-Za-z0-9._~-]+|(?:OPENAI_API_KEY|ANTHROPIC_API_KEY)\\s*[:=]\\s*\\S+)");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private BrainAuthSession session;
    private BrainFeedRepository feedRepository;
    private SharedPreferences uiPreferences;
    private volatile GitHubDeviceFlowProtocol.DeviceCode pendingDeviceCode;
    private TextView loginStatus;
    private TextView deviceCodeView;
    private TextView operationStatus;
    private TextView feedView;
    private ProgressBar progress;
    private Button connectButton;
    private Button openGitHubButton;
    private Button approveLastButton;
    private Button cancelLastButton;
    private Button publishButton;
    private Button publishApproveButton;
    private Button refreshButton;
    private EditText titleInput;
    private EditText specificationInput;
    private EditText capabilitiesInput;
    private Spinner agentSpinner;
    private CheckBox retryCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new BrainAuthSession(this);
        feedRepository = new BrainFeedRepository(this);
        uiPreferences = getSharedPreferences(UI_STORE, MODE_PRIVATE);
        configureWindow();
        buildUi();
        BrainNotificationCenter.ensureChannel(this);
        requestNotificationPermission();
        updateSessionUi();
        showCachedFeed();
        if (session.hasSession()) {
            BrainFeedJobService.schedule(this);
            refreshFeed(false);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed.set(true);
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
        content.addView(text("AMIN BRAIN", 12f, true, COLOR_ACCENT), top(12));
        content.addView(text("手機任務中心", 28f, true, COLOR_TEXT), top(4));
        content.addView(text(
                "不同 Wi-Fi 也可使用。手機只連私人 GitHub Repo；電腦由 self-hosted runner 呼叫已訂閱登入的 Codex 與 Claude Code。",
                14f, false, COLOR_MUTED), top(8));

        LinearLayout authCard = card(COLOR_SURFACE);
        authCard.addView(text("私人 GitHub 連結", 18f, true, COLOR_TEXT), full());
        loginStatus = text("讀取中…", 13f, false, COLOR_MUTED);
        authCard.addView(loginStatus, top(7));
        deviceCodeView = text("", 21f, true, COLOR_ACCENT);
        deviceCodeView.setGravity(Gravity.CENTER);
        deviceCodeView.setVisibility(View.GONE);
        authCard.addView(deviceCodeView, top(12));
        openGitHubButton = secondaryButton("複製代碼並開啟 GitHub 驗證");
        openGitHubButton.setVisibility(View.GONE);
        openGitHubButton.setOnClickListener(view -> openDeviceVerification());
        authCard.addView(openGitHubButton, top(8));
        connectButton = primaryButton("連結 GitHub");
        connectButton.setOnClickListener(view -> startDeviceFlow());
        authCard.addView(connectButton, top(10));
        Button logoutButton = secondaryButton("登出並清除手機權杖");
        logoutButton.setOnClickListener(view -> logout());
        authCard.addView(logoutButton, top(8));
        content.addView(authCard, section());

        content.addView(text("新增任務", 19f, true, COLOR_TEXT), top(26));
        LinearLayout taskCard = card(COLOR_SURFACE);
        taskCard.addView(label("標題"), full());
        titleInput = input("例如：新增任務歷史頁", false);
        taskCard.addView(titleInput, top(4));
        taskCard.addView(label("開發規格"), top(12));
        specificationInput = input("清楚描述要新增或修改的 App 功能。不要貼 API key、token 或私密金鑰。", true);
        specificationInput.setMinLines(5);
        taskCard.addView(specificationInput, top(4));
        taskCard.addView(label("需要的能力（逗號分隔，至少一項）"), top(12));
        capabilitiesInput = input("android:ui, github:issues", false);
        capabilitiesInput.setText("android:ui");
        taskCard.addView(capabilitiesInput, top(4));
        taskCard.addView(label("主要 Agent"), top(12));
        agentSpinner = new Spinner(this);
        ArrayAdapter<String> agents = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"自動（Codex 主做）", "Codex", "Claude Code"});
        agentSpinner.setAdapter(agents);
        taskCard.addView(agentSpinner, top(4));
        retryCheck = new CheckBox(this);
        retryCheck.setText("工具暫時失敗時，允許交換 Agent 再試一次");
        retryCheck.setTextColor(COLOR_TEXT);
        retryCheck.setChecked(true);
        taskCard.addView(retryCheck, top(8));
        publishButton = secondaryButton("只發布為 suggested");
        publishButton.setOnClickListener(view -> publish(false));
        taskCard.addView(publishButton, top(10));
        publishApproveButton = primaryButton("發布並由我批准執行");
        publishApproveButton.setOnClickListener(view -> publish(true));
        taskCard.addView(publishApproveButton, top(8));
        approveLastButton = secondaryButton("批准上次只發布的任務");
        approveLastButton.setOnClickListener(view -> approveLast());
        taskCard.addView(approveLastButton, top(8));
        cancelLastButton = secondaryButton("取消上次任務");
        cancelLastButton.setOnClickListener(view -> cancelLast());
        taskCard.addView(cancelLastButton, top(8));
        content.addView(taskCard, top(8));

        operationStatus = text("建立 Issue 與加入批准標籤是兩個獨立、可稽核動作。", 13f, false, COLOR_MUTED);
        content.addView(operationStatus, top(10));

        LinearLayout statusHeader = new LinearLayout(this);
        statusHeader.setOrientation(LinearLayout.HORIZONTAL);
        statusHeader.setGravity(Gravity.CENTER_VERTICAL);
        statusHeader.addView(text("任務狀態", 19f, true, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        refreshButton = secondaryButton("重新整理");
        refreshButton.setOnClickListener(view -> refreshFeed(false));
        statusHeader.addView(refreshButton, wrap());
        content.addView(statusHeader, top(26));
        LinearLayout feedCard = card(COLOR_SURFACE);
        feedView = text("尚無狀態。", 13f, false, COLOR_TEXT);
        feedCard.addView(feedView, full());
        content.addView(feedCard, top(8));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        progress.setVisibility(View.GONE);
        content.addView(progress, top(12));
        content.addView(text(
                "手機無權修改公開 App Repo、Actions、Secrets、Workflow、PR 或版本通道；合併、簽章與發布仍走既有閘門。",
                12f, false, COLOR_WARNING), top(18));
        setContentView(scroll);
    }

    private void startDeviceFlow() {
        if (!session.isConfigured()) {
            operationStatus.setText("此 APK 尚未設定 GitHub App 公開 client ID；完成 GitHub App 註冊後才可連結。 ");
            return;
        }
        setBusy(true, "正在向 GitHub 取得一次性裝置驗證碼…");
        executor.execute(() -> {
            try {
                GitHubDeviceFlowClient client = session.deviceFlowClient();
                GitHubDeviceFlowProtocol.DeviceCode code = client.requestCode();
                pendingDeviceCode = code;
                runOnUiThread(() -> {
                    deviceCodeView.setText(code.userCode());
                    deviceCodeView.setVisibility(View.VISIBLE);
                    openGitHubButton.setVisibility(View.VISIBLE);
                    operationStatus.setText("請複製代碼並在 GitHub 完成授權；本頁會自動等待結果。 ");
                });
                int interval = code.intervalSeconds();
                while (!destroyed.get() && System.currentTimeMillis() < code.expiresAtMillis()) {
                    Thread.sleep(interval * 1000L);
                    GitHubDeviceFlowProtocol.Poll poll = client.poll(code, interval);
                    interval = poll.nextIntervalSeconds();
                    if (poll.status() == GitHubDeviceFlowProtocol.PollStatus.PENDING) continue;
                    if (poll.status() == GitHubDeviceFlowProtocol.PollStatus.SUCCESS) {
                        session.verifyAndSave(poll.token());
                        BrainFeedJobService.schedule(this);
                        runOnUiThread(() -> {
                            pendingDeviceCode = null;
                            deviceCodeView.setVisibility(View.GONE);
                            openGitHubButton.setVisibility(View.GONE);
                            updateSessionUi();
                            setBusy(false, "GitHub 擁有者與私人 Repo 驗證成功。 ");
                        });
                        refreshFeed(false);
                        return;
                    }
                    throw new SecurityException(poll.status() == GitHubDeviceFlowProtocol.PollStatus.DENIED
                            ? "你已拒絕 GitHub 授權。 " : "GitHub 裝置驗證碼已失效。 ");
                }
                throw new SecurityException("GitHub 裝置驗證碼已逾時。 ");
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                runOnUiThread(() -> setBusy(false, safeMessage(error)));
            }
        });
    }

    private void openDeviceVerification() {
        GitHubDeviceFlowProtocol.DeviceCode code = pendingDeviceCode;
        if (code == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("GitHub device code", code.userCode()));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri())));
            Toast.makeText(this, "驗證碼已複製。", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            operationStatus.setText("無法開啟 GitHub 驗證頁：" + safeMessage(error));
        }
    }

    private void publish(boolean approveImmediately) {
        setBusy(true, approveImmediately ? "正在發布並批准私人任務…" : "正在發布私人任務…");
        final BrainTaskEnvelope envelope;
        try {
            envelope = BrainTaskEnvelope.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    titleInput.getText().toString(), specificationInput.getText().toString(),
                    selectedAgent(), capabilities(), retryCheck.isChecked() ? 2 : 1, Instant.now());
        } catch (Exception error) {
            setBusy(false, safeMessage(error));
            return;
        }
        executor.execute(() -> {
            try {
                GitHubBrainApi api = session.requireVerifiedApi();
                GitHubBrainApi.Issue issue = api.createIssue(envelope);
                uiPreferences.edit().putInt(LAST_ISSUE, issue.number())
                        .putString(LAST_TASK, envelope.taskUuid()).commit();
                if (approveImmediately) api.approveIssue(issue.number());
                runOnUiThread(() -> {
                    approveLastButton.setEnabled(!approveImmediately);
                    setBusy(false, approveImmediately
                            ? "私人任務 #" + issue.number() + " 已發布並批准；電腦 runner 會自動執行。"
                            : "私人任務 #" + issue.number() + " 已發布為 suggested，尚未派工。 ");
                });
            } catch (Exception error) {
                runOnUiThread(() -> setBusy(false, safeMessage(error)));
            }
        });
    }

    private void approveLast() {
        int issue = uiPreferences.getInt(LAST_ISSUE, 0);
        if (issue <= 0) {
            operationStatus.setText("沒有可批准的上次任務。 ");
            return;
        }
        setBusy(true, "正在加入擁有者批准標籤…");
        executor.execute(() -> {
            try {
                session.requireVerifiedApi().approveIssue(issue);
                runOnUiThread(() -> {
                    approveLastButton.setEnabled(false);
                    setBusy(false, "私人任務 #" + issue + " 已批准；TASK identity 不會改變。 ");
                });
            } catch (Exception error) {
                runOnUiThread(() -> setBusy(false, safeMessage(error)));
            }
        });
    }

    private void cancelLast() {
        int issue = uiPreferences.getInt(LAST_ISSUE, 0);
        if (issue <= 0) {
            operationStatus.setText("沒有可取消的上次任務。 ");
            return;
        }
        setBusy(true, "正在送出擁有者取消動作…");
        executor.execute(() -> {
            try {
                session.requireVerifiedApi().cancelIssue(issue);
                runOnUiThread(() -> setBusy(false,
                        "私人任務 #" + issue + " 已送出取消；TASK identity 不會改變。 "));
            } catch (Exception error) {
                runOnUiThread(() -> setBusy(false, safeMessage(error)));
            }
        });
    }

    private void refreshFeed(boolean emitNotification) {
        runOnUiThread(() -> {
            refreshButton.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
        });
        executor.execute(() -> {
            try {
                BrainFeedRepository.Refresh result = feedRepository.refresh(emitNotification);
                runOnUiThread(() -> {
                    feedView.setText(result.summary());
                    refreshButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    feedView.setText("狀態讀取失敗：" + safeMessage(error));
                    refreshButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                });
            }
        });
    }

    private void showCachedFeed() {
        try {
            org.json.JSONObject cached = feedRepository.cachedFeed();
            if (cached != null) feedView.setText(BrainFeedState.summary(cached));
        } catch (Exception ignored) { }
    }

    private void logout() {
        session.logout();
        feedRepository.clear();
        BrainFeedJobService.cancel(this);
        uiPreferences.edit().remove(LAST_ISSUE).remove(LAST_TASK).commit();
        updateSessionUi();
        feedView.setText("已清除手機登入與 feed 快取。 ");
        operationStatus.setText("已登出。 ");
    }

    private void updateSessionUi() {
        boolean configured = session.isConfigured();
        boolean loggedIn = session.hasSession();
        if (!configured) loginStatus.setText("等待 GitHub App 公開 client ID；APK 內不會放 client secret 或 private key。 ");
        else if (loggedIn) loginStatus.setText("已連結 ken12121122-dotcom，私人控制 Repo 可用。 ");
        else loginStatus.setText("尚未連結；使用 GitHub Device Flow 登入。 ");
        connectButton.setEnabled(configured && !loggedIn);
        publishButton.setEnabled(loggedIn);
        publishApproveButton.setEnabled(loggedIn);
        refreshButton.setEnabled(loggedIn);
        approveLastButton.setEnabled(loggedIn && uiPreferences.getInt(LAST_ISSUE, 0) > 0);
        cancelLastButton.setEnabled(loggedIn && uiPreferences.getInt(LAST_ISSUE, 0) > 0);
    }

    private List<String> capabilities() {
        List<String> values = new ArrayList<>();
        String raw = capabilitiesInput.getText().toString().trim();
        if (raw.isEmpty()) return values;
        for (String part : raw.split("[,\\n]")) {
            String value = part.trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    private String selectedAgent() {
        int position = agentSpinner.getSelectedItemPosition();
        return position == 1 ? "codex" : position == 2 ? "claude" : "auto";
    }

    private void setBusy(boolean busy, String status) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        connectButton.setEnabled(!busy && session.isConfigured() && !session.hasSession());
        boolean loggedIn = session.hasSession();
        publishButton.setEnabled(!busy && loggedIn);
        publishApproveButton.setEnabled(!busy && loggedIn);
        approveLastButton.setEnabled(!busy && loggedIn && uiPreferences.getInt(LAST_ISSUE, 0) > 0);
        operationStatus.setText(status);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4107);
        }
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
    private LinearLayout.LayoutParams section() { return top(22); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String safeMessage(Throwable error) {
        String message = error == null ? "未知錯誤" : error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        return SENSITIVE.matcher(message).replaceAll("[已隱藏]");
    }
}
