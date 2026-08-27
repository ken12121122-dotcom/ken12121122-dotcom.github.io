package com.amin.pocketgba;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Isolated POC injector for the Neural Flow entry card.
 *
 * It avoids modifying the existing ControlCenterActivity source. Removing this
 * provider and its manifest entry fully rolls back the POC entry point.
 */
public final class NeuralFlowEntryProvider extends ContentProvider {
    private static final int ENTRY_ID = 0x4e46504f; // "NFPO"

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;
        Application application = (Application) getContext().getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof ControlCenterActivity) inject(activity);
            }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
        return true;
    }

    private void inject(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        View existing = root.findViewById(ENTRY_ID);
        if (existing != null) return;
        LinearLayout content = findPrimaryVerticalLayout((ViewGroup) root);
        if (content == null) return;

        LinearLayout card = new LinearLayout(activity);
        card.setId(ENTRY_ID);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 14), dp(activity, 16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setElevation(dp(activity, 2));
        card.setBackground(round(activity, 0xffffffff, 20, 0xffcfded5));
        card.setOnClickListener(v -> activity.startActivity(new Intent(activity, NeuralFlowActivity.class)));

        TextView icon = text(activity, "⚡", 25f, false, 0xff19794b);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        copyParams.leftMargin = dp(activity, 12);
        card.addView(copy, copyParams);

        copy.addView(text(activity, "Neural Flow", 17f, true, 0xff16231b), new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(
                activity,
                "觀察訊號經過 Router、LLM 與未來 Skill 的即時流動",
                13f,
                false,
                0xff68766e
        );
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(activity, 4);
        copy.addView(description, descriptionParams);

        TextView action = text(activity, "觀察  ›", 13f, true, 0xff19794b);
        action.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.addView(action, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 10);

        // Put it after the principal home actions and before status/management blocks.
        int targetIndex = Math.min(7, content.getChildCount());
        content.addView(card, targetIndex, params);
    }

    private LinearLayout findPrimaryVerticalLayout(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getOrientation() == LinearLayout.VERTICAL && layout.getChildCount() >= 4) {
                    return layout;
                }
            }
            if (child instanceof ViewGroup) {
                LinearLayout nested = findPrimaryVerticalLayout((ViewGroup) child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static TextView text(Activity activity, String value, float size, boolean bold, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.25f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static GradientDrawable round(Activity activity, int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(activity, radiusDp));
        drawable.setStroke(dp(activity, 1), stroke);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
