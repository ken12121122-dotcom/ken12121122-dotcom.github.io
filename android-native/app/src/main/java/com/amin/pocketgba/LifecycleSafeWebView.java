package com.amin.pocketgba;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class LifecycleSafeWebView extends WebView {
    private volatile boolean destroyed;
    private NativeLocalContentRouter localContentRouter;

    public LifecycleSafeWebView(Context context) {
        super(context);
        initializeNativeLayer(context);
    }

    public LifecycleSafeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeNativeLayer(context);
    }

    public LifecycleSafeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeNativeLayer(context);
    }

    @SuppressLint("AddJavascriptInterface")
    private void initializeNativeLayer(Context context) {
        Context appContext = context.getApplicationContext();
        localContentRouter = new NativeLocalContentRouter(appContext);
        addJavascriptInterface(new NativeSaveVaultBridge(appContext), "AminNativeSaveVault");
        addJavascriptInterface(new NativeCartridgeVaultBridge(appContext), "AminNativeCartridgeVault");
        addJavascriptInterface(new NativeDiagnosticsBridge(appContext), "AminNativeDiagnostics");
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        if (client == null || localContentRouter == null) {
            super.setWebViewClient(client);
            return;
        }
        super.setWebViewClient(new NativeContentWebViewClient(client, localContentRouter));
    }

    @Override
    public boolean post(Runnable action) {
        if (action == null || destroyed) return false;
        return super.post(() -> {
            if (!destroyed) action.run();
        });
    }

    @Override
    public boolean postDelayed(Runnable action, long delayMillis) {
        if (action == null || destroyed) return false;
        return super.postDelayed(() -> {
            if (!destroyed) action.run();
        }, delayMillis);
    }

    @Override
    public void destroy() {
        destroyed = true;
        removeJavascriptInterface("AminNativeSaveVault");
        removeJavascriptInterface("AminNativeCartridgeVault");
        removeJavascriptInterface("AminNativeDiagnostics");
        localContentRouter = null;
        super.destroy();
    }

    boolean isDestroyedForTest() {
        return destroyed;
    }
}
