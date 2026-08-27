package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Minimal conversation-to-node proof store.
 *
 * This is intentionally not the final long-term memory schema. It only proves that
 * a successful LLM conversation can persist a node and that the node map can render it.
 */
final class MemoryNodeStore {
    static final class MemoryNode {
        final String id;
        final String label;
        final String userText;
        final String assistantText;
        final String createdAt;

        MemoryNode(String id, String label, String userText, String assistantText, String createdAt) {
            this.id = id;
            this.label = label;
            this.userText = userText;
            this.assistantText = assistantText;
            this.createdAt = createdAt;
        }
    }

    private static final String PREFS = "amin_memory_node_poc_v1";
    private static final String KEY_NODES = "nodes";
    private static final int MAX_NODES = 40;

    private final SharedPreferences preferences;

    MemoryNodeStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void recordConversation(String userText, String assistantText) {
        String user = userText == null ? "" : userText.trim();
        String assistant = assistantText == null ? "" : assistantText.trim();
        if (user.isEmpty()) return;
        if ("請只回答 OK".equals(user)) return; // LLM connection test must not pollute the graph.

        JSONArray existing = readArray();
        JSONArray next = new JSONArray();
        int start = Math.max(0, existing.length() - (MAX_NODES - 1));
        for (int i = start; i < existing.length(); i++) {
            Object value = existing.opt(i);
            if (value != null) next.put(value);
        }

        JSONObject node = new JSONObject();
        try {
            node.put("id", UUID.randomUUID().toString());
            node.put("label", makeLabel(user));
            node.put("userText", user);
            node.put("assistantText", assistant);
            node.put("createdAt", Instant.now().toString());
            next.put(node);
            preferences.edit().putString(KEY_NODES, next.toString()).apply();
        } catch (Exception ignored) {
            // This POC must never break the chat path if node persistence fails.
        }
    }

    synchronized List<MemoryNode> getRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(MAX_NODES, limit));
        JSONArray array = readArray();
        int start = Math.max(0, array.length() - safeLimit);
        ArrayList<MemoryNode> result = new ArrayList<>();
        for (int i = start; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            result.add(new MemoryNode(
                    item.optString("id", ""),
                    item.optString("label", "對話"),
                    item.optString("userText", ""),
                    item.optString("assistantText", ""),
                    item.optString("createdAt", "")
            ));
        }
        return result;
    }

    synchronized int count() {
        return readArray().length();
    }

    synchronized void clear() {
        preferences.edit().remove(KEY_NODES).apply();
    }

    private JSONArray readArray() {
        String raw = preferences.getString(KEY_NODES, "[]");
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    private static String makeLabel(String text) {
        String compact = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (compact.length() <= 10) return compact;
        return compact.substring(0, 10) + "…";
    }
}
