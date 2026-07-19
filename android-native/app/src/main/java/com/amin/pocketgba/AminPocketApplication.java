package com.amin.pocketgba;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class AminPocketApplication extends Application {
    private static final String UNIVERSAL_CONTROL_ENTRY_TAG = "amin-universal-control-entry";

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
                    View root = activity.findViewById(android.R.id.content);
                    rewritePreviewLabels(root);
                    attachUniversalControlEntry(activity, root);
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

    private void attachUniversalControlEntry(Activity activity, View root) {
        if (root == null || root.findViewWithTag(UNIVERSAL_CONTROL_ENTRY_TAG) != null) {
            return;
        }

        LinearLayout content = findScrollContent(root);
        if (content == null) {
            return;
        }

        Button entry = new Button(activity);
        entry.setTag(UNIVERSAL_CONTROL_ENTRY_TAG);
        entry.setAllCaps(false);
        entry.setText("🎮 全域控制盤（v0.1 測試）");
        entry.setTextColor(Color.WHITE);
        entry.setTextSize(16f);
        entry.setMinHeight(dp(activity, 52));
        entry.setBackgroundTintList(ColorStateList.valueOf(0xff19794b));
        entry.setContentDescription("開啟全域方向鍵、A B 鍵與游標設定");
        entry.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, UniversalControlSetupActivity.class)
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(activity, 18), 0, 0);
        content.addView(entry, params);
    }

    private LinearLayout findScrollContent(View view) {
        if (view instanceof ScrollView) {
            ScrollView scrollView = (ScrollView) view;
            if (scrollView.getChildCount() > 0 && scrollView.getChildAt(0) instanceof LinearLayout) {
                return (LinearLayout) scrollView.getChildAt(0);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index += 1) {
                LinearLayout result = findScrollContent(group.getChildAt(index));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
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

    private int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
