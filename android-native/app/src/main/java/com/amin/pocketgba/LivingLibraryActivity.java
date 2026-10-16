package com.amin.pocketgba;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.content.pm.PackageManager;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.JavaScriptReplyProxy;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.HashSet;
import java.util.Arrays;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Collections;

/** Trusted GitHub Pages web UI with bundled offline fallback; private GEN2
 * remains an explicit browser-session GitHub read. Voice transport is scoped to the exact home document. */
public final class LivingLibraryActivity extends Activity {
    private WebView web;
    private static final int REQUEST_LIBRARY_FILE = 4712;
    private ValueCallback<Uri[]> fileCallback;
    private String fileRequestPage = "";
    private void cancelFileRequest() {
        ValueCallback<Uri[]> previous = fileCallback;
        fileCallback = null; fileRequestPage = "";
        if (previous != null) previous.onReceiveValue(null);
    }
    boolean requestLibraryFile(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        cancelFileRequest();
        String[] types = params == null ? new String[0] : LivingLibraryFileChooserPolicy.mimeTypes(params.getAcceptTypes());
        if (!foreground || web == null || !LivingLibraryFileChooserPolicy.allowedPage(committedUrl, web.getUrl())
                || params == null || params.getMode() != WebChromeClient.FileChooserParams.MODE_OPEN
                || params.isCaptureEnabled() || types.length == 0) {
            callback.onReceiveValue(null); return true;
        }
        fileCallback = callback; fileRequestPage = committedUrl;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/json".equals(types[0]) ? "application/json" : "image/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, types);
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(picker, REQUEST_LIBRARY_FILE); }
        catch (ActivityNotFoundException | SecurityException error) {
            cancelFileRequest();
            Toast.makeText(this, "無法開啟檔案選擇器，請確認系統檔案 App 可用", Toast.LENGTH_SHORT).show();
        }
        return true;
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_LIBRARY_FILE || fileCallback == null) return;
        ValueCallback<Uri[]> callback = fileCallback;
        String page = fileRequestPage; fileCallback = null; fileRequestPage = "";
        Uri[] selected = result == RESULT_OK ? WebChromeClient.FileChooserParams.parseResult(result, data) : null;
        if (web == null || !page.equals(committedUrl) || !LivingLibraryFileChooserPolicy.allowedPage(committedUrl, web.getUrl())
                || selected == null || selected.length != 1 || selected[0] == null || !"content".equals(selected[0].getScheme())) selected = null;
        callback.onReceiveValue(selected);
    }
    private boolean fallbackStarted;
    private boolean foreground;
    private LivingLibraryVoiceController voice;
    private JavaScriptReplyProxy voiceReply;
    private String voiceOrigin = "";
    private boolean voiceAuthorized() {
        return web != null && LivingLibraryVoicePolicy.allowed(committedUrl, web.getUrl(), voiceOrigin, true, foreground);
    }
    private void configureVoiceTransport() {
        voice = new LivingLibraryVoiceController(this, this::voiceAuthorized, json -> {
            if (voiceReply != null && voiceAuthorized()) voiceReply.postMessage(json);
        });
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
        WebViewCompat.addWebMessageListener(web, "LivingLibraryNativeVoice",
                new HashSet<>(Arrays.asList("https://ken12121122-dotcom.github.io", "https://appassets.androidplatform.net")),
                (view, message, origin, mainFrame, reply) -> {
                    if (!LivingLibraryVoicePolicy.allowed(committedUrl, view.getUrl(), origin.toString(), mainFrame, foreground)) return;
                    String data = message.getData(); if (data == null || data.length() > 512) return;
                    try {
                        JSONObject request = new JSONObject(data);
                        if (request.optInt("v") != 1) return;
                        String op = request.optString("op"), session = request.optString("session");
                        if (!LivingLibraryVoicePolicy.validSession(session)) return;
                        voiceOrigin = origin.toString(); voiceReply = reply;
                        if ("start".equals(op)) voice.request(session);
                        else if ("stop".equals(op)) voice.stopSession(session);
                    } catch (JSONException ignored) { }
                });
    }
    volatile String committedUrl = "";
    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(0xff081426);
        getWindow().setNavigationBarColor(0xff081426);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // Online HTML/CSS/catalog are GitHub Pages-owned. Offline APK assets remain available.
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " LivingLibraryNative/" + BuildConfig.VERSION_NAME);
        configureVoiceTransport();
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return requestLibraryFile(callback, params);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!LivingLibraryPolicy.HOST.equals(uri.getHost())) return null;
                String path = "GET".equals(request.getMethod()) ? LivingLibraryPolicy.asset(uri.toString()) : null;
                try {
                    if (path != null) return new WebResourceResponse(path.endsWith(".js") ? "application/javascript" : "text/html",
                            "UTF-8", 200, "OK", Collections.singletonMap("Cache-Control", "no-store"), getAssets().open("workbench/" + path));
                } catch (IOException ignored) { }
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageCommitVisible(WebView view, String url) {
                committedUrl = LivingLibraryPolicy.isHome(url) ? url : "";
                if (!fallbackStarted && !LivingLibraryPolicy.isHome(url)) loadBundledFallback();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && LivingLibraryPolicy.isRemoteHome(request.getUrl().toString())) loadBundledFallback();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                        android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400
                        && LivingLibraryPolicy.isRemoteHome(request.getUrl().toString())) loadBundledFallback();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!fallbackStarted && LivingLibraryPolicy.isRemoteHome(url)) {
                    // A 200 HTML response is insufficient if a critical remote script failed.
                    view.evaluateJavascript("(function(){return !!(window.LivingLibrary && window.LivingLibraryCatalog && window.LivingBookPlacement && window.PackageCanvasGen2)})()", ready -> {
                        if (!"true".equals(ready)) loadBundledFallback();
                    });
                }
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { cancelFileRequest(); if (voice != null) voice.stop(); voiceReply = null; voiceOrigin = ""; committedUrl = ""; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String route = LivingLibraryPolicy.route(request.getUrl().toString());
                if (route != null) {
                    if (canOpenNative(committedUrl, request.isForMainFrame(), request.hasGesture())) openNative(route);
                    return true;
                }
                if (!request.isForMainFrame()) return false;
                if (LivingLibraryPolicy.isHome(request.getUrl().toString())) return false;
                if (LivingLibraryPolicy.asset(request.getUrl().toString()) != null) return false;
                String scheme = request.getUrl().getScheme();
                if (("https".equals(scheme) || "http".equals(scheme)) && request.hasGesture()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl())); }
                    catch (ActivityNotFoundException error) { Toast.makeText(LivingLibraryActivity.this, "沒有可開啟連結的 App", Toast.LENGTH_SHORT).show(); }
                }
                return true;
            }
        });
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets;
        });
        setContentView(root); ViewCompat.requestApplyInsets(root);
        if (state == null) loadRemoteWorkbench();
        else if (web.restoreState(state) == null) loadRemoteWorkbench();
    }
    private void loadRemoteWorkbench() {
        fallbackStarted = false;
        // Cache-bust the top-level document; content changes remain web-only.
        web.loadUrl(LivingLibraryPolicy.REMOTE_HOME + "?v=" + System.currentTimeMillis());
    }
    private void loadBundledFallback() {
        if (fallbackStarted || web == null) return;
        fallbackStarted = true;
        committedUrl = "";
        // Keep the same package/signature and safely retain all existing native routes.
        web.post(() -> {
            if (web == null) return;
            Toast.makeText(this, "線上書庫無法載入，使用內建離線圖書館", Toast.LENGTH_SHORT).show();
            web.loadUrl(LivingLibraryPolicy.HOME);
        });
    }
    static boolean canOpenNative(String page, boolean mainFrame, boolean gesture) {
        return mainFrame && gesture && LivingLibraryPolicy.isHome(page);
    }
    WebView workbenchView() { return web; }
    void openNative(String route) {
        String target = LivingLibraryPolicy.ROUTES.get(route);
        if (target == null) return;
        if ("voice-ball".equals(route)) {
            FoxPetPreferences.setDisplayMode(this, FoxPetPreferences.MODE_VOICE_BALL);
            UniversalControlAccessibilityService.setVoiceBubbleEnabled(this, true);
            UniversalControlAccessibilityService.refreshVoicePresentation(this);
            startActivity(new Intent(this, UniversalControlSetupActivity.class));
            return;
        }
        if ("gen2-chat".equals(route)) { startActivity(Gen2ChatActivity.intent(this, 0)); return; }
        Intent intent = new Intent();
        intent.setClassName(this, target.contains(".") ? target : getPackageName() + "." + target);
        startActivity(intent);
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); web.saveState(state); }
    @Override public void onBackPressed() {
        web.evaluateJavascript("(function(){var a=window.LivingScene&&window.LivingScene.atlas;if(a&&a.inFocus){a.exitFocus();return true;}var d=document.getElementById('drawerWrap');if(d&&d.classList.contains('open')){document.getElementById(document.getElementById('readerShell').hidden?'closeDrawer':'readerBack').click();return true;}return false;})()", result -> {
            if (!"true".equals(result)) { if (web.canGoBack()) web.goBack(); else LivingLibraryActivity.super.onBackPressed(); }
        });
    }
    @Override protected void onPause() { if (voice != null) voice.stop(); foreground = false; if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); foreground = true; if (web != null) web.onResume(); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LivingLibraryVoiceController.AUDIO_PERMISSION_REQUEST && voice != null)
            voice.permissionResult(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
    }
    @Override protected void onDestroy() {
        cancelFileRequest();
        if (voice != null) voice.destroy(); voiceReply = null;
        if (web != null) { web.stopLoading(); web.loadUrl("about:blank"); web.removeAllViews(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
