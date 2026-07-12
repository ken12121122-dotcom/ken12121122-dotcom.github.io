package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
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
    private static final String PREVIEW_RUNTIME_MANIFEST_URL =
            "https://raw.githubusercontent.com/ken12121122-dotcom/ken12121122-dotcom.github.io/agent/amin-pocket-gba-rc092/amin-vault/runtime-manifest.json";
    private static final String STABLE_RUNTIME_MANIFEST_URL =
            "https://ken12121122-dotcom.github.io/amin-vault/runtime-manifest.json";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private TextView networkValue;
    private TextView nativeValue;
    private TextView previewRuntimeValue;
    private TextView stableRuntimeValue;
    private TextView nativeUpdateValue;
    private TextView detectionSummary;
    private ProgressBar detectionProgress;
    private Button detectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        detectEverything();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNetwork();
    }

    private void buildUi() {
        int padding = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(0xff070908);

        TextView eyebrow = text("AMIN POCKET GBA · PREVIEW 2", 12f, true);
        eyebrow.setTextColor(0xff79f2b0);
        content.addView(eyebrow, fullWidth());

        TextView title = text("原生控制台", 30f, true);
        title.setTextColor(0xffffffff);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = dp(4);
        content.addView(title, titleParams);

        TextView intro = text(
                "這個畫面直接內建在 APK。即使遠端遊戲庫仍是舊版，你也能看見並操作新的原生功能。",
                14f,
                false
        );
        intro.setTextColor(0xffcbd4cf);
        LinearLayout.LayoutParams introParams = fullWidth();
        introParams.topMargin = dp(8);
        content.addView(intro, introParams);

        addSectionTitle(content, "啟動自動偵測");
        LinearLayout statusCard = card();
        networkValue = addStatusRow(statusCard, "網路", "讀取中…");
        nativeValue = addStatusRow(
                statusCard,
                "Native APK",
                BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE
        );
        previewRuntimeValue = addStatusRow(statusCard, "Preview Runtime", "檢查中…");
        stableRuntimeValue = addStatusRow(statusCard, "Stable Runtime", "檢查中…");
        nativeUpdateValue = addStatusRow(statusCard, "原生更新通道", "檢查中…");
        content.addView(statusCard, cardParams());

        detectionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        detectionProgress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressParams.topMargin = dp(12);
        content.addView(detectionProgress, progressParams);

        detectionSummary = text("App 啟動後會自動檢查兩個 Runtime 與原生 APK 更新通道。", 13f, false);
        detectionSummary.setTextColor(0xffaebbb4);
        LinearLayout.LayoutParams summaryParams = fullWidth();
        summaryParams.topMargin = dp(8);
        content.addView(detectionSummary, summaryParams);

        detectButton = button("重新自動偵測");
        detectButton.setOnClickListener(view -> detectEverything());
        content.addView(detectButton, spaced());

        addSectionTitle(content, "原生功能入口");

        Button libraryButton = primaryButton("進入 Pokémon GBA 遊戲庫");
        libraryButton.setOnClickListener(view -> startActivity(new Intent(this, MainActivity.class)));
        content.addView(libraryButton, spaced());

        Button permissionsButton = button("開啟權限控制中心");
        permissionsButton.setOnClickListener(view -> startActivity(new Intent(this, PermissionCenterActivity.class)));
        content.addView(permissionsButton, spaced());

        Button updateButton = button("開啟原生 APK 更新中心");
        updateButton.setOnClickListener(view -> startActivity(new Intent(this, NativeUpdateActivity.class)));
        content.addView(updateButton, spaced());

        addSectionTitle(content, "這一包實際新增了什麼");
        addInfoCard(content, "啟動首頁", "現在一開啟 App 就會先看到這個控制台，不再把新功能藏在遠端網頁裡。", "可見");
        addInfoCard(content, "自動偵測", "啟動時檢查網路、Preview Runtime、Stable Runtime 與原生更新清單。", "自動");
        addInfoCard(content, "權限中心", "通知、APK 安裝來源、App 設定、通知設定與電池最佳化入口。", "原生");
        addInfoCard(content, "更新中心", "檢查正式更新清單；正式簽章通道尚未啟用時會明確顯示停用。", "原生");
        addInfoCard(content, "遠端遊戲庫", "目前仍開啟正式 GitHub Pages 遊戲庫。PR 未合併前，備份 v2 與診斷網頁不會冒充已發布。", "誠實狀態");

        TextView warning = text(
                "注意：這是 Preview 2 測試包。正式 APK 自動更新仍需永久簽章與實體覆蓋升級驗收。",
                13f,
                true
        );
        warning.setTextColor(0xffffc56b);
        LinearLayout.LayoutParams warningParams = fullWidth();
        warningParams.topMargin = dp(22);
        warningParams.bottomMargin = dp(28);
        content.addView(warning, warningParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        setContentView(scrollView);
    }

    private void detectEverything() {
        detectButton.setEnabled(false);
        detectionProgress.setVisibility(View.VISIBLE);
        detectionProgress.setIndeterminate(true);
        previewRuntimeValue.setText("檢查中…");
        stableRuntimeValue.setText("檢查中…");
        nativeUpdateValue.setText("檢查中…");
        detectionSummary.setText("正在自動偵測，這不是安裝動作。");
        refreshNetwork();

        EXECUTOR.execute(() -> {
            String previewRuntime = checkRuntime(PREVIEW_RUNTIME_MANIFEST_URL, "Preview");
            String stableRuntime = checkRuntime(STABLE_RUNTIME_MANIFEST_URL, "Stable");
            String nativeUpdate = checkNativeUpdate();

            runOnUiThread(() -> {
                previewRuntimeValue.setText(previewRuntime);
                stableRuntimeValue.setText(stableRuntime);
                nativeUpdateValue.setText(nativeUpdate);
                detectionProgress.setVisibility(View.GONE);
                detectButton.setEnabled(true);
                detectionSummary.setText(
                        "偵測完成。Runtime 可自動檢查；APK 更新仍由 Android 要求使用者確認安裝。"
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
                ? transport + (validated ? " · 已驗證" : " · 尚未驗證")
                : "離線"
        );
    }

    private String checkRuntime(String url, String label) {
        try {
            JSONObject manifest = fetchJson(url);
            if (!"amin-runtime-manifest".equals(manifest.optString("format"))) {
                return label + " 清單格式不正確";
            }
            String version = manifest.optString("runtimeVersion", "未知版本");
            String minimum = manifest.optString("minimumNativeVersion", "未指定");
            return version + " · Native ≥ " + minimum;
        } catch (Exception error) {
            return "偵測失敗 · " + safeMessage(error);
        }
    }

    private String checkNativeUpdate() {
        try {
            JSONObject manifest = fetchJson(BuildConfig.NATIVE_UPDATE_MANIFEST_URL);
            if (!"amin-native-release-manifest".equals(manifest.optString("format"))) {
                return "清單格式不正確";
            }
            if (!manifest.optBoolean("enabled", false)) {
                return "正式通道停用 · 不會下載 APK";
            }
            return "可用 " + manifest.optString("latestVersionName", "未知版本")
                    + " · code " + manifest.optLong("latestVersionCode", 0L);
        } catch (Exception error) {
            return "偵測失敗 · " + safeMessage(error);
        }
    }

    private JSONObject fetchJson(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
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
                    if (total > 1024 * 1024) {
                        throw new IllegalStateException("清單過大");
                    }
                    output.write(buffer, 0, read);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private TextView addStatusRow(LinearLayout parent, String label, String initialValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView labelView = text(label, 13f, true);
        labelView.setTextColor(0xff91a49a);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.34f));

        TextView valueView = text(initialValue, 13f, false);
        valueView.setTextColor(0xffffffff);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.66f));

        parent.addView(row, fullWidth());
        return valueView;
    }

    private void addSectionTitle(LinearLayout parent, String value) {
        TextView heading = text(value, 17f, true);
        heading.setTextColor(0xffffffff);
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(24);
        parent.addView(heading, params);
    }

    private void addInfoCard(LinearLayout parent, String title, String description, String badge) {
        LinearLayout info = card();
        TextView heading = text(title + "  ·  " + badge, 15f, true);
        heading.setTextColor(0xff79f2b0);
        info.addView(heading, fullWidth());
        TextView body = text(description, 13f, false);
        body.setTextColor(0xffcbd4cf);
        LinearLayout.LayoutParams bodyParams = fullWidth();
        bodyParams.topMargin = dp(6);
        info.addView(body, bodyParams);
        parent.addView(info, cardParams());
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(12), dp(15), dp(12));
        card.setBackgroundColor(0xff111714);
        return card;
    }

    private Button primaryButton(String label) {
        Button button = button(label);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        return button;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(50));
        return button;
    }

    private TextView text(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setLineSpacing(0f, 1.28f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(12);
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
