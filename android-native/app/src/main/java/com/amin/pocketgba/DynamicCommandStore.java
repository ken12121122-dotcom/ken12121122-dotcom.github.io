package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Approved user-created commands. Built-in VoiceCommandCatalog remains read-only. */
final class DynamicCommandStore {
    private static final String PREFS = "amin_dynamic_commands";
    private static final String KEY_REGISTERED = "registered";
    private final Context context;
    private final SharedPreferences prefs;

    DynamicCommandStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONArray registered() {
        try { return new JSONArray(prefs.getString(KEY_REGISTERED, "[]")); }
        catch (Exception error) { return new JSONArray(); }
    }

    JSONObject register(JSONObject command) {
        if (command == null) return null;
        String id = command.optString("id", "").trim();
        if (id.startsWith("command:")) id = id.substring("command:".length());
        if (id.isEmpty()) return null;
        try {
            JSONObject clean = new JSONObject(command.toString());
            clean.put("id", id);
            clean.put("status", "active");
            JSONArray source = registered();
            JSONArray out = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < source.length(); i++) {
                JSONObject current = source.optJSONObject(i);
                if (current == null) continue;
                if (id.equals(current.optString("id", ""))) { out.put(clean); replaced = true; }
                else out.put(current);
            }
            if (!replaced) out.put(clean);
            prefs.edit().putString(KEY_REGISTERED, out.toString()).apply();
            UnifiedGraphProvider.notifyChanged(context);
            return new JSONObject(clean.toString());
        } catch (Exception error) { return null; }
    }

    void remove(String id) {
        String wanted = id == null ? "" : id.trim();
        if (wanted.startsWith("command:")) wanted = wanted.substring("command:".length());
        if (wanted.isEmpty()) return;
        JSONArray source = registered();
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject current = source.optJSONObject(i);
            if (current != null && !wanted.equals(current.optString("id", ""))) out.put(current);
        }
        prefs.edit().putString(KEY_REGISTERED, out.toString()).apply();
        UnifiedGraphProvider.notifyChanged(context);
    }
}
