package com.amin.pocketgba;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reuses the canonical native update manifest and only adds proactive UI.
 *
 * This class is not a second update channel: it never downloads, signs,
 * publishes, or installs an APK. When a newer version is detected it opens
 * the existing UpdateHubActivity, which continues to own the safe update flow.
 */
final class NativeUpdatePromptController {
    private static final String PREFS = "native_update_prompt";
    private static final String KEY_LAST_CODE = "last_prompted_code";
    private static final String KEY_LAST_AT = "last_prompted_at";
    private static final long REMIND_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean CHECK_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean CHECKED_THIS_PROCESS = new AtomicBoolean(false);

    private NativeUpdatePromptController() {}

    static void onActivityResumed(Activity activity) {
        if (!(activity instanceof ControlCenterActivity)) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (CHECKED_THIS_PROCESS.getAndSet(true)) return;
        if (!CHECK_IN_FLIGHT.compareAndSet(false, true)) return;

        EXECUTOR.execute(() -> {
            try {
                JSONObject manifest = fetchManifest();
                if (!"amin-native-release-manifest".equals(manifest.optString("format"))) return;
                if (!BuildConfig.APPLICATION_ID.equals(manifest.optString("packageId"))) return;
                if (!manifest.optBoolean("enabled", false)) return;

                long latestCode = manifest.optLong("latestVersionCode", 0L);
                if (latestCode <= BuildConfig.VERSION_CODE) return;

                String latestName = manifest.optString("latestVersionName", "未知版本");
                if (!shouldPrompt(activity, latestCode)) return;

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    rememberPrompt(activity, latestCode);
                    new AlertDialog.Builder(activity)
                            .setTitle("發現新版本")
                            .setMessage(
                                    "可更新至 " + latestName
                                            + "（code " + latestCode + "）。\n\n"
                                            + "目前版本：" + BuildConfig.VERSION_NAME
                                            + "（code " + BuildConfig.VERSION_CODE + "）。\n"
                                            + "要現在查看更新內容嗎？"
                            )
                            .setPositiveButton("查看更新", (dialog, which) ->
                                    activity.startActivity(new Intent(activity, UpdateHubActivity.class))
                            )
                            .setNegativeButton("稍後", null)
                            .show();
                });
            } catch (Exception ignored) {
                // The existing UpdateHub/ControlCenter status remains the visible error surface.
                // A failed proactive check must never block app startup.
            } finally {
                CHECK_IN_FLIGHT.set(false);
            }
        });
    }

    private static boolean shouldPrompt(Activity activity, long latestCode) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        long lastCode = prefs.getLong(KEY_LAST_CODE, 0L);
        long lastAt = prefs.getLong(KEY_LAST_AT, 0L);
        if (latestCode != lastCode) return true;
        return System.currentTimeMillis() - lastAt >= REMIND_INTERVAL_MS;
    }

    private static void rememberPrompt(Activity activity, long latestCode) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CODE, latestCode)
                .putLong(KEY_LAST_AT, System.currentTimeMillis())
                .apply();
    }

    private static JSONObject fetchManifest() throws Exception {
        URL url = new URL(BuildConfig.NATIVE_UPDATE_MANIFEST_URL);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new SecurityException("只允許 HTTPS 更新來源");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-cache");
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
                    if (total > 512 * 1024) {
                        throw new SecurityException("更新清單過大");
                    }
                    output.write(buffer, 0, read);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }
}
