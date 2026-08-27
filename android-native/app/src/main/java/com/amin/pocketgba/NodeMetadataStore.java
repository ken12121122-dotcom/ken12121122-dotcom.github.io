package com.amin.pocketgba;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/** Local metadata overrides, approved custom nodes, and typed connects. */
final class NodeMetadataStore {
    private static final String PREFS = "amin_node_metadata";
    private static final String KEY_OVERRIDES = "overrides";
    private static final String KEY_EDGES = "custom_edges";
    private static final String KEY_CUSTOM_NODES = "custom_nodes";
    private final Context context;
    private final SharedPreferences prefs;

    NodeMetadataStore(Context context) {
        this.context = context.getApplicationContext();
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
        if (isCustomNode(nodeId)) {
            JSONObject node = customNode(nodeId);
            if (node == null) return;
            try {
                node.put("name", clean(name));
                node.put("title", clean(name));
                node.put("description", clean(description));
                node.put("parent_id", clean(parentId));
                node.put("parentNodeId", clean(parentId));
                if (voice != null) node.put("voice", voice);
                if (storage != null) node.put("storage", storage);
                saveCustomNode(node);
            } catch (Exception ignored) { }
            return;
        }
        try {
            JSONObject all = new JSONObject(prefs.getString(KEY_OVERRIDES, "{}"));
            JSONObject item = all.optJSONObject(nodeId);
            if (item == null) item = new JSONObject();
            item.put("name", clean(name));
            item.put("description", clean(description));
            item.put("parent_id", clean(parentId));
            if (voice != null) item.put("voice", voice);
            if (storage != null) item.put("storage", storage);
            all.put(nodeId, item);
            prefs.edit().putString(KEY_OVERRIDES, all.toString()).apply();
            UnifiedGraphProvider.notifyChanged(context);
        } catch (Exception ignored) {}
    }

