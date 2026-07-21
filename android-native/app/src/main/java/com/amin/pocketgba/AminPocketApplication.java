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
    private static final String CONTROL_API_ENTRY_TAG = "amin-control-api-entry";

    @Override
    public void onCreate() {
        super.onCreate();
        AminInputGateway.get(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
                activity.getWindow().getDecorView();
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity instanceof MainActivity) {
                    WebView webView = activity.findViewById(R.id.webView);
                    if (webView != null) {
                        webView.addJavascriptInterface(
                                new NativeSaveBridge(activity),
                                "AminNativeSaveVault"
                        );
                    }
                }
                if (activity instanceof ControlCenterActivity) {
                    View root = activity.findViewById(android.R.id.content);
                    rewritePreviewLabels(root);
                    scheduleControlEntries(activity);
                }
            }

            @Override public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                if (activity instanceof ControlCenterActivity) scheduleControlEntries(activity);
            }

            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private void scheduleControlEntries(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root != null) root.post(() -> attachControlEntries(activity, root));
    }

    private void attachControlEntries(Activity activity, View root) {
        if (activity.isFinishing() || root == null) return;
        LinearLayout content = findScrollContent(root);
        if (content == null) return;

        if (root.findViewWithTag(UNIVERSAL_CONTROL_ENTRY_TAG) == null) {
            Button entry = createEntryButton(
                    activity,
                    UNIVERSAL_CONTROL_ENTRY_TAG,
                    "🎮 開啟全域控制盤",
                    "開啟全域方向鍵、A B 鍵與游標設定",
                    0xff19794b
            );
            entry.setOnClickListener(view -> activity.startActivity(
                    new Intent(activity, UniversalControlSetupActivity.class)
            ));
            content.addView(entry, Math.min(4, content.getChildCount()), entryParams(activity));
        }

        if (root.findViewWithTag(CONTROL_API_ENTRY_TAG) == null) {
            Button entry = createEntryButton(
                    activity,
                    CONTROL_API_ENTRY_TAG,
                    "🌐 Amin Control API",
                    "開啟 localhost、LAN、WebSocket 與自動化控制設定",
                    0xff105f39
            );
            entry.setOnClickListener(view -> activity.startActivity(
                    new Intent(activity, AminControlApiActivity.class)
            ));
            content.addView(entry, Math.min(5, content.getChildCount()), entryParams(activity));
        }
    }

    private Button createEntryButton(
            Activity activity,
            String tag,
            String label,
            String description,
            int color
    ) {
        Button entry = new Button(activity);
        entry.setTag(tag);
        entry.setAllCaps(false);
        entry.setText(label);
        entry.setTextColor(Color.WHITE);
        entry.setTextSize(17f);
        entry.setMinHeight(dp(activity, 58));
        entry.setElevation(dp(activity, 5));
        entry.setBackgroundTintList(ColorStateList.valueOf(color));
        entry.setContentDescription(description);
        return entry;
    }

    private LinearLayout.LayoutParams entryParams(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(activity, 10), 0, dp(activity, 6));
        return params;
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
                if (result != null) return result;
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
