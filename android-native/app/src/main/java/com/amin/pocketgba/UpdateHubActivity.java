package com.amin.pocketgba;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

public final class UpdateHubActivity extends Activity {
    private static final int COLOR_BG = 0xfff4f7f5;
    private static final int COLOR_SURFACE = 0xffffffff;
    private static final int COLOR_SURFACE_SOFT = 0xffeaf3ee;
    private static final int COLOR_TEXT = 0xff16231b;
    private static final int COLOR_MUTED = 0xff68766e;
    private static final int COLOR_ACCENT = 0xff19794b;
    private static final int COLOR_BORDER = 0xffd9e4de;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private TextView statusView;
    private TextView detailView;
    private ProgressBar progressBar;
    private Button actionButton;
    private Button retryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        buildUi();
        if (isPlayBuild()) {
            showPlayManagedUpdate();
        } else {
            checkForUpdate();
        }
    }

    private boolean isPlayBuild() {
        return "release".equals(BuildConfig.RELEASE_CHANNEL);
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
        content.setPadding(dp(20), dp(18), dp(20), dp(36));
        content.setBackgroundColor(COLOR_BG);
        scroll.addView(content);

        Button back = textButton("← 返回控制台");
        back.setOnClickListener(view -> finish());
        content.addView(back, wrapContent());

        TextView eyebrow = text("RELEASE & UPDATE", 12f, true, COLOR_ACCENT);
        LinearLayout.LayoutParams eyebrowParams = fullWidth();
        eyebrowParams.topMargin = dp(12);
        content.addView(eyebrow, eyebrowParams);

        TextView title = text("版本與更新", 28f, true, COLOR_TEXT);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = dp(4);
        content.addView(title, titleParams);

        TextView intro = text(
                isPlayBuild()
                        ? "Google Play 版本由 Play Store 驗證、下載與安裝；App 不會自行安裝外部 APK。"
                        : "GitHub Bridge 版本會在下載後執行完整驗證，再交給 Android 安裝。",
                14f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams introParams = fullWidth();
        introParams.topMargin = dp(8);
        content.addView(intro, introParams);

        LinearLayout currentCard = surfaceCard(COLOR_SURFACE_SOFT);
        currentCard.addView(text("目前安裝版本", 14f, true, COLOR_MUTED), fullWidth());
        TextView currentVersion = text(
                BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE,
                21f,
                true,
                COLOR_TEXT
        );
        LinearLayout.LayoutParams currentParams = fullWidth();
        currentParams.topMargin = dp(5);
        currentCard.addView(currentVersion, currentParams);
        content.addView(currentCard, sectionCardParams());

        LinearLayout resultCard = surfaceCard(COLOR_SURFACE);
        statusView = text("正在檢查…", 19f, true, COLOR_TEXT);
        resultCard.addView(statusView, fullWidth());
        detailView = text("正在讀取更新狀態。", 13f, false, COLOR_MUTED);
        LinearLayout.LayoutParams detailParams = fullWidth();
        detailParams.topMargin = dp(7);
        resultCard.addView(detailView, detailParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(6)
        );
        progressParams.topMargin = dp(14);
        resultCard.addView(progressBar, progressParams);

        actionButton = primaryButton(isPlayBuild() ? "開啟 Google Play" : "進入安全更新流程");
        actionButton.setVisibility(View.GONE);
        actionButton.setOnClickListener(view -> {
            if (isPlayBuild()) {
                openPlayStore();
            } else {
                startActivity(new Intent(this, NativeUpdateActivity.class));
            }
        });
        LinearLayout.LayoutParams actionParams = fullWidth();
        actionParams.topMargin = dp(14);
        resultCard.addView(actionButton, actionParams);

        retryButton = secondaryButton("重新檢查");
        retryButton.setOnClickListener(view -> checkForUpdate());
        LinearLayout.LayoutParams retryParams = fullWidth();
        retryParams.topMargin = dp(10);
        resultCard.addView(retryButton, retryParams);

        content.addView(resultCard, cardParams());

        TextView securityTitle = text("更新安全機制", 18f, true, COLOR_TEXT);
        LinearLayout.LayoutParams securityTitleParams = fullWidth();
        securityTitleParams.topMargin = dp(26);
        content.addView(securityTitle, securityTitleParams);

        LinearLayout securityCard = surfaceCard(COLOR_SURFACE);
        if (isPlayBuild()) {
            addInfo(securityCard, "Google Play 分發", "版本簽章、下載與安裝由 Google Play 管理。");
            addInfo(securityCard, "相同永久簽章", "保留既有 App 資料與原地更新能力。");
            addInfo(securityCard, "無外部 APK 安裝", "Play 版本移除未知來源安裝權限與自我更新入口。");
        } else {
            addInfo(securityCard, "HTTPS 來源", "只接受核准網域的加密連線。");
            addInfo(securityCard, "四重驗證", "比對 SHA-256、套件名稱、版本碼與簽章憑證。");
            addInfo(securityCard, "Android 安裝服務", "驗證通過後交由系統 PackageInstaller 處理。");
        }
        content.addView(securityCard, cardParams());

        setContentView(scroll);
    }

    private void showPlayManagedUpdate() {
        progressBar.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        statusView.setText("更新由 Google Play 管理");
        detailView.setText("Play Store 會驗證正式版本並依裝置的自動更新設定安裝。也可以手動開啟商店查看更新。");
        actionButton.setVisibility(View.VISIBLE);
    }

    private void checkForUpdate() {
        if (isPlayBuild()) {
            showPlayManagedUpdate();
            return;
        }

        retryButton.setEnabled(false);
        actionButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        statusView.setText("正在檢查更新…");
        detailView.setText("正在讀取正式更新清單。");

        EXECUTOR.execute(() -> {
            try {
                JSONObject manifest = fetchJson(BuildConfig.NATIVE_UPDATE_MANIFEST_URL);
                if (!"amin-native-release-manifest".equals(manifest.optString("format"))) {
                    throw new IllegalStateException("更新清單格式不正確");
                }
                if (!getPackageName().equals(manifest.optString("packageId"))) {
                    throw new SecurityException("更新套件識別碼不受信任");
                }

                boolean enabled = manifest.optBoolean("enabled", false);
                long latestCode = manifest.optLong("latestVersionCode", 0L);
                String latestName = manifest.optString("latestVersionName", "未知版本");

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    retryButton.setEnabled(true);
                    if (!enabled) {
                        statusView.setText("更新通道目前關閉");
                        detailView.setText("目前沒有可下載的原生 APK 版本。");
                        return;
                    }
                    if (latestCode <= BuildConfig.VERSION_CODE) {
                        statusView.setText("目前已是最新版本");
                        detailView.setText("線上版本：" + latestName + " · code " + latestCode);
                        return;
                    }
                    statusView.setText("發現可用更新 " + latestName);
                    detailView.setText("版本碼：" + latestCode + "。下一步會下載並執行完整驗證。");
                    actionButton.setVisibility(View.VISIBLE);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    retryButton.setEnabled(true);
                    statusView.setText("更新檢查失敗");
                    detailView.setText(safeMessage(error));
                });
            }
        });
    }

    private void openPlayStore() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + getPackageName())
            ));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())
            ));
        }
    }

    private JSONObject fetchJson(String urlText) throws Exception {
        URL url = new URL(urlText);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new SecurityException("只允許 HTTPS 更新來源");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
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
                    if (total > 512 * 1024) throw new SecurityException("更新清單過大");
                    output.write(buffer, 0, read);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private void addInfo(LinearLayout parent, String title, String description) {
        TextView heading = text(title, 14f, true, COLOR_TEXT);
        LinearLayout.LayoutParams headingParams = fullWidth();
        headingParams.topMargin = parent.getChildCount() == 0 ? 0 : dp(14);
        parent.addView(heading, headingParams);
        TextView body = text(description, 12f, false, COLOR_MUTED);
        LinearLayout.LayoutParams bodyParams = fullWidth();
        bodyParams.topMargin = dp(3);
        parent.addView(body, bodyParams);
    }

    private LinearLayout surfaceCard(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBackground(color, 18, COLOR_BORDER, 1));
        card.setElevation(dp(1));
        return card;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(50));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_ACCENT));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_ACCENT);
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_SURFACE_SOFT));
        return button;
    }

    private Button textButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_ACCENT);
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, dp(5), dp(10), dp(5));
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

    private LinearLayout.LayoutParams sectionCardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(20);
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
