package com.amin.pocketgba;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

/**
 * A WebView that refuses to execute callbacks queued before it was destroyed.
 *
 * MainActivity receives asynchronous network and controller events. A callback may be
 * posted while the WebView is alive but execute after Activity.onDestroy() has cleared
 * the Activity field. Guarding at the View queue boundary prevents that stale callback
 * from dereferencing a destroyed WebView without changing emulator or save behavior.
 *
 * The trusted WebView owns two narrow JavaScript bridges:
 * - AminNativeSaveVault stores only bounded save payloads under SHA-256 game identities.
 * - AminNativeDiagnostics exposes only sanitized pending crash data and device versions.
 */
public final class LifecycleSafeWebView extends WebView {
    private volatile boolean destroyed;

    public LifecycleSafeWebView(Context context) {
        super(context);
        installNativeBridges(context);
    }

    public LifecycleSafeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        installNativeBridges(context);
    }

    public LifecycleSafeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        installNativeBridges(context);
    }

    @SuppressLint("AddJavascriptInterface")
    private void installNativeBridges(Context context) {
        Context appContext = context.getApplicationContext();
        addJavascriptInterface(
                new NativeSaveVaultBridge(appContext),
                "AminNativeSaveVault"
        );
        addJavascriptInterface(
                new NativeDiagnosticsBridge(appContext),
                "AminNativeDiagnostics"
        );
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
        removeJavascriptInterface("AminNativeDiagnostics");
        super.destroy();
    }

    boolean isDestroyedForTest() {
        return destroyed;
    }
}
