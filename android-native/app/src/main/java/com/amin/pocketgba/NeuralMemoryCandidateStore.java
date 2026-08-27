package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** POC-only storage for user-approved memory candidates. Never writes production memory. */
final class NeuralMemoryCandidateStore {
    private static final String PREFS = "neural_flow_memory_poc";
    private static final String KEY_APPROVED = "approved_candidates";
    private static final int MAX_ITEMS = 40;

    private NeuralMemoryCandidateStore() { }

    static void saveApproved(Context context, String turnId, String type, String summary,
                             String sourceText, String assistantReply) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray items;
        try { items = new JSONArray(prefs.getString(KEY_APPROVED, "[]")); }
        catch (Exception ignored) { items = new JSONArray(); }

        JSONObject item = new JSONObject();
        try {
            item.put("turn_id", turnId == null ? "" : turnId);
            item.put("type", type == null ? "other" : type);
            item.put("summary", summary == null ? "" : summary);
            item.put("source_text", sourceText == null ? "" : sourceText);
            item.put("assistant_reply", assistantReply == null ? "" : assistantReply);
            item.put("review_status", "approved");
            item.put("storage_scope", "neural_flow_poc_only");
            item.put("approved_at_ms", System.currentTimeMillis());
            items.put(item);
            while (items.length() > MAX_ITEMS) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < items.length(); i++) trimmed.put(items.get(i));
                items = trimmed;
            }
            prefs.edit().putString(KEY_APPROVED, items.toString()).apply();
        } catch (Exception ignored) { }
    }
}
