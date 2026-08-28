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
    private static final String BUNDLED_SENTINEL = "__BUNDLED__";
    private static final Object LOCK = new Object();

    private final SharedPreferences prefs;

    CloudSourceGraphStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONObject accepted() { synchronized (LOCK) { return parse(prefs.getString(ACCEPTED_GRAPH, "")); } }
    JSONObject previous() { synchronized (LOCK) { return parse(prefs.getString(PREVIOUS_GRAPH, "")); } }
    JSONObject pending() { synchronized (LOCK) { return parse(prefs.getString(PENDING_GRAPH, "")); } }
    String acceptedRevision() { synchronized (LOCK) { return clean(prefs.getString(ACCEPTED_REVISION, "")); } }
    String previousRevision() { synchronized (LOCK) { return clean(prefs.getString(PREVIOUS_REVISION, "")); } }
    String pendingRevision() { synchronized (LOCK) { return clean(prefs.getString(PENDING_REVISION, "")); } }
    boolean hasPending() { synchronized (LOCK) { return pendingLocked() != null && !pendingRevisionLocked().isEmpty(); } }

    void stage(String revision, JSONObject graph) {
        String next = clean(revision);
        if (graph == null || next.isEmpty()) return;
        synchronized (LOCK) {
            JSONObject staged;
            try {
                staged = new JSONObject(graph.toString()).put("revision", next);
            } catch (Exception error) { return; }

            JSONObject currentPending = pendingLocked();
            JSONObject currentAccepted = acceptedLocked();
            if (sameSnapshot(currentPending, staged) || sameSnapshot(currentAccepted, staged)) return;

            prefs.edit()
                    .putString(PENDING_REVISION, next)
                    .putString(PENDING_GRAPH, staged.toString())
                    .commit();
        }
    }

    boolean acceptPending(String expectedRevision) {
        String expected = clean(expectedRevision);
        if (expected.isEmpty()) return false;
        synchronized (LOCK) {
            JSONObject graph = pendingLocked();
            String revision = pendingRevisionLocked();
            if (graph == null || revision.isEmpty() || !revision.equals(expected)) return false;
            if (!revision.equals(clean(graph.optString("revision", "")))) return false;

            SharedPreferences.Editor edit = prefs.edit();
            JSONObject currentAccepted = acceptedLocked();
            String currentRevision = acceptedRevisionLocked();
            if (currentAccepted != null && !currentRevision.isEmpty()) {
                edit.putString(PREVIOUS_REVISION, currentRevision)
                        .putString(PREVIOUS_GRAPH, currentAccepted.toString());
            } else {
                edit.putString(PREVIOUS_REVISION, BUNDLED_SENTINEL)
                        .remove(PREVIOUS_GRAPH);
            }
            boolean ok = edit.putString(ACCEPTED_REVISION, revision)
                    .putString(ACCEPTED_GRAPH, graph.toString())
                    .remove(PENDING_REVISION)
                    .remove(PENDING_GRAPH)
                    .commit();
            return ok;
        }
    }

    boolean rollbackAccepted() {
        synchronized (LOCK) {
            String revision = previousRevisionLocked();
            if (revision.isEmpty()) return false;
            JSONObject current = acceptedLocked();
            String currentRevision = acceptedRevisionLocked();
            SharedPreferences.Editor edit = prefs.edit();

            if (BUNDLED_SENTINEL.equals(revision)) {
                edit.remove(ACCEPTED_REVISION).remove(ACCEPTED_GRAPH);
            } else {
                JSONObject graph = previousLocked();
                if (graph == null) return false;
                edit.putString(ACCEPTED_REVISION, revision)
                        .putString(ACCEPTED_GRAPH, graph.toString());
            }

            if (current != null && !currentRevision.isEmpty()) {
                edit.putString(PREVIOUS_REVISION, currentRevision)
                        .putString(PREVIOUS_GRAPH, current.toString());
            } else {
                edit.remove(PREVIOUS_REVISION).remove(PREVIOUS_GRAPH);
            }
            return edit.commit();
        }
    }

    JSONObject reviewState() {
        synchronized (LOCK) {
            try {
                JSONObject pendingValue = pendingLocked();
                String pendingRevision = pendingRevisionLocked();
                if (pendingValue != null && !pendingRevision.equals(clean(pendingValue.optString("revision", "")))) {
                    return new JSONObject()
                            .put("hasPending", false)
                            .put("reviewError", "PENDING_REVISION_GRAPH_MISMATCH")
                            .put("acceptedRevision", acceptedRevisionLocked())
                            .put("previousRevision", previousRevisionLocked())
                            .put("pendingRevision", pendingRevision)
                            .put("pendingGraph", JSONObject.NULL);
                }
                return new JSONObject()
                        .put("hasPending", pendingValue != null && !pendingRevision.isEmpty())
                        .put("acceptedRevision", acceptedRevisionLocked())
                        .put("previousRevision", previousRevisionLocked())
                        .put("pendingRevision", pendingRevision)
                        .put("pendingGraph", pendingValue == null ? JSONObject.NULL : new JSONObject(pendingValue.toString()));
            } catch (Exception ignored) { return new JSONObject(); }
        }
    }

    private boolean sameSnapshot(JSONObject left, JSONObject right) {
        if (left == null || right == null) return false;
        String leftRevision = clean(left.optString("revision", ""));
        String rightRevision = clean(right.optString("revision", ""));
        String leftMode = clean(left.optString("scanMode", ""));
        String rightMode = clean(right.optString("scanMode", ""));
        String leftAnchor = clean(left.optString("anchorId", ""));
        String rightAnchor = clean(right.optString("anchorId", ""));
        int leftReverseVersion = reverseVersion(left);
        int rightReverseVersion = reverseVersion(right);
        return leftRevision.equals(rightRevision)
                && leftMode.equals(rightMode)
                && leftAnchor.equals(rightAnchor)
                && leftReverseVersion == rightReverseVersion;
    }

    private int reverseVersion(JSONObject graph) {
        JSONObject reverse = graph == null ? null : graph.optJSONObject("reverseDiscovery");
        return reverse == null ? 0 : reverse.optInt("version", 0);
    }

    private JSONObject acceptedLocked() { return parse(prefs.getString(ACCEPTED_GRAPH, "")); }
    private JSONObject previousLocked() { return parse(prefs.getString(PREVIOUS_GRAPH, "")); }
    private JSONObject pendingLocked() { return parse(prefs.getString(PENDING_GRAPH, "")); }
    private String acceptedRevisionLocked() { return clean(prefs.getString(ACCEPTED_REVISION, "")); }
    private String previousRevisionLocked() { return clean(prefs.getString(PREVIOUS_REVISION, "")); }
    private String pendingRevisionLocked() { return clean(prefs.getString(PENDING_REVISION, "")); }

    private static JSONObject parse(String raw) {
        try {
            String value = clean(raw);
            return value.isEmpty() ? null : new JSONObject(value);
        } catch (Exception ignored) { return null; }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
