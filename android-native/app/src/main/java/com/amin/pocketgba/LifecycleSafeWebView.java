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
 * The same trusted WebView owns the narrow AminNativeSaveVault JavaScript bridge. The
 * bridge accepts only SHA-256 game identities and bounded save payloads, and can access
 * only the app-private gba-saves directory.
 */
public final class LifecycleSafeWebView extends WebView {
    private volatile boolean destroyed;

    public LifecycleSafeWebView(Context context) {
        super(context);
        installNativeSaveVault(context);
    }

    public LifecycleSafeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        installNativeSaveVault(context);
    }

    public LifecycleSafeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        installNativeSaveVault(context);
    }

    @SuppressLint("AddJavascriptInterface")
    private void installNativeSaveVault(Context context) {
        addJavascriptInterface(
                new NativeSaveVaultBridge(context.getApplicationContext()),
                "AminNativeSaveVault"
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
        super.destroy();
    }

    boolean isDestroyedForTest() {
        return destroyed;
    }
}
