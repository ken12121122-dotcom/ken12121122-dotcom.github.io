package com.amin.pocketgba;

import org.json.JSONObject;

/** Process-local status exposed to the existing graph UI. */
final class GitHubWorkSyncState {
    private static volatile String status = "idle";
    private static volatile String revision = "";
    private static volatile String error = "";
    private static volatile long startedAt;
    private static volatile long finishedAt;

    private GitHubWorkSyncState() { }

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
        error = exception == null ? "UNKNOWN" : exception.getClass().getSimpleName();
        finishedAt = System.currentTimeMillis();
    }

    static JSONObject snapshot() {
        try {
            return new JSONObject()
                    .put("status", status)
                    .put("revision", revision)
                    .put("error", error)
                    .put("startedAt", startedAt)
                    .put("finishedAt", finishedAt)
                    .put("rawLogsEnabled", false);
        } catch (Exception error) {
            return new JSONObject();
        }
    }
}
