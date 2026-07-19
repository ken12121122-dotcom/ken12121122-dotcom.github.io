package com.amin.pocketgba;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
    private static final String UPDATE_APK_NAME = "amin-update-verified.apk";
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
    private PackageInstaller packageInstaller;
    private int activeSessionId = -1;
    private boolean waitingForInstallSourcePermission;

    private final PackageInstaller.SessionCallback sessionCallback =
            new PackageInstaller.SessionCallback() {
                @Override
                public void onCreated(int sessionId) {
                    // Session identity is recorded when createSession returns.
                }

                @Override
                public void onBadgingChanged(int sessionId) {
                    // No-op.
                }

                @Override
                public void onActiveChanged(int sessionId, boolean active) {
                    // No-op.
                }

                @Override
                public void onProgressChanged(int sessionId, float progress) {
                    if (sessionId != activeSessionId) return;
                    int percent = 80 + Math.round(Math.max(0f, Math.min(1f, progress)) * 19f);
                    runOnUiThread(() -> {
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(percent);
                        statusView.setText("正在安裝更新…");
                        detailView.setText("系統安裝進度 " + percent + "%\n請保持 App 開啟。完成後會自動重新啟動。");
                    });
                }

                @Override
                public void onFinished(int sessionId, boolean success) {
                    if (sessionId != activeSessionId) return;
                    runOnUiThread(() -> {
                        if (success) {
                            progressBar.setProgress(100);
                            statusView.setText("更新完成，正在重新啟動…");
                            detailView.setText("Android 已接受新版 APK。App 將切換至新版本。");
                        }
                    });
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageInstaller = getPackageManager().getPackageInstaller();
        packageInstaller.registerSessionCallback(sessionCallback, new Handler(Looper.getMainLooper()));
        buildUi();

        String previousError = getIntent().getStringExtra("install_error");
        if (previousError != null && !previousError.trim().isEmpty()) {
            showResult("上次安裝未完成", previousError, true);
        } else {
            checkForUpdate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForInstallSourcePermission
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallSourcePermission = false;
            installVerifiedApk();
        }
    }

    @Override
    protected void onDestroy() {
        if (packageInstaller != null) {
            packageInstaller.unregisterSessionCallback(sessionCallback);
        }
        super.onDestroy();
    }

    private void buildUi() {
        int padding = dp(22);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("GAME-STYLE UPDATE");
        eyebrow.setTextSize(12f);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(eyebrow, matchWrap());

        TextView title = new TextView(this);
        title.setText("Amin 自動更新");
        title.setTextSize(28f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(8);
        content.addView(title, titleParams);

        TextView intro = new TextView(this);
        intro.setText("新版會在這個畫面完成下載、四重驗證與安裝。只有 Android 強制要求時，才會出現系統確認。");
        intro.setTextSize(14f);
        intro.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams introParams = matchWrap();
        introParams.topMargin = dp(10);
        content.addView(intro, introParams);

        statusView = new TextView(this);
        statusView.setTextSize(20f);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(26);
        content.addView(statusView, statusParams);

        detailView = new TextView(this);
        detailView.setTextSize(14f);
        detailView.setLineSpacing(0f, 1.4f);
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(12);
        content.addView(detailView, detailParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(14)
        );
        progressParams.topMargin = dp(24);
        content.addView(progressBar, progressParams);

        primaryButton = new Button(this);
        primaryButton.setVisibility(View.GONE);
        primaryButton.setAllCaps(false);
        LinearLayout.LayoutParams primaryParams = matchWrap();
        primaryParams.topMargin = dp(20);
        content.addView(primaryButton, primaryParams);

        retryButton = new Button(this);
        retryButton.setText("重新開始更新");
        retryButton.setAllCaps(false);
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(view -> checkForUpdate());
        LinearLayout.LayoutParams retryParams = matchWrap();
        retryParams.topMargin = dp(10);
        content.addView(retryButton, retryParams);

        Button closeButton = new Button(this);
        closeButton.setText("稍後再說");
        closeButton.setAllCaps(false);
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

    private void setBusy(String status, String detail, boolean determinate, int progress) {
        runOnUiThread(() -> {
            statusView.setText(status);
            detailView.setText(detail);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(!determinate);
            if (determinate) progressBar.setProgress(progress);
            primaryButton.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
        });
    }

    private void showResult(String status, String detail, boolean canRetry) {
        runOnUiThread(() -> {
            statusView.setText(status);
            detailView.setText(detail);
            progressBar.setVisibility(View.GONE);
            primaryButton.setVisibility(View.GONE);
            retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        });
    }

    private void checkForUpdate() {
        releaseManifest = null;
        verifiedApk = null;
        setBusy(
                "正在檢查新版…",
                "目前版本：" + BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE,
                false,
                0
        );

        EXECUTOR.execute(() -> {
            try {
                JSONObject manifest = fetchJson(BuildConfig.NATIVE_UPDATE_MANIFEST_URL);
                validateManifest(manifest);
                releaseManifest = manifest;

                if (!manifest.optBoolean("enabled", false)) {
                    showResult(
                            "更新通道目前關閉",
                            releaseNotes(manifest),
                            true
                    );
                    return;
                }

                long latestCode = manifest.getLong("latestVersionCode");
                String latestName = manifest.getString("latestVersionName");
                if (latestCode <= BuildConfig.VERSION_CODE) {
                    showResult(
                            "目前已是最新版本",
                            "本機：" + BuildConfig.VERSION_NAME + " · code " + BuildConfig.VERSION_CODE
                                    + "\n線上：" + latestName + " · code " + latestCode,
                            false
                    );
                    return;
                }

                setBusy(
                        "發現 " + latestName,
                        "正在自動下載並驗證。檔案大小："
                                + formatBytes(manifest.optLong("sizeBytes", -1L)),
                        true,
                        0
                );
                downloadAndVerify();
            } catch (Exception error) {
                showResult("更新檢查失敗", safeMessage(error), true);
            }
        });
    }

    private JSONObject fetchJson(String urlText) throws Exception {
        HttpURLConnection connection = openTrustedConnection(urlText);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
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
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
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
        if (!getPackageName().equals(manifest.optString("packageId"))) {
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
            showResult("沒有可下載版本", "請重新檢查更新。", true);
            return;
        }

        EXECUTOR.execute(() -> {
            File partial = new File(getCacheDir(), "amin-update.partial.apk");
            File completed = new File(getCacheDir(), UPDATE_APK_NAME);
            try {
                deleteIfPresent(partial);
                deleteIfPresent(completed);

                HttpURLConnection connection = openTrustedConnection(manifest.getString("apkUrl"));
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setUseCaches(false);
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
                        if (total > MAX_APK_BYTES) {
                            throw new SecurityException("APK 超過允許大小。");
                        }
                        output.write(buffer, 0, read);
                        updateDownloadProgress(total, expectedLength);
                    }
                    output.flush();
                    if (expectedLength > 0 && total != expectedLength) {
                        throw new SecurityException("APK 大小與清單不一致。");
                    }
                } finally {
                    connection.disconnect();
                }

                setBusy("正在執行四重驗證…", "比對檔案、套件、版本碼與永久簽章。", true, 72);
                verifyApk(partial, manifest);
                copyFile(partial, completed);
                deleteIfPresent(partial);
                verifiedApk = completed;
                setBusy("驗證完成", "安全檢查全部通過，正在提交 Android 安裝工作階段。", true, 80);
                installVerifiedApk();
            } catch (Exception error) {
                try {
                    deleteIfPresent(partial);
                } catch (Exception ignored) {
                    // Preserve original error.
                }
                showResult("更新失敗，舊版未受影響", safeMessage(error), true);
            }
        });
    }

    private void updateDownloadProgress(long completed, long total) {
        if (total <= 0) return;
        int percent = (int) Math.min(70L, completed * 70L / total);
        runOnUiThread(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setProgress(percent);
            statusView.setText("正在下載更新… " + percent + "%");
            detailView.setText(formatBytes(completed) + " / " + formatBytes(total)
                    + "\n下載完成後會自動驗證與安裝。");
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
        if (archiveVersion <= BuildConfig.VERSION_CODE) {
            throw new SecurityException("APK 版本碼沒有高於目前版本。");
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

    private void installVerifiedApk() {
        File apk = verifiedApk;
        if (apk == null || !apk.isFile()) {
            showResult("找不到已驗證 APK", "請重新開始更新。", true);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallSourcePermission = true;
            runOnUiThread(() -> {
                statusView.setText("需要一次安裝來源授權");
                detailView.setText("開啟「允許此來源」後返回，更新會自動繼續。這是 Android 對非商店 APK 的一次性保護。");
                progressBar.setVisibility(View.GONE);
                primaryButton.setText("開啟允許此來源");
                primaryButton.setVisibility(View.VISIBLE);
                primaryButton.setOnClickListener(view -> openInstallSourceSettings());
            });
            return;
        }

        setBusy("正在準備安裝…", "已驗證 APK 正在寫入 Android 安裝工作階段。", true, 80);
        EXECUTOR.execute(() -> {
            try {
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL
                );
                params.setAppPackageName(getPackageName());
                params.setSize(apk.length());
                params.setInstallReason(PackageManager.INSTALL_REASON_USER);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(
                            PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    );
                    params.setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST);
                }

                int sessionId = packageInstaller.createSession(params);
                activeSessionId = sessionId;

                try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
                    try (InputStream input = new BufferedInputStream(new FileInputStream(apk));
                         OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                        byte[] buffer = new byte[64 * 1024];
                        long total = 0;
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                            total += read;
                            float staged = apk.length() <= 0 ? 0f : (float) total / (float) apk.length();
                            session.setStagingProgress(staged);
                            int percent = 80 + Math.round(Math.min(1f, staged) * 15f);
                            runOnUiThread(() -> {
                                progressBar.setProgress(percent);
                                statusView.setText("正在準備安裝… " + percent + "%");
                            });
                        }
                        session.fsync(output);
                    }

                    Intent statusIntent = new Intent(
                            this,
                            PackageInstallResultReceiver.class
                    );
                    statusIntent.setAction(PackageInstallResultReceiver.ACTION_INSTALL_STATUS);
                    statusIntent.putExtra("session_id", sessionId);
                    int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        pendingFlags |= PendingIntent.FLAG_MUTABLE;
                    }
                    PendingIntent statusReceiver = PendingIntent.getBroadcast(
                            this,
                            sessionId,
                            statusIntent,
                            pendingFlags
                    );

                    runOnUiThread(() -> {
                        progressBar.setProgress(96);
                        statusView.setText("正在安裝更新…");
                        detailView.setText("Android 正在切換至新版。正常情況不需要離開這個畫面。");
                    });
                    session.commit(statusReceiver.getIntentSender());
                }
            } catch (Exception error) {
                if (activeSessionId != -1) {
                    try {
                        packageInstaller.abandonSession(activeSessionId);
                    } catch (Exception ignored) {
                        // Preserve the original error.
                    }
                }
                showResult("安裝工作階段失敗", safeMessage(error), true);
            }
        });
    }

    private void openInstallSourceSettings() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            showResult("無法開啟安裝來源設定", safeMessage(error), true);
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
