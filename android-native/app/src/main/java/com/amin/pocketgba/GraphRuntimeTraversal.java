package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** Minimal runtime traversal: registered edge -> shared Gate -> trace -> target node. */
final class GraphRuntimeTraversal {
    private GraphRuntimeTraversal() { }

    static String traverse(Context context, NodeMetadataStore nodeStore, String edgeId, String turnId) {
        String requested = clean(edgeId);
        String turn = clean(turnId);
        if (turn.isEmpty()) turn = "turn-" + System.currentTimeMillis();
        try {
            JSONObject root = new JSONObject(NodeRegistry.typedEdgesJson(context, nodeStore));
            JSONArray edges = root.optJSONArray("edges");
            if (edges == null) edges = new JSONArray();
            JSONObject edge = null;
            for (int i = 0; i < edges.length(); i++) {
                JSONObject candidate = edges.optJSONObject(i);
                if (candidate == null) continue;
                String id = first(candidate, "edge_id", "edgeId");
                if (requested.equals(id)) { edge = candidate; break; }
            }
            if (edge == null) return result(requested, "", "", "not_registered", "missing", turn, false);

            String from = first(edge, "from", "source", "source_node_id", "sourceNodeId");
            String to = first(edge, "to", "target", "target_node_id", "targetNodeId");
            String status = clean(edge.optString("status", "active"));
            if (!status.isEmpty() && !"active".equalsIgnoreCase(status)) {
                GraphRuntimeEdgeTrace.mark(requested, from, to, "blocked", turn);
                return result(requested, from, to, "blocked", "edge_inactive", turn, false);
            }

            JSONObject gate = edge.optJSONObject("gate");
            boolean enabled = gate == null || gate.optBoolean("enabled", true);
            GraphRuntimeEdgeTrace.mark(requested, from, to, "candidate", turn);
            if (!enabled) {
                GraphRuntimeEdgeTrace.mark(requested, from, to, "blocked", turn);
                return result(requested, from, to, "blocked", "gate_blocked", turn, false);
            }

            // Traversal is legal only across a registered edge. The Gate above is the same persisted
            // Gate object exposed by UnifiedGraphProvider, so UI and Runtime share one source of truth.
            GraphRuntimeEdgeTrace.mark(requested, from, to, "traversed", turn);
            return result(requested, from, to, "traversed", "pass", turn, true);
        } catch (Exception error) {
            return result(requested, "", "", "failed", "runtime_error", turn, false);
        }
    }

    private static String result(String edgeId, String from, String to, String status, String gateStatus, String turnId, boolean traversed) {
        try {
            return new JSONObject()
                    .put("edge_id", clean(edgeId))
                    .put("from", clean(from))
                    .put("to", clean(to))
                    .put("status", clean(status))
                    .put("gate_status", clean(gateStatus))
                    .put("turn_id", clean(turnId))
                    .put("traversed", traversed)
                    .put("timestamp", System.currentTimeMillis())
                    .toString();
        } catch (Exception ignored) {
            return "{\"status\":\"failed\",\"traversed\":false}";
        }
    }

    private static String first(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            String value = clean(object.optString(key, ""));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
