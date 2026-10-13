package com.amin.pocketgba;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
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
 * remains an explicit browser-session GitHub read. No JS bridge is added. */
public final class LivingLibraryActivity extends Activity {
    private WebView web;
    private boolean fallbackStarted;
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
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { committedUrl = ""; }
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
        web.evaluateJavascript("(function(){var d=document.getElementById('drawerWrap');if(d&&d.classList.contains('open')){document.getElementById(document.getElementById('readerShell').hidden?'closeDrawer':'readerBack').click();return true;}return false;})()", result -> {
            if (!"true".equals(result)) { if (web.canGoBack()) web.goBack(); else LivingLibraryActivity.super.onBackPressed(); }
        });
    }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.loadUrl("about:blank"); web.removeAllViews(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
