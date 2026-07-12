package com.amin.pocketgba;

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
 */
public final class LifecycleSafeWebView extends WebView {
    private volatile boolean destroyed;

    public LifecycleSafeWebView(Context context) {
        super(context);
    }

    public LifecycleSafeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LifecycleSafeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
        super.destroy();
    }

    boolean isDestroyedForTest() {
        return destroyed;
    }
}
