package com.amin.pocketgba;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

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
                if (activity instanceof MainActivity) {
                    WebView webView = activity.findViewById(R.id.webView);
                    if (webView != null) {
                        // The bridge exposes only bounded GBA SRAM reads/writes inside app-private storage.
                        // It cannot browse arbitrary files, execute commands, or access other app data.
                        webView.addJavascriptInterface(
                                new NativeSaveBridge(activity),
                                "AminNativeSaveVault"
                        );
                    }
                }

                if (activity instanceof ControlCenterActivity) {
                    rewritePreviewLabels(activity.findViewById(android.R.id.content));
                }
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private void rewritePreviewLabels(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence value = textView.getText();
            if (value != null) {
                String updated = value.toString()
                        .replace("PREVIEW 3", "PREVIEW 4")
                        .replace("Preview 3", "Preview 4");
                if (!updated.contentEquals(value)) textView.setText(updated);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index += 1) {
                rewritePreviewLabels(group.getChildAt(index));
            }
        }
    }
}
