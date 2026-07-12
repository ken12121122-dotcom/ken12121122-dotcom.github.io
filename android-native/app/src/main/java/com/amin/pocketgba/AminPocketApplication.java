package com.amin.pocketgba;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.webkit.WebView;

public final class AminPocketApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
                // Android 11+ can expose a null WindowInsetsController until the DecorView exists.
                // MainActivity changes orientation before drawing, so prepare the window first.
                activity.getWindow().getDecorView();
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (!(activity instanceof MainActivity)) return;
                WebView webView = activity.findViewById(R.id.webView);
                if (webView == null) return;
                // The bridge exposes only bounded GBA SRAM reads/writes inside app-private storage.
                // It cannot browse arbitrary files, execute commands, or access other app data.
                webView.addJavascriptInterface(
                        new NativeSaveBridge(activity),
                        "AminNativeSaveVault"
                );
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }
}
