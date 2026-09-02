package com.amin.pocketgba;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/** Local metadata overrides, approved custom nodes/actions, and typed connects. */
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

    JSONObject registerApprovedNode(String requestedName, String sourceText) {
        String name = clean(requestedName);
        if (name.isEmpty()) name = "新節點";
        String id = "local:" + UUID.randomUUID().toString().substring(0, 8);
        try {
            String description = sourceText == null ? "由聊天建立" : "由聊天建立：" + sourceText.trim();
            String mdSource = ManagedNodeMdStore.createDraft(context, id, name, description);
            if (mdSource.isEmpty()) return null;
            String contextNodeId = id + ":md";
            JSONObject voice = new JSONObject().put("enabled", true)
                    .put("aliases", new JSONArray().put(name));
            JSONObject storage = new JSONObject().put("adapter", "").put("source_id", "").put("table", "");
            JSONObject node = new JSONObject()
                    .put("node_id", id).put("nodeId", id)
                    .put("rawId", id.substring("local:".length()))
                    .put("capability_id", id).put("capabilityId", id)
                    .put("name", name).put("title", name)
                    .put("description", description)
                    .put("node_type", "custom")
                    .put("parent_id", "app:app-core").put("parentNodeId", "app:app-core")
                    .put("status", "active").put("version", "1")
                    .put("actions", new JSONArray())
                    .put("input_contract", "").put("output_contract", "")
                    .put("route", "").put("activity", "")
                    .put("origin", "local").put("locked", false)
                    .put("voice", voice).put("storage", storage)
                    .put("filter", new JSONObject()).put("input_context", new JSONObject()
                            .put("mode", "managed_md").put("context_node_id", contextNodeId)
                            .put("chat_access", "read_only"));
            saveCustomNode(node);
            JSONObject reference = new JSONObject()
                    .put("node_id", contextNodeId).put("nodeId", contextNodeId)
                    .put("rawId", id.substring("local:".length()) + "-md")
                    .put("capability_id", "context." + id.substring("local:".length()) + ".md")
                    .put("capabilityId", "context." + id.substring("local:".length()) + ".md")
                    .put("name", name + " MD").put("title", name + " MD")
                    .put("description", name + "節點的唯讀 Markdown context。")
                    .put("node_type", "reference")
                    .put("parent_id", id).put("parentNodeId", id)
                    .put("status", "active").put("version", "1")
                    .put("actions", new JSONArray().put("read"))
                    .put("input_contract", "").put("output_contract", "")
                    .put("route", "").put("activity", "")
                    .put("origin", "local").put("locked", true)
                    .put("voice", new JSONObject().put("enabled", false).put("aliases", new JSONArray()))
                    .put("storage", new JSONObject().put("adapter", "internal_md")
                            .put("source_id", mdSource).put("table", ""))
                    .put("filter", new JSONObject()).put("input_context", new JSONObject());
            saveCustomNode(reference);
            addOrReplaceEdge(GraphContract.edge("edge:" + id + ":reads-context", id,
                    contextNodeId, "reads_from", "active", "1")
                    .put("authority", "managed_node_md").put("read_only", true));
            return new JSONObject(node.toString());
        } catch (Exception e) { return null; }
    }

    JSONObject registerApprovedAction(String ownerNodeId, String actionId) {
        String owner = clean(ownerNodeId);
        String action = clean(actionId);
        if (owner.isEmpty() || action.isEmpty()) return null;
        try {
            JSONObject custom = customNode(owner);
            if (custom != null) {
                JSONArray actions = copyArray(custom.optJSONArray("actions"));
                appendUnique(actions, action);
                custom.put("actions", actions);
                saveCustomNode(custom);
                return new JSONObject().put("entity_type", "action").put("owner_node_id", owner).put("action", action);
            }
            JSONObject registered = NodeRegistry.findNode(context, this, owner);
            if (registered == null) return null;
            JSONArray actions = copyArray(registered.optJSONArray("actions"));
            appendUnique(actions, action);
            JSONObject all = new JSONObject(prefs.getString(KEY_OVERRIDES, "{}"));
            JSONObject item = all.optJSONObject(owner);
            if (item == null) item = new JSONObject();
            item.put("actions", actions);
            all.put(owner, item);
            prefs.edit().putString(KEY_OVERRIDES, all.toString()).apply();
            UnifiedGraphProvider.notifyChanged(context);
            return new JSONObject().put("entity_type", "action").put("owner_node_id", owner).put("action", action);
        } catch (Exception error) { return null; }
    }

    JSONObject registerApprovedConnect(JSONObject edge) {
        if (edge == null) return null;
        try {
            String edgeId = value(edge, "edge_id", "edgeId");
            String source = value(edge, "source_node_id", "source");
            if (source.isEmpty()) source = value(edge, "from", "source");
            String target = value(edge, "target_node_id", "target");
            if (target.isEmpty()) target = value(edge, "to", "target");
            String relationship = CapabilityCandidateProtocol.relationshipName(edge);
            if (edgeId.isEmpty() || source.isEmpty() || target.isEmpty() || !GraphContract.isRelationshipTypeAllowed(relationship)) return null;
            JSONObject canonical = new JSONObject(edge.toString())
                    .put("edge_id", edgeId).put("edgeId", edgeId)
                    .put("source_node_id", source).put("source", source).put("from", source)
                    .put("target_node_id", target).put("target", target).put("to", target)
                    .put("relationship_type", relationship).put("relationshipType", relationship).put("relation", relationship)
                    .put("status", edge.optString("status", "active"))
                    .put("version", edge.optString("version", "1"));
            if (canonical.optJSONObject("gate") == null) canonical.put("gate", new JSONObject().put("enabled", true));
            if (canonical.optJSONArray("command_chain") == null) canonical.put("command_chain", new JSONArray());
            addOrReplaceEdge(canonical);
            return new JSONObject(canonical.toString());
        } catch (Exception error) { return null; }
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
        String contextNodeId = "";
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject candidate = nodes.optJSONObject(i);
            if (candidate == null || !wanted.equals(value(candidate, "node_id", "nodeId"))) continue;
            JSONObject input = candidate.optJSONObject("input_context");
            if (input != null) contextNodeId = clean(input.optString("context_node_id", ""));
            break;
        }
        JSONArray keptNodes = new JSONArray();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            String currentId = value(node, "node_id", "nodeId");
            if (node != null && !wanted.equals(currentId) && !contextNodeId.equals(currentId)) keptNodes.put(node);
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
            if (!wanted.equals(source) && !wanted.equals(target)
                    && !contextNodeId.equals(source) && !contextNodeId.equals(target)) keptEdges.put(edge);
        }
        prefs.edit().putString(KEY_CUSTOM_NODES, keptNodes.toString())
                .putString(KEY_EDGES, keptEdges.toString()).apply();
        UnifiedGraphProvider.notifyChanged(context);
    }

    JSONArray customEdges() {
        try { return new JSONArray(prefs.getString(KEY_EDGES, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    JSONObject customEdge(String edgeId) {
        String wanted = clean(edgeId);
        if (wanted.isEmpty()) return null;
        JSONArray edges = customEdges();
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            if (edge != null && wanted.equals(value(edge, "edge_id", "edgeId"))) {
                try { return new JSONObject(edge.toString()); } catch (Exception ignored) { return edge; }
            }
        }
        return null;
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
                if (o.has("actions")) node.put("actions", copyArray(o.optJSONArray("actions")));
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

    private static JSONArray copyArray(JSONArray source) {
        try { return source == null ? new JSONArray() : new JSONArray(source.toString()); }
        catch (Exception error) { return new JSONArray(); }
    }

    private static void appendUnique(JSONArray values, String value) {
        String wanted = clean(value);
        if (wanted.isEmpty()) return;
        for (int i = 0; i < values.length(); i++) {
            if (wanted.equalsIgnoreCase(clean(values.optString(i, "")))) return;
        }
        values.put(wanted);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String value(JSONObject o, String a, String b) {
        String v = o == null ? "" : o.optString(a, "");
        if (v.isEmpty() && o != null) v = o.optString(b, "");
        return clean(v);
    }
}
