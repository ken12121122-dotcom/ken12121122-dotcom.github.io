package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only adapter from approved Registry and Command records into Capability Graph batches. */
final class CapabilityInventoryAdapter {
    private CapabilityInventoryAdapter() { }

    static JSONObject registryBatch(JSONObject registry, JSONObject typedEdges, long observedAt) throws Exception {
        if (registry == null || !registry.optBoolean("valid", false)) {
            throw new IllegalArgumentException("INVALID_NODE_REGISTRY");
        }
        JSONArray nodes = registry.optJSONArray("nodes");
        if (nodes == null) throw new IllegalArgumentException("INVALID_NODE_REGISTRY");
        JSONArray entities = new JSONArray();
        Map<String, String> nodeToCapability = new LinkedHashMap<>();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null) continue;
            if ("reference".equalsIgnoreCase(node.optString("node_type", ""))) continue;
            String nodeId = first(node, "node_id", "nodeId");
            String capabilityId = first(node, "capability_id", "capabilityId");
            if (capabilityId.isEmpty()) throw new IllegalArgumentException("CAPABILITY_ID_REQUIRED");
            if (!nodeId.isEmpty()) nodeToCapability.put(nodeId, capabilityId);
            String type = capabilityType(node.optString("node_type", "capability"));
            JSONObject payload = new JSONObject()
                    .put("title", node.optString("name", node.optString("title", capabilityId)))
                    .put("description", node.optString("description", ""))
                    .put("node_id", nodeId)
                    .put("node_type", node.optString("node_type", "capability"))
                    .put("route", node.optString("route", ""))
                    .put("activity", node.optString("activity", ""))
                    .put("actions", copy(node.optJSONArray("actions")))
                    .put("voice", copy(node.optJSONObject("voice")))
                    .put("storage", copy(node.optJSONObject("storage")))
                    .put("input_context", copy(node.optJSONObject("input_context")))
                    .put("lifecycle_status", lifecycle(node.optString("status", "active")))
                    .put("review_status", "approved")
                    .put("certification_status", "not_certified")
                    .put("source_records", new JSONArray().put(new JSONObject()
                            .put("source_id", nodeId)
                            .put("authority", registry.optString("sourceAuthority", "android_manifest"))
                            .put("version", node.optString("version", "1"))));
            entities.put(CapabilityGraphContract.entity(capabilityId, type, payload));
        }

        JSONArray relations = new JSONArray();
        JSONArray edges = typedEdges == null ? null : typedEdges.optJSONArray("edges");
        if (edges != null) {
            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.optJSONObject(i);
                if (edge == null) continue;
                String edgeId = first(edge, "edge_id", "edgeId");
                String source = nodeToCapability.get(first(edge, "source_node_id", "source", "from"));
                String target = nodeToCapability.get(first(edge, "target_node_id", "target", "to"));
                String semantic = first(edge, "relationship_type", "relationshipType", "relation");
                if (source == null || target == null || edgeId.isEmpty()) continue;
                relations.put(CapabilityGraphContract.relation(edgeId, source,
                        CapabilityGraphContract.CAPABILITY, semantic, target,
                        CapabilityGraphContract.CAPABILITY, new JSONObject()
                                .put("authority", edge.optString("authority", "node_registry"))
                                .put("source_records", new JSONArray().put(new JSONObject()
                                        .put("source_id", edgeId)
                                        .put("authority", edge.optString("authority", "node_registry"))))));
            }
        }
        String revision = "registry:" + CapabilityGraphContract.contentFingerprint(new JSONObject()
                .put("entities", entities).put("relations", relations));
        return CapabilityGraphContract.batch("node_registry", "capabilities", revision,
                entities, relations, observedAt);
    }

    static JSONObject commandBatch(JSONArray commands, long observedAt) throws Exception {
        JSONArray source = commands == null ? new JSONArray() : commands;
        JSONArray entities = new JSONArray();
        Map<String, JSONObject> unique = new LinkedHashMap<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject command = source.optJSONObject(i);
            if (command == null) continue;
            String rawId = clean(command.optString("id", ""));
            if (rawId.startsWith("command:")) rawId = rawId.substring("command:".length());
            if (rawId.isEmpty() || unique.containsKey(rawId)) continue;
            unique.put(rawId, command);
        }
        for (Map.Entry<String, JSONObject> entry : unique.entrySet()) {
            String rawId = entry.getKey();
            JSONObject command = entry.getValue();
            String id = "command:" + rawId;
            JSONObject payload = new JSONObject()
                    .put("title", command.optString("title", rawId))
                    .put("description", command.optString("description", ""))
                    .put("command_id", rawId)
                    .put("action", command.optString("action", ""))
                    .put("phrases", copy(command.optJSONArray("phrases")))
                    .put("requires_accessibility", command.optBoolean("requires_accessibility", false))
                    .put("lifecycle_status", lifecycle(command.optString("status", "active")))
                    .put("review_status", "approved")
                    .put("certification_status", "not_certified")
                    .put("source_records", new JSONArray().put(new JSONObject()
                            .put("source_id", rawId)
                            .put("authority", command.optString("authority", "voice_command_catalog"))));
            entities.put(CapabilityGraphContract.entity(id, CapabilityGraphContract.COMMAND, payload));
        }
        String revision = "commands:" + CapabilityGraphContract.contentFingerprint(
                new JSONObject().put("entities", entities));
        return CapabilityGraphContract.batch("command_catalog", "commands", revision,
                entities, new JSONArray(), observedAt);
    }

    private static String capabilityType(String raw) {
        String type = clean(raw).toLowerCase();
        if ("skill".equals(type)) return CapabilityGraphContract.SKILL;
        if ("workflow".equals(type)) return CapabilityGraphContract.WORKFLOW;
        if ("tool".equals(type)) return CapabilityGraphContract.TOOL;
        if ("connector".equals(type)) return CapabilityGraphContract.CONNECTOR;
        return CapabilityGraphContract.CAPABILITY;
    }

    private static String lifecycle(String status) {
        String value = clean(status).toLowerCase();
        if ("suspended".equals(value)) return "suspended";
        if ("revoked".equals(value) || "deprecated".equals(value)) return "revoked";
        return "approved";
    }

    private static JSONArray copy(JSONArray value) {
        try { return value == null ? new JSONArray() : new JSONArray(value.toString()); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static JSONObject copy(JSONObject value) {
        try { return value == null ? new JSONObject() : new JSONObject(value.toString()); }
        catch (Exception ignored) { return new JSONObject(); }
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
