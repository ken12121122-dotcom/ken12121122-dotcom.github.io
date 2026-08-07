package com.amin.pocketgba;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

final class AminTheme {
    static final String CLEAN_LIGHT = "clean_light";
    static final String SOFT_GREEN = "soft_green";
    static final String DARK = "dark";
    static final String MINIMAL = "minimal";
    static final String PRESERVE_TAG = "amin-theme-preserve";
    private static final String PREFS = "amin_global_theme";
    private static final String KEY = "theme";

    private AminTheme() {}

    static String current(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SOFT_GREEN);
    }

    static void set(Context context, String theme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, valid(theme) ? theme : SOFT_GREEN).apply();
    }

    static void applyBeforeCreate(Activity activity) {
        if (activity instanceof PromptUnlockActivity || activity instanceof PromptCaptureActivity) return;
        switch (current(activity)) {
            case CLEAN_LIGHT: activity.setTheme(R.style.Theme_Amin_CleanLight); break;
            case DARK: activity.setTheme(R.style.Theme_Amin_Dark); break;
            case MINIMAL: activity.setTheme(R.style.Theme_Amin_Minimal); break;
            default: activity.setTheme(R.style.Theme_Amin_SoftGreen); break;
        }
    }

    static Palette palette(Context context) {
        switch (current(context)) {
            case CLEAN_LIGHT: return new Palette(0xfffafbfa, 0xffffffff, 0xff176b45, 0xff16231b, 0xff68766e, 0xffdfe5e1, 0xfff0f3f1, 0xffb3261e);
            case DARK: return new Palette(0xff0d1110, 0xff171d19, 0xff5ee3b2, 0xffeef4f0, 0xff9caaa2, 0xff2c3931, 0xff202a24, 0xffff6b63);
            case MINIMAL: return new Palette(0xffffffff, 0xffffffff, 0xff1f6f4a, 0xff111111, 0xff707070, 0xffe8e8e8, 0xffffffff, 0xffb3261e);
            default: return new Palette(0xfff3f8f5, 0xffffffff, 0xff19794b, 0xff16231b, 0xff68766e, 0xffd7e7de, 0xffe9f3ed, 0xffb3261e);
        }
    }

    static void applyToViewTree(Context context, View view) {
        if (view == null || view instanceof WebView) return;
        if (PRESERVE_TAG.equals(view.getTag())) return;
        Palette p = palette(context);
        if (view instanceof EditText) {
            EditText e = (EditText) view;
            e.setTextColor(p.text);
            e.setHintTextColor(p.muted);
            e.setBackgroundTintList(ColorStateList.valueOf(p.primary));
        } else if (view instanceof Button) {
            Button b = (Button) view;
            b.setTextColor(p.primary);
            b.setBackgroundTintList(ColorStateList.valueOf(p.surfaceAlt));
        } else if (view instanceof TextView) {
            TextView t = (TextView) view;
            if (t.getCurrentTextColor() != Color.TRANSPARENT) t.setTextColor(p.text);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyToViewTree(context, group.getChildAt(i));
        }
    }

    static boolean valid(String value) {
        return CLEAN_LIGHT.equals(value) || SOFT_GREEN.equals(value) || DARK.equals(value) || MINIMAL.equals(value);
    }

    static final class Palette {
        final int background, surface, primary, text, muted, border, surfaceAlt, danger;
        Palette(int background, int surface, int primary, int text, int muted, int border, int surfaceAlt, int danger) {
            this.background = background; this.surface = surface; this.primary = primary; this.text = text;
            this.muted = muted; this.border = border; this.surfaceAlt = surfaceAlt; this.danger = danger;
        }
    }
}
