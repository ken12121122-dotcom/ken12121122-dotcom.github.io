package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

public final class LaunchGateActivity extends Activity {
    private static final int COLOR_BG = 0xff0f1713;
    private static final int COLOR_TEXT = 0xfff4faf6;
    private static final int COLOR_MUTED = 0xffa7b8ae;
    private static final int COLOR_ACCENT = 0xff49d488;
    private static final int MAX_MANIFEST_BYTES = 512 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProgressBar progressBar;
    private TextView statusView;
    private TextView detailView;
    private int progress = 5;
    private boolean leaving;

    private final Runnable pulse = new Runnable() {
        @Override
        public void run() {
            if (leaving || progress >= 55) return;
            progress = Math.min(55, progress + 2);
            progressBar.setProgress(progress);
            handler.postDelayed(this, 90L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        buildUi();
        beginLaunchCheck();
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
        progressBar.setProgress(100);
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
