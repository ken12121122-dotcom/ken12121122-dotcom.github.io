package com.amin.pocketgba;

import org.json.JSONObject;

/** Process-local observable state for the visible GitHub Source bootstrap gate. */
final class GitHubSourceSyncState {
    private static volatile String status = "idle";
    private static volatile String revision = "";
    private static volatile String error = "";
    private static volatile long startedAt = 0L;
    private static volatile long finishedAt = 0L;

    private GitHubSourceSyncState() { }

    static synchronized void syncing() {
        status = "syncing";
        revision = "";
        error = "";
        startedAt = System.currentTimeMillis();
        finishedAt = 0L;
    }

    static synchronized void ready(String nextRevision) {
        status = "ready";
        revision = nextRevision == null ? "" : nextRevision.trim();
        error = "";
        finishedAt = System.currentTimeMillis();
    }

    static synchronized void failed(Exception exception) {
        status = "failed";
        error = exception == null ? "UNKNOWN" : exception.getClass().getSimpleName() + ":" + String.valueOf(exception.getMessage());
        finishedAt = System.currentTimeMillis();
    }

    static JSONObject snapshot() {
        try {
            return new JSONObject()
                    .put("status", status)
                    .put("revision", revision)
                    .put("error", error)
                    .put("startedAt", startedAt)
                    .put("finishedAt", finishedAt);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
