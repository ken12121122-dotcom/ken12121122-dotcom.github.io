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
    private static final int BG = 0xff0f1713;
    private static final int SURFACE = 0xff17231c;
    private static final int TEXT = 0xfff4faf6;
    private static final int MUTED = 0xffa7b8ae;
    private static final int ACCENT = 0xff49d488;
    private static final int MAX_MANIFEST_BYTES = 512 * 1024;
    private static final String KEY_ACKNOWLEDGED_CODE = "acknowledged_version_code";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProgressBar progressBar;
    private TextView statusView;
    private TextView detailView;
    private TextView receiptNotesView;
    private boolean leaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (shouldShowReceipt(getIntent())) {
            buildReceiptUi();
            loadReceiptNotes();
        } else {
            buildLaunchUi();
            beginVersionCheck();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (shouldShowReceipt(intent)) {
            leaving = false;
            buildReceiptUi();
            loadReceiptNotes();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean shouldShowReceipt(Intent intent) {
        int acknowledged = getSharedPreferences(PackageInstallResultReceiver.RECEIPT_PREFS, MODE_PRIVATE)
                .getInt(KEY_ACKNOWLEDGED_CODE, 0);
        if (acknowledged >= BuildConfig.VERSION_CODE) return false;
        boolean explicit = intent != null && intent.getBooleanExtra(PackageInstallResultReceiver.EXTRA_SHOW_UPDATE_RECEIPT, false);
        boolean confirmed = getSharedPreferences(PackageInstallResultReceiver.RECEIPT_PREFS, MODE_PRIVATE)
                .getBoolean(PackageInstallResultReceiver.KEY_INSTALL_SUCCESS, false);
        return explicit || confirmed || packageWasUpdated();
    }

    private boolean packageWasUpdated() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.lastUpdateTime > info.firstInstallTime + 1000L;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void buildLaunchUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(32), dp(28), dp(32));
        root.setBackgroundColor(BG);
        TextView mark = text("AMIN", 13, true, ACCENT);
        mark.setGravity(Gravity.CENTER);
        root.addView(mark, fullWidth());
        TextView title = text("Pocket GBA", 32, true, TEXT);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = dp(8);
        root.addView(title, titleParams);
        statusView = text("正在檢查版本…", 19, true, TEXT);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(42);
        root.addView(statusView, statusParams);
        detailView = text("確認 APK 與本地遊戲引擎", 14, false, MUTED);
        detailView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = fullWidth();
        detailParams.topMargin = dp(8);
        root.addView(detailView, detailParams);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(12);
        progressBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff26332c));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12));
        progressParams.topMargin = dp(26);
        root.addView(progressBar, progressParams);
        TextView version = text(BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE, 12, false, MUTED);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = fullWidth();
        versionParams.topMargin = dp(18);
        root.addView(version, versionParams);
        setContentView(root);
    }

    private void buildReceiptUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(34), dp(28), dp(34));
        scroll.addView(root);
        TextView mark = text("✓", 48, true, ACCENT);
        mark.setGravity(Gravity.CENTER);
        root.addView(mark, fullWidth());
        TextView title = text("更新安裝完成", 30, true, TEXT);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = dp(12);
        root.addView(title, titleParams);
        TextView version = text(BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE, 16, true, TEXT);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = fullWidth();
        versionParams.topMargin = dp(24);
        root.addView(version, versionParams);
        ProgressBar complete = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        complete.setMax(100);
        complete.setProgress(100);
        complete.setProgressTintList(ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12));
        barParams.topMargin = dp(20);
        root.addView(complete, barParams);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundColor(SURFACE);
        LinearLayout.LayoutParams cardParams = fullWidth();
        cardParams.topMargin = dp(24);
        root.addView(card, cardParams);
        card.addView(text("這次完成了什麼", 16, true, TEXT), fullWidth());
        receiptNotesView = text("• 新版 APK 已安裝\n• 正在讀取更新內容…", 14, false, MUTED);
        LinearLayout.LayoutParams notesParams = fullWidth();
        notesParams.topMargin = dp(10);
        card.addView(receiptNotesView, notesParams);
        Button enter = new Button(this);
        enter.setText("進入遊戲控制台");
        enter.setAllCaps(false);
        enter.setTextSize(16);
        enter.setTypeface(Typeface.DEFAULT_BOLD);
        enter.setTextColor(BG);
        enter.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        enter.setOnClickListener(view -> acknowledgeAndOpen());
        LinearLayout.LayoutParams enterParams = fullWidth();
        enterParams.topMargin = dp(26);
        root.addView(enter, enterParams);
        setContentView(scroll);
    }

    private void beginVersionCheck() {
        if ("release".equals(BuildConfig.RELEASE_CHANNEL)) {
            handler.postDelayed(this::openControlCenter, 500);
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                JSONObject manifest = fetchManifest();
                validateManifest(manifest);
                long latest = manifest.optLong("latestVersionCode", 0);
                boolean enabled = manifest.optBoolean("enabled", false);
                runOnUiThread(() -> {
                    progressBar.setProgress(70);
                    if (enabled && latest > BuildConfig.VERSION_CODE) {
                        statusView.setText("發現新版");
                        detailView.setText("正在進入安全更新流程");
                        handler.postDelayed(this::openUpdater, 250);
                    } else {
                        statusView.setText("版本檢查完成");
                        detailView.setText("正在進入遊戲控制台");
                        progressBar.setProgress(100);
                        handler.postDelayed(this::openControlCenter, 300);
                    }
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    statusView.setText("離線啟動");
                    detailView.setText("暫時無法檢查更新，使用已安裝版本");
                    progressBar.setProgress(100);
                    handler.postDelayed(this::openControlCenter, 500);
                });
            }
        });
    }

    private void loadReceiptNotes() {
        EXECUTOR.execute(() -> {
            String notes = "• 新版 APK 已完成安裝\n• 永久簽章與本機資料均已保留";
            try {
                JSONObject manifest = fetchManifest();
                if (manifest.optLong("latestVersionCode", 0) == BuildConfig.VERSION_CODE) {
                    JSONArray array = manifest.optJSONArray("releaseNotes");
                    if (array != null && array.length() > 0) {
                        StringBuilder builder = new StringBuilder();
                        for (int i = 0; i < array.length(); i++) {
                            String value = array.optString(i, "").trim();
                            if (value.isEmpty()) continue;
                            if (builder.length() > 0) builder.append('\n');
                            builder.append("• ").append(value);
                        }
                        if (builder.length() > 0) notes = builder.toString();
                    }
                }
            } catch (Exception ignored) {}
            String finalNotes = notes;
            runOnUiThread(() -> {
                if (receiptNotesView != null) receiptNotesView.setText(finalNotes);
            });
        });
    }

    private JSONObject fetchManifest() throws Exception {
        URL url = new URL(BuildConfig.NATIVE_UPDATE_MANIFEST_URL);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !"ken12121122-dotcom.github.io".equalsIgnoreCase(url.getHost())) {
            throw new SecurityException("Untrusted manifest URL");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(7000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP " + connection.getResponseCode());
            try (InputStream input = new BufferedInputStream(connection.getInputStream()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_MANIFEST_BYTES) throw new SecurityException("Manifest too large");
                    output.write(buffer, 0, read);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private void validateManifest(JSONObject manifest) {
        if (!"amin-native-release-manifest".equals(manifest.optString("format"))) throw new IllegalStateException("Invalid manifest format");
        if (!getPackageName().equals(manifest.optString("packageId"))) throw new SecurityException("Package mismatch");
    }

    private void acknowledgeAndOpen() {
        getSharedPreferences(PackageInstallResultReceiver.RECEIPT_PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_ACKNOWLEDGED_CODE, BuildConfig.VERSION_CODE)
                .putBoolean(PackageInstallResultReceiver.KEY_INSTALL_SUCCESS, false)
                .apply();
        openControlCenter();
    }

    private void openUpdater() {
        if (leaving) return;
        leaving = true;
        startActivity(new Intent(this, NativeUpdateActivity.class));
        finish();
    }

    private void openControlCenter() {
        if (leaving) return;
        leaving = true;
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
        view.setLineSpacing(0, 1.25f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
