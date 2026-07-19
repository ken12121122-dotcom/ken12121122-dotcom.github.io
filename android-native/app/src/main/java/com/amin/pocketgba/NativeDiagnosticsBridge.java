package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class NativeDiagnosticsBridge {
    private static final String PREFS_NAME = "amin-native-diagnostics";
    private static final String PENDING_REPORT_KEY = "pending-report-v1";
    private static final int MAX_MESSAGE_CHARS = 2000;
    private static final int MAX_STACK_CHARS = 8000;
    private static final AtomicBoolean HANDLER_INSTALLED = new AtomicBoolean(false);

    private static final Pattern DEVICE_PATH = Pattern.compile(
            "(?:file:///)?/(?:data|storage|sdcard|mnt)/[^\\s)]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTENT_URI = Pattern.compile(
            "content://[^\\s)]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMAIL = Pattern.compile(
            "[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"
    );
    private static final Pattern GAME_FILE = Pattern.compile(
            "[^\\s/\\\\]+\\.(?:gba|bin|zip|sav|state)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Context appContext;
    private final SharedPreferences preferences;

    NativeDiagnosticsBridge(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        installCrashHandler(appContext);
    }

    @JavascriptInterface
    public String getPendingReport() {
        return preferences.getString(PENDING_REPORT_KEY, "");
    }

    @JavascriptInterface
    public boolean ackPendingReport(String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) return false;
        String raw = preferences.getString(PENDING_REPORT_KEY, "");
        if (raw == null || raw.isEmpty()) return false;
        try {
            JSONObject report = new JSONObject(raw);
            if (!reportId.equals(report.optString("id"))) return false;
            return preferences.edit().remove(PENDING_REPORT_KEY).commit();
        } catch (JSONException ignored) {
            return false;
        }
    }

    @JavascriptInterface
    public String getEnvironment() {
        return buildEnvironment(appContext).toString();
    }

    static void recordWebViewError(Context context, String stage, String message) {
        JSONObject report = new JSONObject();
        try {
            report.put("id", UUID.randomUUID().toString());
            report.put("stage", sanitize(stage, 120));
            report.put("message", sanitize(message, MAX_MESSAGE_CHARS));
            report.put("stack", JSONObject.NULL);
            report.put("environment", buildEnvironment(context.getApplicationContext()));
            report.put("createdAt", System.currentTimeMillis());
            savePending(context.getApplicationContext(), report);
        } catch (JSONException ignored) {
            // A diagnostic failure must never crash the app.
        }
    }

    private static void installCrashHandler(Context context) {
        if (!HANDLER_INSTALLED.compareAndSet(false, true)) return;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                JSONObject report = new JSONObject();
                report.put("id", UUID.randomUUID().toString());
                report.put("stage", "uncaught-exception");
                report.put(
                        "message",
                        sanitize(
                                throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage()),
                                MAX_MESSAGE_CHARS
                        )
                );
                report.put("stack", sanitize(stackTrace(throwable), MAX_STACK_CHARS));
                report.put("environment", buildEnvironment(context));
                report.put("thread", sanitize(thread == null ? "unknown" : thread.getName(), 80));
                report.put("createdAt", System.currentTimeMillis());
                savePending(context, report);
            } catch (Exception ignored) {
                // Never replace the original crash with reporter failure.
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
    }

    private static void savePending(Context context, JSONObject report) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PENDING_REPORT_KEY, report.toString())
                .commit();
    }

    private static JSONObject buildEnvironment(Context context) {
        JSONObject environment = new JSONObject();
        try {
            environment.put("androidSdk", Build.VERSION.SDK_INT);
            environment.put("androidRelease", sanitize(Build.VERSION.RELEASE, 40));
            environment.put("deviceManufacturer", sanitize(Build.MANUFACTURER, 80));
            environment.put("deviceModel", sanitize(Build.MODEL, 100));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
                if (webViewPackage != null) {
                    environment.put("webViewVersion", sanitize(webViewPackage.versionName, 80));
                }
            }
            PackageInfo appPackage = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            environment.put("appVersionName", sanitize(appPackage.versionName, 80));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                environment.put("appVersionCode", appPackage.getLongVersionCode());
            } else {
                environment.put("appVersionCode", appPackage.versionCode);
            }
        } catch (Exception ignored) {
            // Return the fields that were collected successfully.
        }
        return environment;
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        throwable.printStackTrace(writer);
        writer.flush();
        return buffer.toString();
    }

    private static String sanitize(String value, int maxLength) {
        String text = value == null ? "" : value;
        text = DEVICE_PATH.matcher(text).replaceAll("/[device-path-redacted]");
        text = CONTENT_URI.matcher(text).replaceAll("content://[redacted]");
        text = EMAIL.matcher(text).replaceAll("[email-redacted]");
        text = GAME_FILE.matcher(text).replaceAll("[game-file-redacted]");
        text = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]", "").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
