package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Short-lived workflow trace for visual Neural Flow overlays. Never becomes Registry data. */
final class GraphRuntimeFlowTrace {
    private static final Map<String, JSONObject> FLOWS = new LinkedHashMap<>();
    private static final int MAX_FLOWS = 12;

    private GraphRuntimeFlowTrace() { }

    static synchronized void startNodeCreation(String flowId, String title) {
        String id = clean(flowId);
        if (id.isEmpty()) return;
        try {
            JSONObject flow = new JSONObject()
                    .put("flow_id", id)
                    .put("type", "node_creation")
                    .put("title", clean(title).isEmpty() ? "建立 NODE" : title.trim())
                    .put("start_node_id", "app:voice")
                    .put("status", "running")
                    .put("created_at", System.currentTimeMillis())
                    .put("updated_at", System.currentTimeMillis())
                    .put("steps", new JSONArray());
            FLOWS.put(id, flow);
            trim();
        } catch (Exception ignored) { }
    }

    static synchronized void step(String flowId, String stepId, String title, String status) {
        JSONObject flow = FLOWS.get(clean(flowId));
        if (flow == null) return;
        try {
            JSONArray steps = flow.optJSONArray("steps");
            if (steps == null) { steps = new JSONArray(); flow.put("steps", steps); }
            JSONObject found = null;
            for (int i = 0; i < steps.length(); i++) {
                JSONObject item = steps.optJSONObject(i);
                if (item != null && clean(stepId).equals(item.optString("step_id", ""))) {
                    found = item;
                    break;
                }
            }
            if (found == null) {
                found = new JSONObject().put("step_id", clean(stepId));
                steps.put(found);
            }
            found.put("title", title == null ? "" : title)
                    .put("status", status == null ? "" : status)
                    .put("timestamp", System.currentTimeMillis());
            flow.put("updated_at", System.currentTimeMillis());
        } catch (Exception ignored) { }
    }

    static synchronized void finish(String flowId, String status, String finalNodeId) {
        JSONObject flow = FLOWS.get(clean(flowId));
        if (flow == null) return;
        try {
            flow.put("status", status == null ? "completed" : status)
                    .put("final_node_id", clean(finalNodeId))
                    .put("updated_at", System.currentTimeMillis());
        } catch (Exception ignored) { }
    }

    static synchronized JSONArray snapshotJson() {
        JSONArray out = new JSONArray();
        long now = System.currentTimeMillis();
        for (JSONObject flow : FLOWS.values()) {
            long updated = flow.optLong("updated_at", 0L);
            if (updated > 0 && now - updated > 5 * 60 * 1000L) continue;
            try { out.put(new JSONObject(flow.toString())); }
            catch (Exception ignored) { }
        }
        return out;
    }

    private static void trim() {
        while (FLOWS.size() > MAX_FLOWS) {
            String first = FLOWS.keySet().iterator().next();
            FLOWS.remove(first);
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
