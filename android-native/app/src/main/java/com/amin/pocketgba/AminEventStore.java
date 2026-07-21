package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AminEventStore {
    public interface Listener { void onEvent(JSONObject event); }

    private static final String PREFS = "amin_control_event_store_v1";
    private static final String KEY_EVENTS = "events";
    private static final int MAX_EVENTS = 200;

    private final SharedPreferences preferences;
    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public AminEventStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public JSONObject recordExecution(ExecutionResult result) {
        return append("execution_result", result.toJsonObject());
    }

    public JSONObject recordAudit(String type, JSONObject details) {
        return append(type == null || type.isBlank() ? "audit" : type,
                details == null ? new JSONObject() : details);
    }

    public JSONObject append(String type, JSONObject payload) {
        JSONObject event = new JSONObject();
        try {
            event.put("eventId", UUID.randomUUID().toString());
            event.put("type", type);
            event.put("timestamp", Instant.now().toString());
            event.put("payload", payload == null ? new JSONObject() : payload);
        } catch (JSONException error) {
            throw new IllegalStateException("Unable to create event", error);
        }
        synchronized (lock) {
            JSONArray existing = readArrayLocked();
            JSONArray next = new JSONArray();
            int start = Math.max(0, existing.length() - (MAX_EVENTS - 1));
            for (int index = start; index < existing.length(); index += 1) {
                Object value = existing.opt(index);
                if (value != null) next.put(value);
            }
            next.put(event);
            preferences.edit().putString(KEY_EVENTS, next.toString()).apply();
        }
        for (Listener listener : listeners) {
            try { listener.onEvent(event); } catch (RuntimeException ignored) { }
        }
        return event;
    }

    public JSONArray getRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(MAX_EVENTS, limit));
        synchronized (lock) {
            JSONArray existing = readArrayLocked();
            JSONArray result = new JSONArray();
            int start = Math.max(0, existing.length() - safeLimit);
            for (int index = start; index < existing.length(); index += 1) {
                Object value = existing.opt(index);
                if (value != null) result.put(value);
            }
            return result;
        }
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) { listeners.remove(listener); }

    private JSONArray readArrayLocked() {
        String raw = preferences.getString(KEY_EVENTS, "[]");
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (JSONException error) {
            preferences.edit().remove(KEY_EVENTS).apply();
            return new JSONArray();
        }
    }
}