    JSONArray customNodes() {
        try { return new JSONArray(prefs.getString(KEY_CUSTOM_NODES, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    /** Creates only a registration candidate and opens the mandatory approval gate. */
    JSONObject createCustomNode(String requestedName, String sourceText) {
        RegistryCandidateStore candidates = new RegistryCandidateStore(context);
        JSONObject candidate = candidates.createNodeCandidate(requestedName, sourceText);
        if (candidate == null) return null;
        try {
            Intent intent = new Intent(context, RegistryApprovalActivity.class);
            intent.putExtra(RegistryApprovalActivity.EXTRA_CANDIDATE_JSON, candidate.toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(intent);
            return new JSONObject()
                    .put("node_id", candidate.optString("candidate_id", ""))
                    .put("nodeId", candidate.optString("candidate_id", ""))
                    .put("name", "待註冊 · " + candidate.optString("title", "新節點"))
                    .put("title", "待註冊 · " + candidate.optString("title", "新節點"))
                    .put("registration_pending", true);
        } catch (Exception error) {
            candidates.reject(candidate);
            return null;
        }
    }

    /** Writes an already-approved node candidate into the authoritative registry store. */
    JSONObject registerApprovedNode(String requestedName, String sourceText) {
        String name = clean(requestedName);
        if (name.isEmpty()) name = "新節點";
        String id = "local:" + UUID.randomUUID().toString().substring(0, 8);
        try {
            JSONObject voice = new JSONObject().put("enabled", true)
                    .put("aliases", new JSONArray().put(name));
            JSONObject storage = new JSONObject().put("adapter", "").put("source_id", "").put("table", "");
            JSONObject node = new JSONObject()
                    .put("node_id", id).put("nodeId", id)
                    .put("rawId", id.substring("local:".length()))
                    .put("capability_id", id).put("capabilityId", id)
                    .put("name", name).put("title", name)
                    .put("description", sourceText == null ? "由聊天建立" : "由聊天建立：" + sourceText.trim())
                    .put("node_type", "custom")
                    .put("parent_id", "app:app-core").put("parentNodeId", "app:app-core")
                    .put("status", "active").put("version", "1")
                    .put("actions", new JSONArray())
                    .put("input_contract", "").put("output_contract", "")
                    .put("route", "").put("activity", "")
                    .put("origin", "local").put("locked", false)
                    .put("voice", voice).put("storage", storage)
                    .put("filter", new JSONObject()).put("input_context", new JSONObject());
            saveCustomNode(node);
            return new JSONObject(node.toString());
        } catch (Exception e) { return null; }
    }

    void saveCustomNode(JSONObject node) {
        if (node == null) return;
        String id = value(node, "node_id", "nodeId");
        if (id.isEmpty()) return;
        JSONArray source = customNodes();
        JSONArray out = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < source.length(); i++) {
            JSONObject current = source.optJSONObject(i);
            if (current == null) continue;
            if (id.equals(value(current, "node_id", "nodeId"))) {
                out.put(node);
                replaced = true;
            } else out.put(current);
        }
        if (!replaced) out.put(node);
        prefs.edit().putString(KEY_CUSTOM_NODES, out.toString()).apply();
        UnifiedGraphProvider.notifyChanged(context);
    }

    JSONObject customNode(String nodeId) {
        String wanted = clean(nodeId);
        JSONArray nodes = customNodes();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null && wanted.equals(value(node, "node_id", "nodeId"))) {
                try { return new JSONObject(node.toString()); } catch (Exception ignored) { return node; }
            }
        }
        return null;
    }

    boolean isCustomNode(String nodeId) { return customNode(nodeId) != null; }

    void removeCustomNode(String nodeId) {
        String wanted = clean(nodeId);
        if (wanted.isEmpty()) return;
        JSONArray nodes = customNodes();
        JSONArray keptNodes = new JSONArray();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null && !wanted.equals(value(node, "node_id", "nodeId"))) keptNodes.put(node);
        }
        JSONArray edges = customEdges();
        JSONArray keptEdges = new JSONArray();
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            if (edge == null) continue;
            String source = value(edge, "from", "source");
            if (source.isEmpty()) source = value(edge, "source_node_id", "sourceNodeId");
            String target = value(edge, "to", "target");
            if (target.isEmpty()) target = value(edge, "target_node_id", "targetNodeId");
            if (!wanted.equals(source) && !wanted.equals(target)) keptEdges.put(edge);
        }
        prefs.edit().putString(KEY_CUSTOM_NODES, keptNodes.toString())
                .putString(KEY_EDGES, keptEdges.toString()).apply();
        UnifiedGraphProvider.notifyChanged(context);
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
        UnifiedGraphProvider.notifyChanged(context);
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
        UnifiedGraphProvider.notifyChanged(context);
    }

    String applyToRegistry(String baseRegistryJson) {
        try {
            JSONObject root = new JSONObject(baseRegistryJson);
            JSONArray nodes = root.optJSONArray("nodes");
            if (nodes == null) { nodes = new JSONArray(); root.put("nodes", nodes); }
            JSONObject all = new JSONObject(prefs.getString(KEY_OVERRIDES, "{}"));
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.optJSONObject(i);
                if (node == null) continue;
                String nodeId = value(node, "node_id", "nodeId");
                JSONObject o = all.optJSONObject(nodeId);
                if (o == null) continue;
                if (o.has("name")) { node.put("name", o.optString("name")); node.put("title", o.optString("name")); }
                if (o.has("description")) node.put("description", o.optString("description"));
                if (o.has("parent_id")) { node.put("parent_id", o.optString("parent_id")); node.put("parentNodeId", o.optString("parent_id")); }
                if (o.has("voice")) node.put("voice", o.optJSONObject("voice"));
                if (o.has("storage")) node.put("storage", o.optJSONObject("storage"));
            }
            JSONArray custom = customNodes();
            for (int i = 0; i < custom.length(); i++) {
                JSONObject node = custom.optJSONObject(i);
                if (node != null) nodes.put(new JSONObject(node.toString()));
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

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String value(JSONObject o, String a, String b) {
        String v = o == null ? "" : o.optString(a, "");
        if (v.isEmpty() && o != null) v = o.optString(b, "");
        return clean(v);
    }
}
