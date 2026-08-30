package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Durable, sanitized Phase 1 sync state exposed to the existing graph UI. */
final class GitHubWorkSyncState {
    private static final String PREFS = "amin_github_work_sync_status";
    private static final String KEY = "status_v2";
    private static final long AUTO_REFRESH_MS = 30L * 60L * 1000L;
    private static final int MINIMUM_SAFE_REQUEST_BUDGET = 20;

    private GitHubWorkSyncState() { }

    static synchronized void syncing(Context context) {
        JSONObject state = snapshot(context);
        try {
            state.put("status", "syncing")
                    .put("error", "")
                    .put("startedAt", System.currentTimeMillis());
            save(context, state);
        } catch (Exception ignored) { }
    }

    static synchronized void ready(Context context, JSONObject observation) {
        JSONObject metadata = observation == null ? null : observation.optJSONObject("sync_metadata");
        try {
            JSONObject state = base()
                    .put("status", "ready")
                    .put("revision", observation == null ? "" : observation.optString("revision", ""))
                    .put("error", "")
                    .put("startedAt", snapshot(context).optLong("startedAt", 0L))
                    .put("finishedAt", System.currentTimeMillis())
                    .put("requestCount", metadata == null ? 0 : metadata.optInt("request_count", 0))
                    .put("rateLimit", metadata == null ? -1 : metadata.optInt("rate_limit", -1))
                    .put("rateRemaining", metadata == null ? -1 : metadata.optInt("rate_remaining", -1))
                    .put("rateResetAt", metadata == null ? 0L : metadata.optLong("rate_reset_at", 0L))
                    .put("rateResource", metadata == null ? "" : metadata.optString("rate_resource", ""))
                    .put("partialPartitions", metadata == null || metadata.optJSONArray("partial_partitions") == null
                            ? new JSONArray() : new JSONArray(metadata.optJSONArray("partial_partitions").toString()));
            save(context, state);
        } catch (Exception ignored) { }
    }

    static synchronized void failed(Context context, Exception exception, JSONObject rateLimit) {
        JSONObject state = snapshot(context);
        try {
            state.put("status", "failed")
                    .put("error", errorCode(exception))
                    .put("finishedAt", System.currentTimeMillis());
            if (rateLimit != null) {
                state.put("requestCount", rateLimit.optInt("request_count", state.optInt("requestCount", 0)))
                        .put("rateLimit", rateLimit.optInt("rate_limit", state.optInt("rateLimit", -1)))
                        .put("rateRemaining", rateLimit.optInt("rate_remaining", state.optInt("rateRemaining", -1)))
                        .put("rateResetAt", rateLimit.optLong("rate_reset_at", state.optLong("rateResetAt", 0L)))
                        .put("rateResource", rateLimit.optString("rate_resource", state.optString("rateResource", "")));
            }
            save(context, state);
        } catch (Exception ignored) { }
    }

    static synchronized JSONObject snapshot(Context context) {
        if (context == null) return base();
        try {
            String raw = preferences(context).getString(KEY, "");
            if (raw == null || raw.trim().isEmpty()) return base();
            JSONObject state = new JSONObject(raw);
            if (state.optInt("version", -1) != 2) return base();
            return state;
        } catch (Exception error) {
            return base();
        }
    }

    static boolean shouldAutoRefresh(Context context, long now) {
        JSONObject state = snapshot(context);
        if ("syncing".equals(state.optString("status", ""))) return false;
        long finished = state.optLong("finishedAt", 0L);
        return canRefresh(context, now) && (finished <= 0L || now - finished >= AUTO_REFRESH_MS);
    }

    static boolean canRefresh(Context context, long now) {
        JSONObject state = snapshot(context);
        int remaining = state.optInt("rateRemaining", -1);
        long resetAtMillis = state.optLong("rateResetAt", 0L) * 1000L;
        return remaining < 0 || remaining >= MINIMUM_SAFE_REQUEST_BUDGET || resetAtMillis <= now;
    }

    private static JSONObject base() {
        try {
            return new JSONObject()
                    .put("format", "amin-github-work-sync-status")
                    .put("version", 2)
                    .put("status", "idle")
                    .put("revision", "")
                    .put("error", "")
                    .put("startedAt", 0L)
                    .put("finishedAt", 0L)
                    .put("requestCount", 0)
                    .put("rateLimit", -1)
                    .put("rateRemaining", -1)
                    .put("rateResetAt", 0L)
                    .put("rateResource", "")
                    .put("partialPartitions", new JSONArray())
                    .put("readOnly", true)
                    .put("authenticated", false)
                    .put("rawLogsEnabled", false);
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static String errorCode(Exception exception) {
        if (exception == null) return "UNKNOWN";
        String message = exception.getMessage();
        if (message != null && message.matches("[A-Z0-9_]+")) return message;
        return exception.getClass().getSimpleName();
    }

    private static void save(Context context, JSONObject state) {
        if (context == null || state == null) return;
        preferences(context).edit().putString(KEY, state.toString()).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
