package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ControlCenterActivity extends Activity {
    private static final int COLOR_BG = 0xfff4f7f5;
    private static final int COLOR_SURFACE = 0xffffffff;
    private static final int COLOR_SURFACE_SOFT = 0xffeaf3ee;
    private static final int COLOR_TEXT = 0xff16231b;
    private static final int COLOR_MUTED = 0xff68766e;
    private static final int COLOR_ACCENT = 0xff19794b;
    private static final int COLOR_ACCENT_DARK = 0xff105f39;
    private static final int COLOR_BORDER = 0xffd9e4de;
    private static final int COLOR_WARNING = 0xff9a5b00;

    private static final String LIVE_RUNTIME_MANIFEST_URL =
            "https://ken12121122-dotcom.github.io/amin-vault/runtime-manifest.json";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private TextView networkValue;
    private TextView nativeValue;
    private TextView runtimeValue;
    private TextView nativeUpdateValue;
    private TextView detectionSummary;
    private ProgressBar detectionProgress;
    private Button detectButton;
    private Button detailsButton;
    private LinearLayout technicalDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        configureWindow();
        buildUi();
        detectEverything();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (networkValue != null) refreshNetwork();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(22), dp(20), dp(36));
        content.setBackgroundColor(COLOR_BG);
        scroll.addView(content);

        content.addView(text("AMIN POCKET GBA", 12f, true, COLOR_ACCENT), fullWidth());

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleRowParams = fullWidth();
        titleRowParams.topMargin = dp(4);
        content.addView(titleRow, titleRowParams);

        titleRow.addView(
                text("遊戲控制台", 30f, true, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        );
        titleRow.addView(chip("BRIDGE 1"), wrapContent());

        TextView intro = text(
                "首頁保持直向；進入模擬器才切換橫向。Bridge 會從 GitHub Pages 接收新的遊戲 Runtime。",
                14f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams introParams = fullWidth();
        introParams.topMargin = dp(8);
        content.addView(intro, introParams);

        LinearLayout playCard = actionCard(
                "🎮",
                "開啟 GBA 遊戲庫",
                "進入模擬器並自動切換橫向",
                "開始",
                true
        );
        playCard.setContentDescription("進入 Pokémon GBA 遊戲庫");
        playCard.setOnClickListener(view -> startActivity(new Intent(this, MainActivity.class)));
        LinearLayout.LayoutParams playParams = fullWidth();
        playParams.topMargin = dp(24);
        content.addView(playCard, playParams);

        LinearLayout statusHeader = new LinearLayout(this);
        statusHeader.setOrientation(LinearLayout.HORIZONTAL);
        statusHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusHeaderParams = fullWidth();
        statusHeaderParams.topMargin = dp(26);
        content.addView(statusHeader, statusHeaderParams);

        statusHeader.addView(
                text("目前狀態", 18f, true, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        );
        detectButton = compactButton("重新檢查");
        detectButton.setOnClickListener(view -> detectEverything());
        statusHeader.addView(detectButton, wrapContent());

        LinearLayout statusCard = surfaceCard();
        networkValue = addStatusRow(statusCard, "網路", "讀取中…");
        nativeValue = addStatusRow(
                statusCard,
                "App 版本",
                BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE
        );
        runtimeValue = addStatusRow(statusCard, "遊戲 Runtime", "檢查中…");
        nativeUpdateValue = addStatusRow(statusCard, "APK 更新", "檢查中…");
        content.addView(statusCard, cardParams());

        detectionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        detectionProgress.setIndeterminate(true);
        detectionProgress.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(6)
        );
        progressParams.topMargin = dp(10);
        content.addView(detectionProgress, progressParams);

        detectionSummary = text(
                "啟動時會檢查網路、Runtime 與 APK 更新通道。",
                13f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams summaryParams = fullWidth();
        summaryParams.topMargin = dp(7);
        content.addView(detectionSummary, summaryParams);

        addSectionTitle(content, "管理");

        LinearLayout permissionCard = actionCard(
                "🔐",
                "權限與裝置",
                "通知、APK 安裝來源與電池設定",
                "管理",
                false
        );
        permissionCard.setContentDescription("開啟權限控制中心");
        permissionCard.setOnClickListener(view -> startActivity(new Intent(this, PermissionCenterActivity.class)));
        content.addView(permissionCard, cardParams());

        LinearLayout updateCard = actionCard(
                "↻",
                "版本與更新",
                "檢查原生 APK、簽章與發布通道",
                "檢查",
                false
        );
        updateCard.setContentDescription("開啟原生 APK 更新中心");
        updateCard.setOnClickListener(view -> startActivity(new Intent(this, UpdateHubActivity.class)));
        content.addView(updateCard, cardParams());

        addSectionTitle(content, "進階");
        detailsButton = secondaryButton("顯示系統詳細資訊");
        detailsButton.setOnClickListener(view -> toggleTechnicalDetails());
        content.addView(detailsButton, cardParams());

        technicalDetails = surfaceCard();
        technicalDetails.setVisibility(View.GONE);
        addPlainRow(technicalDetails, "Runtime 來源", "GitHub Pages Live Channel");
        addPlainRow(technicalDetails, "原生套件", BuildConfig.APPLICATION_ID);
        addPlainRow(technicalDetails, "發布通道", BuildConfig.RELEASE_CHANNEL);
        addPlainRow(technicalDetails, "原生 APK", "永久簽章設定前保持停用");
        TextView technicalNote = text(
                "Runtime 更新會先完整下載並驗證資源，再切換快取；失敗時保留上一個可用版本。",
                12f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams noteParams = fullWidth();
        noteParams.topMargin = dp(10);
        technicalDetails.addView(technicalNote, noteParams);
        content.addView(technicalDetails, cardParams());

        TextView footer = text(
                "v0.9.2 Bridge 1 · Runtime 可熱更新 · APK 通道待永久簽章",
                12f,
                false,
                COLOR_WARNING
        );
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = fullWidth();
        footerParams.topMargin = dp(24);
        content.addView(footer, footerParams);

        setContentView(scroll);
    }

    private void toggleTechnicalDetails() {
        boolean show = technicalDetails.getVisibility() != View.VISIBLE;
        technicalDetails.setVisibility(show ? View.VISIBLE : View.GONE);
        detailsButton.setText(show ? "隱藏系統詳細資訊" : "顯示系統詳細資訊");
    }

    private void detectEverything() {
        detectButton.setEnabled(false);
        detectionProgress.setVisibility(View.VISIBLE);
        detectionProgress.setIndeterminate(true);
        runtimeValue.setText("檢查中…");
        nativeUpdateValue.setText("檢查中…");
        detectionSummary.setText("正在偵測；此動作不會下載或安裝 APK。");
        refreshNetwork();

        EXECUTOR.execute(() -> {
            String runtime = checkRuntime();
            String nativeUpdate = checkNativeUpdate();
            runOnUiThread(() -> {
                runtimeValue.setText(runtime);
                nativeUpdateValue.setText(nativeUpdate);
                detectionProgress.setVisibility(View.GONE);
                detectButton.setEnabled(true);
                detectionSummary.setText(
                        "偵測完成。Runtime 可由 GitHub 更新；APK 更新仍需簽章驗證與 Android 安裝確認。"
                );
            });
        });
    }

    private void refreshNetwork() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network active = manager == null ? null : manager.getActiveNetwork();
        NetworkCapabilities capabilities = manager == null || active == null
                ? null
                : manager.getNetworkCapabilities(active);
        boolean connected = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean validated = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        String transport = "離線";
        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "Wi-Fi";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "行動網路";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "乙太網路";
            else transport = "其他網路";
        }
        networkValue.setText(connected
                ? transport + (validated ? " · 正常" : " · 待驗證")
                : "離線");
    }

    private String checkRuntime() {
        try {
            JSONObject manifest = fetchJson(LIVE_RUNTIME_MANIFEST_URL);
            if (!"amin-runtime-manifest".equals(manifest.optString("format"))) {
                return "清單格式錯誤";
            }
            return manifest.optString("runtimeVersion", "未知版本");
        } catch (Exception error) {
            return "偵測失敗 · " + safeMessage(error);
        }
    }

    private String checkNativeUpdate() {
        try {
            JSONObject manifest = fetchJson(BuildConfig.NATIVE_UPDATE_MANIFEST_URL);
            if (!"amin-native-release-manifest".equals(manifest.optString("format"))) {
                return "清單格式錯誤";
            }
            if (!manifest.optBoolean("enabled", false)) {
                return "原生通道待簽章";
            }
            long latestCode = manifest.optLong("latestVersionCode", 0L);
            if (latestCode <= BuildConfig.VERSION_CODE) return "目前已是最新";
            return "可更新至 " + manifest.optString("latestVersionName", "未知版本");
        } catch (Exception error) {
            return "偵測失敗 · " + safeMessage(error);
        }
    }

    private JSONObject fetchJson(String urlText) throws Exception {
        URL url = new URL(urlText);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new SecurityException("只允許 HTTPS 清單");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.connect();
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 1024 * 1024) throw new IllegalStateException("清單過大");
                    output.write(buffer, 0, read);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private LinearLayout actionCard(
            String icon,
            String title,
            String description,
            String action,
            boolean primary
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(14), dp(16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setElevation(dp(primary ? 4 : 2));
        card.setBackground(roundedBackground(
                primary ? COLOR_ACCENT : COLOR_SURFACE,
                20,
                primary ? COLOR_ACCENT : COLOR_BORDER,
                1
        ));

        TextView iconView = text(icon, 25f, false, primary ? Color.WHITE : COLOR_ACCENT);
        iconView.setGravity(Gravity.CENTER);
        card.addView(iconView, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        copyParams.leftMargin = dp(12);
        card.addView(copy, copyParams);

        copy.addView(text(title, primary ? 19f : 17f, true, primary ? Color.WHITE : COLOR_TEXT), fullWidth());
        TextView descriptionView = text(
                description,
                13f,
                false,
                primary ? 0xffdff4e8 : COLOR_MUTED
        );
        LinearLayout.LayoutParams descriptionParams = fullWidth();
        descriptionParams.topMargin = dp(4);
        copy.addView(descriptionView, descriptionParams);

        TextView actionView = text(action + "  ›", 13f, true, primary ? Color.WHITE : COLOR_ACCENT);
        actionView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.addView(actionView, wrapContent());
        return card;
    }

    private LinearLayout surfaceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        card.setBackground(roundedBackground(COLOR_SURFACE, 18, COLOR_BORDER, 1));
        card.setElevation(dp(1));
        return card;
    }

    private TextView addStatusRow(LinearLayout parent, String label, String initialValue) {
        return addPlainRow(parent, label, initialValue);
    }

    private TextView addPlainRow(LinearLayout parent, String label, String initialValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView labelView = text(label, 13f, true, COLOR_MUTED);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.42f));

        TextView valueView = text(initialValue, 13f, false, COLOR_TEXT);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.58f));

        parent.addView(row, fullWidth());
        return valueView;
    }

    private void addSectionTitle(LinearLayout parent, String value) {
        TextView heading = text(value, 18f, true, COLOR_TEXT);
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(26);
        parent.addView(heading, params);
    }

    private TextView chip(String value) {
        TextView chip = text(value, 11f, true, COLOR_ACCENT_DARK);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(roundedBackground(COLOR_SURFACE_SOFT, 20, COLOR_SURFACE_SOFT, 0));
        return chip;
    }

    private Button compactButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12f);
        button.setTextColor(COLOR_ACCENT_DARK);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), dp(7), dp(12), dp(7));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_SURFACE_SOFT));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14f);
        button.setTextColor(COLOR_ACCENT_DARK);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_SURFACE));
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

    private GradientDrawable roundedBackground(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(10);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
