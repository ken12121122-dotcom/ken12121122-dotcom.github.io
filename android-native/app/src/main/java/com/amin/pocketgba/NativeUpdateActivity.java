package com.amin.pocketgba;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NativeUpdateActivity extends Activity {
    private static final long MAX_MANIFEST_BYTES = 512L * 1024L;
    private static final long MAX_APK_BYTES = 250L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXACT_HOSTS = new HashSet<>(Arrays.asList(
            "ken12121122-dotcom.github.io",
            "github.com"
    ));
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private TextView statusView;
    private TextView detailView;
    private ProgressBar progressBar;
    private Button primaryButton;
    private Button retryButton;
    private JSONObject releaseManifest;
    private File verifiedApk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        checkForUpdate();
    }

    private void buildUi() {
        int padding = dp(22);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Amin Native Update Center");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setTextSize(18f);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(22);
        content.addView(statusView, statusParams);

        detailView = new TextView(this);
        detailView.setTextSize(14f);
        detailView.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(12);
        content.addView(detailView, detailParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12)
        );
        progressParams.topMargin = dp(22);
        content.addView(progressBar, progressParams);

        primaryButton = new Button(this);
        primaryButton.setEnabled(false);
        LinearLayout.LayoutParams primaryParams = matchWrap();
        primaryParams.topMargin = dp(22);
        content.addView(primaryButton, primaryParams);

        retryButton = new Button(this);
        retryButton.setText("重新檢查");
        retryButton.setOnClickListener(view -> checkForUpdate());
        LinearLayout.LayoutParams retryParams = matchWrap();
        retryParams.topMargin = dp(10);
        content.addView(retryButton, retryParams);

        Button closeButton = new Button(this);
        closeButton.setText("關閉");
        closeButton.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams closeParams = matchWrap();
        closeParams.topMargin = dp(10);
        content.addView(closeButton, closeParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        setContentView(scrollView);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setBusy(String status, String detail) {
        runOnUiThread(() -> {
            statusView.setText(status);
            detailView.setText(detail);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(true);
            primaryButton.setEnabled(false);
            retryButton.setEnabled(false);
        });
    }

    private void showResult(String status, String detail, boolean canDownload) {
        runOnUiThread(() -> {
            statusView.setText(status);
            detailView.setText(detail);
            progressBar.setVisibility(View.GONE);
            primaryButton.setEnabled(canDownload);
            retryButton.setEnabled(true);
        });
    }

    private void checkForUpdate() {
        verifiedApk = null;
        releaseManifest = null;
        primaryButton.setText("下載並驗證更新");
        primaryButton.setOnClickListener(view -> downloadAndVerify());
        setBusy(
                "正在檢查原生版本…",
                "目前版本：" + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）"
        );

        EXECUTOR.execute(() -> {
            try {
                JSONObject manifest = fetchJson(BuildConfig.NATIVE_UPDATE_MANIFEST_URL);
                validateManifest(manifest);
                releaseManifest = manifest;

                if (!manifest.optBoolean("enabled", false)) {
                    showResult(
                            "正式更新通道尚未啟用",
                            releaseNotes(manifest) + "\n\n安全鎖定中，不會下載 APK。",
                            false
                    );
                    return;
                }

                long latestCode = manifest.getLong("latestVersionCode");
                String latestName = manifest.getString("latestVersionName");
                if (latestCode <= BuildConfig.VERSION_CODE) {
                    showResult(
                            "目前已是最新版本",
                            "本機：" + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）\n"
                                    + "線上：" + latestName + "（" + latestCode + "）\n\n"
                                    + releaseNotes(manifest),
                            false
                    );
                    return;
                }

                showResult(
                        "找到 Amin Pocket GBA " + latestName,
                        "版本碼：" + latestCode
                                + "\n檔案大小：" + formatBytes(manifest.optLong("sizeBytes", -1L))
                                + "\n\n" + releaseNotes(manifest)
                                + "\n\n下載後會驗證檔案、套件、版本與簽章。",
                        true
                );
            } catch (Exception error) {
                showResult("更新檢查失敗", safeMessage(error), false);
            }
        });
    }

    private JSONObject fetchJson(String urlText) throws Exception {
        HttpURLConnection connection = openTrustedConnection(urlText);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.connect();
        verifyTrustedUrl(connection.getURL());
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("更新清單 HTTP " + connection.getResponseCode());
        }
        if (connection.getContentLengthLong() > MAX_MANIFEST_BYTES) {
            throw new SecurityException("更新清單超過安全大小。");
        }

        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_MANIFEST_BYTES) {
                    throw new SecurityException("更新清單超過安全大小。");
                }
                output.write(buffer, 0, read);
            }
            String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
            return new JSONObject(json);
        } finally {
            connection.disconnect();
        }
    }

    private void validateManifest(JSONObject manifest) throws Exception {
        if (!"amin-native-release-manifest".equals(manifest.optString("format"))) {
            throw new IllegalStateException("更新清單格式不正確。");
        }
        if (manifest.optInt("manifestVersion") != 1) {
            throw new IllegalStateException("更新清單版本不相容。");
        }
        if (!"com.amin.pocketgba".equals(manifest.optString("packageId"))) {
            throw new SecurityException("更新套件識別碼不受信任。");
        }
        if (!manifest.optBoolean("enabled", false)) return;

        String apkUrl = manifest.optString("apkUrl");
        String apkHash = normalizeFingerprint(manifest.optString("apkSha256"));
        String signerHash = normalizeFingerprint(manifest.optString("signerCertificateSha256"));
        long versionCode = manifest.optLong("latestVersionCode", 0L);
        if (isBlank(apkUrl) || apkHash.length() != 64 || signerHash.length() != 64 || versionCode <= 0) {
            throw new SecurityException("已啟用的更新清單缺少必要驗證資料。");
        }
        verifyTrustedUrl(new URL(apkUrl));
    }

    private HttpURLConnection openTrustedConnection(String urlText) throws Exception {
        URL url = new URL(urlText);
        verifyTrustedUrl(url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private void verifyTrustedUrl(URL url) throws Exception {
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new SecurityException("只允許 HTTPS 更新來源。");
        }
        String host = url.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_EXACT_HOSTS.contains(host)
                || host.endsWith(".githubusercontent.com")
                || host.endsWith(".githubassets.com");
        if (!allowed) {
            throw new SecurityException("更新來源網域不受信任：" + host);
        }
    }

    private void downloadAndVerify() {
        JSONObject manifest = releaseManifest;
        if (manifest == null || !manifest.optBoolean("enabled", false)) {
            showResult("無可下載版本", "請重新檢查更新。", false);
            return;
        }

        primaryButton.setEnabled(false);
        retryButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
        statusView.setText("正在下載更新…");

        EXECUTOR.execute(() -> {
            File partial = new File(getCacheDir(), "amin-update.partial.apk");
            File completed = new File(getCacheDir(), "amin-update-verified.apk");
            try {
                deleteIfPresent(partial);
                deleteIfPresent(completed);

                HttpURLConnection connection = openTrustedConnection(manifest.getString("apkUrl"));
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.connect();
                verifyTrustedUrl(connection.getURL());
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("APK 下載 HTTP " + connection.getResponseCode());
                }

                long expectedLength = manifest.optLong("sizeBytes", connection.getContentLengthLong());
                long responseLength = connection.getContentLengthLong();
                if (responseLength > MAX_APK_BYTES || expectedLength > MAX_APK_BYTES) {
                    throw new SecurityException("APK 超過允許大小。");
                }

                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(partial))) {
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_APK_BYTES) throw new SecurityException("APK 超過允許大小。");
                        output.write(buffer, 0, read);
                        updateProgress(total, expectedLength);
                    }
                    output.flush();
                    if (expectedLength > 0 && total != expectedLength) {
                        throw new SecurityException("APK 大小與清單不一致。");
                    }
                } finally {
                    connection.disconnect();
                }

                verifyApk(partial, manifest);
                copyFile(partial, completed);
                deleteIfPresent(partial);
                verifiedApk = completed;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusView.setText("APK 驗證完成");
                    detailView.setText("SHA-256、套件、版本碼與簽章憑證全部相符。\n\nAndroid 仍會要求確認安裝。");
                    primaryButton.setText("開啟 Android 安裝畫面");
                    primaryButton.setOnClickListener(view -> openInstaller());
                    primaryButton.setEnabled(true);
                    retryButton.setEnabled(true);
                });
            } catch (Exception error) {
                try {
                    deleteIfPresent(partial);
                } catch (Exception ignored) {
                    // Keep the original verification failure as the user-facing error.
                }
                showResult("下載或驗證失敗", safeMessage(error), true);
            }
        });
    }

    private void updateProgress(long completed, long total) {
        if (total <= 0) return;
        int percent = (int) Math.min(100L, completed * 100L / total);
        runOnUiThread(() -> {
            progressBar.setProgress(percent);
            detailView.setText("下載進度：" + percent + "%\n"
                    + formatBytes(completed) + " / " + formatBytes(total));
        });
    }

    private void verifyApk(File apk, JSONObject manifest) throws Exception {
        if (!normalizeFingerprint(manifest.getString("apkSha256")).equals(sha256(apk))) {
            throw new SecurityException("APK SHA-256 不一致。");
        }

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) throw new SecurityException("Android 無法辨識下載的 APK。");
        if (!manifest.getString("packageId").equals(archive.packageName)) {
            throw new SecurityException("APK 套件名稱不一致。");
        }

        long archiveVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode()
                : archive.versionCode;
        if (archiveVersion != manifest.getLong("latestVersionCode")) {
            throw new SecurityException("APK 版本碼不一致。");
        }

        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (archive.signingInfo == null) throw new SecurityException("APK 缺少簽章資訊。");
            signatures = archive.signingInfo.getApkContentsSigners();
        } else {
            signatures = archive.signatures;
        }
        if (signatures == null || signatures.length != 1) {
            throw new SecurityException("APK 簽章數量不符合預期。");
        }

        String expectedSigner = normalizeFingerprint(manifest.getString("signerCertificateSha256"));
        String actualSigner = sha256(signatures[0].toByteArray());
        if (!expectedSigner.equals(actualSigner)) {
            throw new SecurityException("APK 簽章憑證不一致。");
        }
    }

    private void openInstaller() {
        if (verifiedApk == null || !verifiedApk.isFile()) {
            showResult("找不到已驗證 APK", "請重新下載更新。", true);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())
                ));
                Toast.makeText(this, "允許安裝更新後，返回再按一次安裝。", Toast.LENGTH_LONG).show();
            } catch (ActivityNotFoundException error) {
                showResult("無法開啟安裝權限", safeMessage(error), false);
            }
            return;
        }

        Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                verifiedApk
        );
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(installIntent);
        } catch (ActivityNotFoundException error) {
            showResult("找不到 Android 安裝程式", safeMessage(error), false);
        }
    }

    private String releaseNotes(JSONObject manifest) {
        JSONArray notes = manifest.optJSONArray("releaseNotes");
        if (notes == null || notes.length() == 0) return "沒有版本說明。";
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < notes.length(); index++) {
            if (index > 0) builder.append('\n');
            builder.append("• ").append(notes.optString(index));
        }
        return builder.toString();
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return toHex(digest.digest());
    }

    private String sha256(byte[] bytes) throws Exception {
        return toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(String.format(Locale.ROOT, "%02x", value));
        return builder.toString();
    }

    private String normalizeFingerprint(String value) {
        return value == null ? "" : value.replace(":", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private void copyFile(File source, File target) throws Exception {
        try (InputStream input = new FileInputStream(source);
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        }
    }

    private void deleteIfPresent(File file) throws Exception {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("無法清除舊的更新暫存檔。");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "未知";
        String[] units = { "B", "KB", "MB", "GB" };
        double value = bytes;
        int index = 0;
        while (value >= 1024d && index < units.length - 1) {
            value /= 1024d;
            index += 1;
        }
        return String.format(Locale.TAIWAN, index == 0 ? "%.0f %s" : "%.1f %s", value, units[index]);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return isBlank(message) ? error.getClass().getSimpleName() : message;
    }
}
