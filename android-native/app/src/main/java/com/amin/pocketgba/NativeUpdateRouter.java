package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class NativeUpdateRouter {
    static final String EXTRA_AUTO_ADVANCE = "amin_auto_advance_native_update";
    private static final String EXTRA_CONSUMED = "amin_auto_advance_consumed";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private NativeUpdateRouter() {}

    static void openExistingUpdateFlow(Activity activity) {
        Intent intent = new Intent(activity, UpdateHubActivity.class);
        intent.putExtra(EXTRA_AUTO_ADVANCE, true);
        activity.startActivity(intent);
    }

    static void maybeAutoAdvance(UpdateHubActivity activity) {
        Intent intent = activity.getIntent();
        if (intent == null || !intent.getBooleanExtra(EXTRA_AUTO_ADVANCE, false)) return;
        if (intent.getBooleanExtra(EXTRA_CONSUMED, false)) return;
        intent.putExtra(EXTRA_CONSUMED, true);

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BuildConfig.NATIVE_UPDATE_MANIFEST_URL + "?route=" + System.currentTimeMillis());
                if (!"https".equalsIgnoreCase(url.getProtocol())) return;
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.connect();
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return;
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > 512 * 1024) return;
                        output.write(buffer, 0, read);
                    }
                    JSONObject manifest = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
                    if (!"amin-native-release-manifest".equals(manifest.optString("format"))) return;
                    if (!manifest.optBoolean("enabled", false)) return;
                    if (!BuildConfig.APPLICATION_ID.equals(manifest.optString("packageId"))) return;
                    if (manifest.optLong("latestVersionCode", 0L) <= BuildConfig.VERSION_CODE) return;
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        activity.startActivity(new Intent(activity, NativeUpdateActivity.class));
                    });
                }
            } catch (Exception ignored) {
                // The existing UpdateHubActivity remains visible and owns user-facing errors.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
