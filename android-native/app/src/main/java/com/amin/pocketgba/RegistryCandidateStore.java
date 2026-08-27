package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/** Pending registration candidates. Nothing becomes registered until approve(...) is called. */
final class RegistryCandidateStore {
    private static final String PREFS = "amin_registry_candidates";
    private static final String KEY_PENDING = "pending";
    private final Context context;
    private final SharedPreferences prefs;

    RegistryCandidateStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONObject createNodeCandidate(String requestedName, String sourceText) {
        try {
            String title = clean(requestedName);
            if (title.isEmpty()) title = "新節點";
            JSONObject candidate = new JSONObject()
                    .put("candidate_id", "candidate:" + UUID.randomUUID().toString().substring(0, 8))
                    .put("entity_type", "node")
                    .put("title", title)
                    .put("source_text", clean(sourceText))
                    .put("review_status", "generated")
                    .put("created_at", System.currentTimeMillis());
            save(candidate);
            return candidate;
        } catch (Exception error) { return null; }
    }

    JSONObject createCommandCandidate(String id, String title, String description, String action, JSONArray phrases) {
        try {
            JSONObject candidate = new JSONObject()
                    .put("candidate_id", "candidate:" + UUID.randomUUID().toString().substring(0, 8))
                    .put("entity_type", "command")
                    .put("command_id", clean(id))
                    .put("title", clean(title))
                    .put("description", clean(description))
                    .put("action", clean(action))
                    .put("phrases", phrases == null ? new JSONArray() : phrases)
                    .put("review_status", "generated")
                    .put("created_at", System.currentTimeMillis());
            save(candidate);
            return candidate;
        } catch (Exception error) { return null; }
    }

    JSONObject approve(JSONObject candidate, NodeMetadataStore nodeStore) {
        if (candidate == null) return null;
        String type = candidate.optString("entity_type", "");
        JSONObject registered = null;
        if ("node".equals(type)) {
            registered = nodeStore.createCustomNode(candidate.optString("title", ""), candidate.optString("source_text", ""));
        } else if ("command".equals(type)) {
            try {
                JSONObject command = new JSONObject()
                        .put("id", candidate.optString("command_id", ""))
                        .put("title", candidate.optString("title", ""))
                        .put("description", candidate.optString("description", ""))
                        .put("action", candidate.optString("action", ""))
                        .put("phrases", candidate.optJSONArray("phrases") == null ? new JSONArray() : candidate.optJSONArray("phrases"));
                registered = new DynamicCommandStore(context).register(command);
            } catch (Exception ignored) { }
        }
        if (registered != null) {
            remove(candidate.optString("candidate_id", ""));
            UnifiedGraphProvider.notifyChanged(context);
        }
        return registered;
    }

    void reject(JSONObject candidate) {
        if (candidate == null) return;
        remove(candidate.optString("candidate_id", ""));
    }

    JSONArray pending() {
        try { return new JSONArray(prefs.getString(KEY_PENDING, "[]")); }
        catch (Exception error) { return new JSONArray(); }
    }

    private void save(JSONObject candidate) {
        if (candidate == null) return;
        JSONArray source = pending();
        source.put(candidate);
        prefs.edit().putString(KEY_PENDING, source.toString()).apply();
    }

    private void remove(String candidateId) {
        String wanted = clean(candidateId);
        JSONArray source = pending();
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject current = source.optJSONObject(i);
            if (current != null && !wanted.equals(current.optString("candidate_id", ""))) out.put(current);
        }
        prefs.edit().putString(KEY_PENDING, out.toString()).apply();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
