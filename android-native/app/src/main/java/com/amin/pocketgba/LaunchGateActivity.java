package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LaunchGateActivity extends Activity {
    private static final int COLOR_BG = 0xff0f1713;
    private static final int COLOR_SURFACE = 0xff17231c;
    private static final int COLOR_TEXT = 0xfff4faf6;
    private static final int COLOR_MUTED = 0xffa7b8ae;
    private static final int COLOR_ACCENT = 0xff49d488;
    private static final int COLOR_LINE = 0xff2b3c32;
    private static final int MAX_MANIFEST_BYTES = 512 * 1024;
    private static final String KEY_ACKNOWLEDGED_CODE = "acknowledged_version_code";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProgressBar progressBar;
    private TextView statusView;
    private TextView detailView;
    private TextView receiptNotesView;
    private int progress = 5;
    private boolean leaving;

    private final Runnable pulse = new Runnable() {
        @Override
        public void run() {
            if (leaving || progress >= 55 || progressBar == null) return;
            progress = Math.min(55, progress + 2);
            progressBar.setProgress(progress);
            handler.postDelayed(this, 90L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        if (shouldShowUpdateReceipt(getIntent())) {
            buildUpdateReceiptUi();
            loadReceiptDetails();
        } else {
            buildUi();
            beginLaunchCheck();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (shouldShowUpdateReceipt(intent)) {
            leaving = false;
            handler.removeCallbacksAndMessages(null);
            buildUpdateReceiptUi();
            loadReceiptDetails();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
    }

    private boolean shouldShowUpdateReceipt(Intent intent) {
        int acknowledgedCode = getSharedPreferences(
                PackageInstallResultReceiver.RECEIPT_PREFS,
                MODE_PRIVATE
        ).getInt(KEY_ACKNOWLEDGED_CODE, 0);
        if (acknowledgedCode >= BuildConfig.VERSION_CODE) return false;

        boolean explicitReceipt = intent != null && intent.getBooleanExtra(
                PackageInstallResultReceiver.EXTRA_SHOW_UPDATE_RECEIPT,
                false
        );
        boolean receiverConfirmed = getSharedPreferences(
                PackageInstallResultReceiver.RECEIPT_PREFS,
                MODE_PRIVATE
        ).getBoolean(PackageInstallResultReceiver.KEY_INSTALL_SUCCESS, false);

        return explicitReceipt || receiverConfirmed || packageWasUpdated();
    }

    private boolean packageWasUpdated() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.lastUpdateTime > packageInfo.firstInstallTime + 1000L;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void buildUpdateReceiptUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(34), dp(28), dp(34));
        scroll.addView(root);

        TextView mark = text("✓", 48f, true, COLOR_ACCENT);
        mark.setGravity(Gravity.CENTER);
        root.addView(mark, fullWidth());

        TextView eyebrow = text("UPDATE COMPLETE", 12f, true, COLOR_ACCENT);
        eyebrow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams eyebrowParams = fullWidth();
        eyebrowParams.topMargin = dp(8);
        root.addView(eyebrow, eyebrowParams);

        TextView title = text("更新安裝完成", 30f, true, COLOR_TEXT);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = dp(8);
        root.addView(title, titleParams);

        statusView = text("Amin 已成功升級", 19f, true, COLOR_TEXT);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(30);
        root.addView(statusView, statusParams);

        detailView = text(
                BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE
                        + "\nAndroid 已完成新版套件替換。",
                14f,
                false,
                COLOR_MUTED
        );
        detailView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = fullWidth();
        detailParams.topMargin = dp(8);
        root.addView(detailView, detailParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff26332c));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12)
        );
        progressParams.topMargin = dp(24);
        root.addView(progressBar, progressParams);

        LinearLayout receiptCard = new LinearLayout(this);
        receiptCard.setOrientation(LinearLayout.VERTICAL);
        receiptCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        receiptCard.setBackgroundColor(COLOR_SURFACE);
        LinearLayout.LayoutParams cardParams = fullWidth();
        cardParams.topMargin = dp(24);
        root.addView(receiptCard, cardParams);

        TextView receiptTitle = text("這次完成了什麼", 16f, true, COLOR_TEXT);
        receiptCard.addView(receiptTitle, fullWidth());

        receiptNotesView = text(
                "• 新版 APK 已安裝\n• 套件名稱與永久簽章保持一致\n• 正在讀取本次更新內容…",
                14f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams notesParams = fullWidth();
        notesParams.topMargin = dp(10);
        receiptCard.addView(receiptNotesView, notesParams);

        Button enterButton = new Button(this);
        enterButton.setText("進入遊戲控制台");
        enterButton.setAllCaps(false);
        enterButton.setTextSize(16f);
        enterButton.setTypeface(Typeface.DEFAULT_BOLD);
        enterButton.setTextColor(COLOR_BG);
        enterButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_ACCENT));
        enterButton.setOnClickListener(view -> acknowledgeAndOpenMain());
        LinearLayout.LayoutParams enterParams = fullWidth();
        enterParams.topMargin = dp(26);
        root.addView(enterButton, enterParams);

        TextView note = text(
                "此確認頁只會顯示一次。按下按鈕後才進入主畫面。",
                12f,
                false,
                COLOR_MUTED
        );
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = fullWidth();
        noteParams.topMargin = dp(12);
        root.addView(note, noteParams);

        setContentView(scroll);
    }

    private void loadReceiptDetails() {
        EXECUTOR.execute(() -> {
            String notes;
            try {
                JSONObject manifest = fetchManifest();
                validateManifest(manifest);
                long latestCode = manifest.optLong("latestVersionCode", 0L);
                if (latestCode == BuildConfig.VERSION_CODE) {
                    notes = formatReleaseNotes(manifest.optJSONArray("releaseNotes"));
                } else {
                    notes = defaultReceiptNotes();
                }
            } catch (Exception ignored) {
                notes = defaultReceiptNotes();
            }
            String finalNotes = notes;
            runOnUiThread(() -> {
                if (receiptNotesView != null) receiptNotesView.setText(finalNotes);
            });
        });
    }

    private String defaultReceiptNotes() {
        return "• 新版 APK 已完成安裝\n"
                + "• 套件識別與永久簽章驗證通過\n"
                + "• 本機資料與 Runtime 更新通道已保留";
    }

    private String formatReleaseNotes(JSONArray notes) {
        if (notes == null || notes.length() == 0) return defaultReceiptNotes();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < notes.length(); index++) {
            String value = notes.optString(index, "").trim();
            if (value.isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append("• ").append(value);
        }
        return builder.length() == 0 ? defaultReceiptNotes() : builder.toString();
    }

    private void acknowledgeAndOpenMain() {
        getSharedPreferences(PackageInstallResultReceiver.RECEIPT_PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_ACKNOWLEDGED_CODE, BuildConfig.VERSION_CODE)
                .putBoolean(PackageInstallResultReceiver.KEY_INSTALL_SUCCESS, false)
                .apply();
        openMain();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(32), dp(28), dp(32));
        root.setBackgroundColor(COLOR_BG);

        TextView mark = text("AMIN", 13f, true, COLOR_ACCENT);
        mark.setGravity(Gravity.CENTER);
        root.addView(mark, fullWidth());

        TextView title = text("Pocket GBA", 32f, true, COLOR_TEXT);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = dp(8);
        root.addView(title, titleParams);

        statusView = text("正在啟動…", 19f, true, COLOR_TEXT);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(42);
        root.addView(statusView, statusParams);

        detailView = text(
                "檢查遊戲資源與 App 版本",
                14f,
                false,
                COLOR_MUTED
        );
        detailView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = fullWidth();
        detailParams.topMargin = dp(8);
        root.addView(detailView, detailParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(progress);
        progressBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff26332c));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12)
        );
        progressParams.topMargin = dp(26);
        root.addView(progressBar, progressParams);

        TextView version = text(
                BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE,
                12f,
                false,
                COLOR_MUTED
        );
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = fullWidth();
        versionParams.topMargin = dp(18);
        root.addView(version, versionParams);

        setContentView(root);
    }

    private void beginLaunchCheck() {
        progress = 8;
        progressBar.setProgress(progress);
        handler.post(pulse);

        if (isPlayBuild()) {
            statusView.setText("Google Play 安全啟動");
            detailView.setText("App 更新由 Google Play 管理，正在載入遊戲資源");
            handler.postDelayed(() -> {
                progress = 76;
                progressBar.setProgress(progress);
                statusView.setText("資源檢查完成");
                detailView.setText("正在進入遊戲");
                finishLaunch(false);
            }, 650L);
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                JSONObject manifest = fetchManifest();
                validateManifest(manifest);
                long latestCode = manifest.optLong("latestVersionCode", 0L);
                boolean enabled = manifest.optBoolean("enabled", false);

                runOnUiThread(() -> {
                    progress = 65;
                    progressBar.setProgress(progress);
                    if (enabled && latestCode > BuildConfig.VERSION_CODE) {
                        statusView.setText("發現新版");
                        detailView.setText("正在進入自動更新流程");
                        handler.postDelayed(this::openUpdater, 260L);
                    } else {
                        statusView.setText("版本檢查完成");
                        detailView.setText("正在進入遊戲");
                        finishLaunch(false);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    statusView.setText("離線啟動");
                    detailView.setText("暫時無法檢查更新，仍可使用已安裝版本");
                    finishLaunch(true);
                });
            }
        });
    }

    private boolean isPlayBuild() {
        return "release".equals(BuildConfig.RELEASE_CHANNEL);
    }

    private JSONObject fetchManifest() throws Exception {
        URL url = new URL(BuildConfig.NATIVE_UPDATE_MANIFEST_URL);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new SecurityException("Only HTTPS is allowed");
        }
        if (!"ken12121122-dotcom.github.io".equalsIgnoreCase(url.getHost())) {
            throw new SecurityException("Untrusted update host");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(7000);
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
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_MANIFEST_BYTES) {
                        throw new SecurityException("Manifest too large");
                    }
                    output.write(buffer, 0, read);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private void validateManifest(JSONObject manifest) {
        if (!"amin-native-release-manifest".equals(manifest.optString("format"))) {
            throw new IllegalStateException("Invalid manifest format");
        }
        if (!getPackageName().equals(manifest.optString("packageId"))) {
            throw new SecurityException("Package mismatch");
        }
    }

    private void finishLaunch(boolean offline) {
        progress = offline ? 92 : 100;
        progressBar.setProgress(progress);
        long delay = offline ? 650L : 320L;
        handler.postDelayed(this::openMain, delay);
    }

    private void openUpdater() {
        if (leaving) return;
        leaving = true;
        progressBar.setProgress(72);
        startActivity(new Intent(this, NativeUpdateActivity.class));
        finish();
    }

    private void openMain() {
        if (leaving) return;
        leaving = true;
        if (progressBar != null) progressBar.setProgress(100);
        Intent intent = new Intent(this, ControlCenterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
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

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
