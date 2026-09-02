package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Persists contract-valid GitHub Work observations; raw provider responses are not retained. */
final class GitHubWorkSyncStore {
    private static final String PREFS = "amin_github_work_sync";
    private static final String STATE = "state_v1";
    private static final Object LOCK = new Object();

    private final SharedPreferences preferences;

    GitHubWorkSyncStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONObject current() {
        synchronized (LOCK) { return currentLocked(); }
    }

    JSONObject sync(JSONArray batches, long observedAt) throws Exception {
        synchronized (LOCK) {
            JSONObject next = currentLocked();
            if (batches == null) return next;
            for (int i = 0; i < batches.length(); i++) {
                JSONObject batch = batches.optJSONObject(i);
                JSONObject result = SharedGraphSyncKernel.apply(next, batch, observedAt);
                if (!result.optBoolean("success", false)) {
                    throw new IllegalStateException("GITHUB_WORK_SYNC_" + result.optString("errorCode", "FAILED"));
                }
                next = result.optJSONObject("state");
                if (next == null) throw new IllegalStateException("GITHUB_WORK_STATE_MISSING");
            }
            if (!preferences.edit().putString(STATE, next.toString()).commit()) {
                throw new IllegalStateException("GITHUB_WORK_STATE_NOT_SAVED");
            }
            return new JSONObject(next.toString());
        }
    }

    private JSONObject currentLocked() {
        String raw = preferences.getString(STATE, "");
        if (raw == null || raw.trim().isEmpty()) return SharedGraphSyncKernel.emptyState();
        try {
            JSONObject state = new JSONObject(raw);
            if (!SharedGraphSyncKernel.STATE_FORMAT.equals(state.optString("format", ""))
                    || state.optInt("version", -1) != SharedGraphSyncKernel.VERSION
                    || state.optJSONArray("entities") == null
                    || state.optJSONArray("relations") == null) {
                return SharedGraphSyncKernel.emptyState();
            }
            return state;
        } catch (Exception error) {
            return SharedGraphSyncKernel.emptyState();
        }
    }
}
