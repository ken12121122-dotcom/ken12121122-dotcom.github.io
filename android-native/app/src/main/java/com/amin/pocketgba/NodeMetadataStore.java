package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Candidate/local metadata overrides. Capability identity is intentionally immutable here. */
final class NodeMetadataStore {
    private static final String PREFS = "amin_node_metadata";
    private static final String KEY_OVERRIDES = "overrides";
    private static final String KEY_EDGES = "custom_edges";
    private final SharedPreferences prefs;

    NodeMetadataStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONObject overrideFor(String nodeId) {
        try {
            JSONObject all = new JSONObject(prefs.getString(KEY_OVERRIDES, "{}"));
            JSONObject item = all.optJSONObject(nodeId);
            return item == null ? new JSONObject() : new JSONObject(item.toString());
        } catch (Exception e) { return new JSONObject(); }
    }

    void saveEditable(String nodeId, String name, String description, String parentId,
                      JSONObject voice, JSONObject storage) {
        if (nodeId == null || nodeId.trim().isEmpty()) return;
        try {
            JSONObject all = new JSONObject(prefs.getString(KEY_OVERRIDES, "{}"));
            JSONObject item = all.optJSONObject(nodeId);
            if (item == null) item = new JSONObject();
            item.put("name", name == null ? "" : name.trim());
            item.put("description", description == null ? "" : description.trim());
            item.put("parent_id", parentId == null ? "" : parentId.trim());
            if (voice != null) item.put("voice", voice);
            if (storage != null) item.put("storage", storage);
            all.put(nodeId, item);
            prefs.edit().putString(KEY_OVERRIDES, all.toString()).apply();
        } catch (Exception ignored) {}
    }

    JSONArray customEdges() {
        try { return new JSONArray(prefs.getString(KEY_EDGES, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    void addOrReplaceEdge(JSONObject edge) {
        if (edge == null) return;
        String edgeId = edge.optString("edge_id", edge.optString("edgeId", "")).trim();
        if (edgeId.isEmpty()) return;
        JSONArray source = customEdges();
        JSONArray out = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < source.length(); i++) {
            JSONObject current = source.optJSONObject(i);
            if (current == null) continue;
            if (edgeId.equals(current.optString("edge_id", current.optString("edgeId", "")))) {
                out.put(edge); replaced = true;
            } else out.put(current);
        }
        if (!replaced) out.put(edge);
        prefs.edit().putString(KEY_EDGES, out.toString()).apply();
    }

    void removeEdge(String edgeId) {
        JSONArray source = customEdges();
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject current = source.optJSONObject(i);
            if (current == null) continue;
            if (!edgeId.equals(current.optString("edge_id", current.optString("edgeId", "")))) out.put(current);
        }
        prefs.edit().putString(KEY_EDGES, out.toString()).apply();
    }

    String applyToRegistry(String baseRegistryJson) {
        try {
            JSONObject root = new JSONObject(baseRegistryJson);
            JSONArray nodes = root.optJSONArray("nodes");
            if (nodes == null) return baseRegistryJson;
            JSONObject all = new JSONObject(prefs.getString(KEY_OVERRIDES, "{}"));
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.optJSONObject(i);
                if (node == null) continue;
                String nodeId = node.optString("node_id", node.optString("nodeId", ""));
                JSONObject o = all.optJSONObject(nodeId);
                if (o == null) continue;
                if (o.has("name")) { node.put("name", o.optString("name")); node.put("title", o.optString("name")); }
                if (o.has("description")) node.put("description", o.optString("description"));
                if (o.has("parent_id")) { node.put("parent_id", o.optString("parent_id")); node.put("parentNodeId", o.optString("parent_id")); }
                if (o.has("voice")) node.put("voice", o.optJSONObject("voice"));
                if (o.has("storage")) node.put("storage", o.optJSONObject("storage"));
            }
            return root.toString();
        } catch (Exception e) { return baseRegistryJson; }
    }

    String mergeEdges(String baseEdgesJson) {
        try {
            JSONObject root = new JSONObject(baseEdgesJson);
            JSONArray edges = root.optJSONArray("edges");
            if (edges == null) { edges = new JSONArray(); root.put("edges", edges); }
            JSONArray custom = customEdges();
            for (int i = 0; i < custom.length(); i++) {
                JSONObject e = custom.optJSONObject(i);
                if (e != null) edges.put(e);
            }
            return root.toString();
        } catch (Exception e) { return baseEdgesJson; }
    }
}
