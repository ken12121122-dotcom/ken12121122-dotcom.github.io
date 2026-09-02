package com.amin.pocketgba;

import android.content.Context;

final class FoxPetState {
    private static final String PREFS = "amin_fox_pet";
    private static final String KEY_ENABLED = "enabled";

    private FoxPetState() {}

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }
}
