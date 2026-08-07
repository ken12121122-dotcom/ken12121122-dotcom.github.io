package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

final class AppNavigationCandidateStore {
    private static final String PREFS = "amin_app_navigation_candidate";
    private static final String KEY_JSON = "candidate_json";
    private final SharedPreferences prefs;

    AppNavigationCandidateStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String getJson() {
        return prefs.getString(KEY_JSON, "{}");
    }

    void saveJson(String json) {
        prefs.edit().putString(KEY_JSON, json == null || json.trim().isEmpty() ? "{}" : json).apply();
    }

    void clear() {
        prefs.edit().remove(KEY_JSON).apply();
    }
}
