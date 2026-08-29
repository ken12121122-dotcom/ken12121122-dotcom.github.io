package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Persists review-only synchronized evidence for the single visual Graph projection. */
final class GraphEvidenceSyncStore {
    private static final String PREFS = "amin_graph_evidence_sync";
    private static final String STATE = "state_v1";
    private static final Object LOCK = new Object();

    private final SharedPreferences prefs;

    GraphEvidenceSyncStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONObject current() {
        synchronized (LOCK) {
            String raw = prefs.getString(STATE, "");
            if (raw == null || raw.trim().isEmpty()) return GraphSyncEngine.empty();
            try {
                JSONObject value = new JSONObject(raw);
                return GraphSyncEngine.FORMAT.equals(value.optString("format", ""))
                        ? value
                        : GraphSyncEngine.empty();
            } catch (Exception ignored) {
                return GraphSyncEngine.empty();
            }
        }
    }

    JSONObject sync(JSONObject canonicalEvidence, long observedAt) {
        synchronized (LOCK) {
            JSONObject next = GraphSyncEngine.sync(currentLocked(), canonicalEvidence, observedAt);
            prefs.edit().putString(STATE, next.toString()).commit();
            return next;
        }
    }

    void clear() {
        synchronized (LOCK) {
            prefs.edit().remove(STATE).commit();
        }
    }

    private JSONObject currentLocked() {
        String raw = prefs.getString(STATE, "");
        if (raw == null || raw.trim().isEmpty()) return GraphSyncEngine.empty();
        try {
            JSONObject value = new JSONObject(raw);
            return GraphSyncEngine.FORMAT.equals(value.optString("format", ""))
                    ? value
                    : GraphSyncEngine.empty();
        } catch (Exception ignored) {
            return GraphSyncEngine.empty();
        }
    }
}
