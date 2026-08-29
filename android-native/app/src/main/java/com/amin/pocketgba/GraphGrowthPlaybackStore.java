package com.amin.pocketgba;

import org.json.JSONObject;

/** In-memory playback projection. Never persists or becomes a second graph authority. */
final class GraphGrowthPlaybackStore {
    private static final Object LOCK = new Object();
    private static JSONObject playback;

    private GraphGrowthPlaybackStore() { }

    static void set(JSONObject state) {
        synchronized (LOCK) {
            playback = cloneJson(state);
        }
    }

    static void clear() {
        synchronized (LOCK) {
            playback = null;
        }
    }

    static boolean isActive() {
        synchronized (LOCK) {
            return playback != null;
        }
    }

    static JSONObject currentOr(JSONObject fallback) {
        synchronized (LOCK) {
            return playback == null ? cloneJson(fallback) : cloneJson(playback);
        }
    }

    private static JSONObject cloneJson(JSONObject value) {
        if (value == null) return GraphSyncEngine.empty();
        try { return new JSONObject(value.toString()); }
        catch (Exception ignored) { return GraphSyncEngine.empty(); }
    }
}
