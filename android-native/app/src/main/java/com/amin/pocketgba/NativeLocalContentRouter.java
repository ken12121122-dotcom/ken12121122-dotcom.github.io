package com.amin.pocketgba;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Serves app-private ROMs and bundled EmulatorJS assets under the existing trusted HTTPS host. */
final class NativeLocalContentRouter {
    private static final Pattern ROM_PATH = Pattern.compile(
            "^" + Pattern.quote(NativeCartridgeVaultBridge.ROM_PATH_PREFIX)
                    + "([a-fA-F0-9]{64})\\.gba$"
    );
    private static final String ASSET_ROOT = "emulator/";

    private final Context appContext;
    private final String trustedHost;
    private final String trustedOrigin;

    NativeLocalContentRouter(Context context) {
        appContext = context.getApplicationContext();
        Uri appUri = Uri.parse(BuildConfig.APP_WEB_URL);
        trustedHost = appUri.getHost();
        trustedOrigin = trustedHost == null ? "" : "https://" + trustedHost;
    }

    WebResourceResponse intercept(WebResourceRequest request) {
        if (request == null) return null;
        Uri uri = request.getUrl();
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || trustedHost == null
                || !trustedHost.equalsIgnoreCase(uri.getHost())) {
            return null;
        }

        String path = uri.getPath();
        if (path == null) return null;
        java.util.regex.Matcher romMatcher = ROM_PATH.matcher(path);
        if (romMatcher.matches()) {
            return serveRom(romMatcher.group(1));
        }
        if (path.startsWith(NativeCartridgeVaultBridge.ENGINE_PATH_PREFIX)) {
            return serveEngineAsset(path.substring(
                    NativeCartridgeVaultBridge.ENGINE_PATH_PREFIX.length()
            ));
        }
        return null;
    }

    private WebResourceResponse serveRom(String key) {
        File file = NativeCartridgeVaultBridge.resolveRomFile(appContext, key);
        if (file == null || !file.isFile() || file.length() <= 0) {
            return textResponse(404, "Not Found", "Native ROM is unavailable.");
        }
        try {
            Map<String, String> headers = baseHeaders("no-store");
            headers.put("Content-Length", String.valueOf(file.length()));
            headers.put("Accept-Ranges", "none");
            return new WebResourceResponse(
                    "application/octet-stream",
                    null,
                    200,
                    "OK",
                    headers,
                    new FileInputStream(file)
            );
        } catch (Exception error) {
            return textResponse(500, "Read Failed", "Unable to read native ROM.");
        }
    }

    private WebResourceResponse serveEngineAsset(String relativePath) {
        String safePath = sanitizeAssetPath(relativePath);
        if (safePath == null || safePath.isEmpty()) {
            return textResponse(400, "Bad Request", "Invalid emulator asset path.");
        }
        try {
            AssetManager assets = appContext.getAssets();
            InputStream stream = assets.open(ASSET_ROOT + safePath, AssetManager.ACCESS_STREAMING);
            Map<String, String> headers = baseHeaders("public, max-age=31536000, immutable");
            return new WebResourceResponse(
                    mimeType(safePath),
                    isTextAsset(safePath) ? "utf-8" : null,
                    200,
                    "OK",
                    headers,
                    stream
            );
        } catch (Exception error) {
            return textResponse(404, "Not Found", "Bundled emulator asset is unavailable.");
        }
    }

    private String sanitizeAssetPath(String value) {
        if (value == null) return null;
        String path = Uri.decode(value).replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty() || path.contains("../") || path.equals("..") || path.indexOf('\0') >= 0) {
            return null;
        }
        return path;
    }

    private Map<String, String> baseHeaders(String cacheControl) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", cacheControl);
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Cross-Origin-Resource-Policy", "same-origin");
        if (!trustedOrigin.isEmpty()) {
            headers.put("Access-Control-Allow-Origin", trustedOrigin);
        }
        return headers;
    }

    private WebResourceResponse textResponse(int status, String reason, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = baseHeaders("no-store");
        headers.put("Content-Length", String.valueOf(bytes.length));
        return new WebResourceResponse(
                "text/plain",
                "utf-8",
                status,
                reason,
                headers,
                new ByteArrayInputStream(bytes)
        );
    }

    private boolean isTextAsset(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".js")
                || lower.endsWith(".css")
                || lower.endsWith(".json")
                || lower.endsWith(".html")
                || lower.endsWith(".txt")
                || lower.endsWith(".svg");
    }

    private String mimeType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".json") || lower.endsWith(".map")) return "application/json";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}
