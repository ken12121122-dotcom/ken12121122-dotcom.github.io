package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Keeps accepted cloud Source Graph separate from pending review data. */
final class CloudSourceGraphStore {
    private static final String PREFS = "amin_cloud_source_graph";
    private static final String ACCEPTED_GRAPH = "accepted_graph";
    private static final String ACCEPTED_REVISION = "accepted_revision";
    private static final String PREVIOUS_GRAPH = "previous_graph";
    private static final String PREVIOUS_REVISION = "previous_revision";
    private static final String PENDING_GRAPH = "pending_graph";
    private static final String PENDING_REVISION = "pending_revision";

    private final SharedPreferences prefs;

    CloudSourceGraphStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONObject accepted() { return parse(prefs.getString(ACCEPTED_GRAPH, "")); }
    JSONObject previous() { return parse(prefs.getString(PREVIOUS_GRAPH, "")); }
    JSONObject pending() { return parse(prefs.getString(PENDING_GRAPH, "")); }
    String acceptedRevision() { return clean(prefs.getString(ACCEPTED_REVISION, "")); }
    String previousRevision() { return clean(prefs.getString(PREVIOUS_REVISION, "")); }
    String pendingRevision() { return clean(prefs.getString(PENDING_REVISION, "")); }
    boolean hasPending() { return pending() != null && !pendingRevision().isEmpty(); }

    void stage(String revision, JSONObject graph) {
        String next = clean(revision);
        if (graph == null || next.isEmpty() || next.equals(acceptedRevision()) || next.equals(pendingRevision())) return;
        prefs.edit().putString(PENDING_REVISION, next).putString(PENDING_GRAPH, graph.toString()).apply();
    }

    boolean acceptPending(String expectedRevision) {
        JSONObject graph = pending();
        String revision = pendingRevision();
        String expected = clean(expectedRevision);
        if (graph == null || revision.isEmpty() || expected.isEmpty() || !revision.equals(expected)) return false;

        SharedPreferences.Editor edit = prefs.edit();
        JSONObject currentAccepted = accepted();
        String currentRevision = acceptedRevision();
        if (currentAccepted != null && !currentRevision.isEmpty()) {
            edit.putString(PREVIOUS_REVISION, currentRevision)
                    .putString(PREVIOUS_GRAPH, currentAccepted.toString());
        }
        edit.putString(ACCEPTED_REVISION, revision)
                .putString(ACCEPTED_GRAPH, graph.toString())
                .remove(PENDING_REVISION)
                .remove(PENDING_GRAPH)
                .apply();
        return true;
    }

    boolean rollbackAccepted() {
        JSONObject graph = previous();
        String revision = previousRevision();
        if (graph == null || revision.isEmpty()) return false;
        JSONObject current = accepted();
        String currentRevision = acceptedRevision();
        SharedPreferences.Editor edit = prefs.edit()
                .putString(ACCEPTED_REVISION, revision)
                .putString(ACCEPTED_GRAPH, graph.toString());
        if (current != null && !currentRevision.isEmpty()) {
            edit.putString(PREVIOUS_REVISION, currentRevision)
                    .putString(PREVIOUS_GRAPH, current.toString());
        }
        edit.apply();
        return true;
    }

    JSONObject reviewState() {
        try {
            JSONObject pendingValue = pending();
            return new JSONObject()
                    .put("hasPending", pendingValue != null && !pendingRevision().isEmpty())
                    .put("acceptedRevision", acceptedRevision())
                    .put("previousRevision", previousRevision())
                    .put("pendingRevision", pendingRevision())
                    .put("pendingGraph", pendingValue == null ? JSONObject.NULL : pendingValue);
        } catch (Exception ignored) { return new JSONObject(); }
    }

    private static JSONObject parse(String raw) {
        try {
            String value = clean(raw);
            return value.isEmpty() ? null : new JSONObject(value);
        } catch (Exception ignored) { return null; }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
