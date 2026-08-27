package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Last runtime state for graph connects. Visual graph reads this to highlight traversed / blocked edges. */
final class GraphRuntimeEdgeTrace {
    private static final Map<String, JSONObject> LAST = new LinkedHashMap<>();

    private GraphRuntimeEdgeTrace() { }

    static synchronized void mark(String edgeId, String from, String to, String status, String turnId) {
        try {
            String id = edgeId == null ? "" : edgeId.trim();
            if (id.isEmpty()) id = (from == null ? "" : from) + "→" + (to == null ? "" : to);
            JSONObject item = new JSONObject()
                    .put("edge_id", id)
                    .put("from", from == null ? "" : from)
                    .put("to", to == null ? "" : to)
                    .put("status", status == null ? "" : status)
                    .put("turn_id", turnId == null ? "" : turnId)
                    .put("timestamp", System.currentTimeMillis());
            LAST.put(id, item);
            while (LAST.size() > 64) {
                String first = LAST.keySet().iterator().next();
                LAST.remove(first);
            }
        } catch (Exception ignored) { }
    }

    static synchronized JSONArray snapshotJson() {
        JSONArray out = new JSONArray();
        for (JSONObject item : LAST.values()) {
            try { out.put(new JSONObject(item.toString())); }
            catch (Exception ignored) { }
        }
        return out;
    }

    static synchronized void clear() { LAST.clear(); }
}
