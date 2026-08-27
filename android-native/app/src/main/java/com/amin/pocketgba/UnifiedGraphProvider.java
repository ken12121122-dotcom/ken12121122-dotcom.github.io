package com.amin.pocketgba;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Single read model for the visual graph. Runtime registries remain authoritative. */
final class UnifiedGraphProvider {
    static final String ACTION_CHANGED = "com.amin.pocketgba.UNIFIED_GRAPH_CHANGED";

    private UnifiedGraphProvider() { }

    static void notifyChanged(Context context) {
        if (context == null) return;
        Intent intent = new Intent(ACTION_CHANGED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    static String graphJson(Context context, NodeMetadataStore nodeStore) {
        try {
            JSONObject out = new JSONObject();
            out.put("format", "amin-unified-graph");
            out.put("version", 2);
            out.put("semanticZoom", new JSONObject().put("hysteresis", 1.08));

            JSONArray domains = new JSONArray();
            domains.put(new JSONObject()
                    .put("id", "system:amin")
                    .put("title", "AMIN")
                    .put("summary", "Unified registry graph"));
            out.put("domains", domains);

            JSONArray groups = new JSONArray();
            groups.put(group("group:nodes", "NODE", "system:amin", "Knowledge / memory nodes"));
            groups.put(group("group:commands", "COMMAND", "system:amin", "Executable commands"));
            out.put("groups", groups);

            JSONArray entities = new JSONArray();
            JSONArray commandCatalog = new JSONArray();
            Set<String> ids = new HashSet<>();

            JSONObject nodeRoot = new JSONObject(NodeRegistry.registryJson(context, nodeStore));
            JSONArray registryNodes = nodeRoot.optJSONArray("nodes");
            if (registryNodes != null) {
                for (int i = 0; i < registryNodes.length(); i++) {
                    JSONObject source = registryNodes.optJSONObject(i);
                    if (source == null) continue;
                    String id = first(source, "node_id", "nodeId", "capability_id", "capabilityId", "id");
                    if (id.isEmpty() || ids.contains(id)) continue;
                    ids.add(id);
                    String title = first(source, "name", "title");
                    if (title.isEmpty()) title = id;
                    JSONObject entity = new JSONObject();
                    entity.put("id", id);
                    entity.put("title", title);
                    entity.put("summary", source.optString("description", ""));
                    entity.put("detail", nodeDetail(source));
                    entity.put("groupId", "group:nodes");
                    entity.put("domainId", "system:amin");
                    entity.put("entityType", "node");
                    entity.put("registryId", id);
                    entity.put("route", source.optString("route", ""));
                    entity.put("parentId", first(source, "parent_id", "parentNodeId", "parent"));
                    entity.put("nodeType", first(source, "node_type", "nodeType"));
                    entity.put("status", source.optString("status", "active"));
                    entity.put("version", first(source, "version", "nodeVersion"));
                    entity.put("direction", source.optString("direction", ""));
                    entity.put("slot", source.optInt("slot", 0));
                    entity.put("voice", source.optJSONObject("voice") == null ? new JSONObject() : source.optJSONObject("voice"));
                    entity.put("storage", source.optJSONObject("storage") == null ? new JSONObject() : source.optJSONObject("storage"));
                    entities.put(entity);
                }
            }

            for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
                String id = "command:" + command.getId();
                if (ids.contains(id)) continue;
                ids.add(id);
                JSONObject entity = new JSONObject();
                entity.put("id", id);
                entity.put("title", command.getTitle());
                entity.put("summary", command.getDescription());
                entity.put("detail", "action: " + command.getAction() + "\nphrases: " + join(command.getPhrases()));
                entity.put("groupId", "group:commands");
                entity.put("domainId", "system:amin");
                entity.put("entityType", "command");
                entity.put("registryId", id);
                entity.put("action", command.getAction());
                entity.put("category", command.getCategory());
                entity.put("mode", command.getMode() == null ? "" : command.getMode());
                entity.put("requiresAccessibility", command.requiresAccessibility());
                entity.put("phrases", new JSONArray(command.getPhrases()));
                entities.put(entity);
                commandCatalog.put(new JSONObject(entity.toString()));
            }

            DynamicCommandStore dynamicStore = new DynamicCommandStore(context);
            JSONArray dynamic = dynamicStore.registered();
            for (int i = 0; i < dynamic.length(); i++) {
                JSONObject source = dynamic.optJSONObject(i);
                if (source == null) continue;
                String rawId = source.optString("id", "").trim();
                if (rawId.isEmpty()) continue;
                String id = rawId.startsWith("command:") ? rawId : "command:" + rawId;
                if (ids.contains(id)) continue;
                ids.add(id);
                JSONObject entity = new JSONObject();
                entity.put("id", id);
                entity.put("title", source.optString("title", rawId));
                entity.put("summary", source.optString("description", "Dynamic command"));
                entity.put("detail", "action: " + source.optString("action", "") + "\nphrases: " + jsonList(source.optJSONArray("phrases")));
                entity.put("groupId", "group:commands");
                entity.put("domainId", "system:amin");
                entity.put("entityType", "command");
                entity.put("registryId", id);
                entity.put("action", source.optString("action", ""));
                entity.put("category", source.optString("category", "dynamic"));
                entity.put("mode", source.optString("mode", ""));
                entity.put("requiresAccessibility", source.optBoolean("requires_accessibility", false));
                entity.put("phrases", source.optJSONArray("phrases") == null ? new JSONArray() : source.optJSONArray("phrases"));
                entities.put(entity);
                commandCatalog.put(new JSONObject(entity.toString()));
            }
            out.put("nodes", entities);
            out.put("commands", commandCatalog);

            JSONArray relations = new JSONArray();
            JSONObject edgeRoot = new JSONObject(NodeRegistry.typedEdgesJson(context, nodeStore));
            JSONArray edges = edgeRoot.optJSONArray("edges");
            if (edges == null) edges = new JSONArray();
            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.optJSONObject(i);
                if (edge == null) continue;
                String from = first(edge, "from", "source", "source_node_id", "sourceNodeId");
                String to = first(edge, "to", "target", "target_node_id", "targetNodeId");
                if (from.isEmpty() || to.isEmpty()) continue;
                String relation = first(edge, "relation", "type", "edge_type");
                if (relation.isEmpty()) relation = "related_to";
                String edgeId = first(edge, "edge_id", "edgeId");
                if (edgeId.isEmpty()) edgeId = "edge:" + i;
                JSONObject relationOut = new JSONObject()
                        .put("id", edgeId)
                        .put("from", from)
                        .put("to", to)
                        .put("type", relation)
                        .put("status", edge.optString("status", "active"));
                if (edge.has("command_chain")) relationOut.put("commandChain", edge.optJSONArray("command_chain"));
                else if (edge.has("commands")) relationOut.put("commandChain", edge.optJSONArray("commands"));
                else relationOut.put("commandChain", new JSONArray());
                if (edge.has("gate")) relationOut.put("gate", edge.optJSONObject("gate"));
                else relationOut.put("gate", new JSONObject().put("enabled", true));
                relationOut.put("authority", edge.optString("authority", ""));
                relations.put(relationOut);
            }
            out.put("relations", relations);
            out.put("runtimeEdges", GraphRuntimeEdgeTrace.snapshotJson());
            out.put("runtimeFlows", GraphRuntimeFlowTrace.snapshotJson());
            return out.toString();
        } catch (Exception error) {
            return "{\"format\":\"amin-unified-graph\",\"version\":2,\"domains\":[],\"groups\":[],\"nodes\":[],\"commands\":[],\"relations\":[],\"runtimeEdges\":[],\"runtimeFlows\":[]}";
        }
    }

    private static JSONObject group(String id, String title, String domainId, String summary) throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("domainId", domainId).put("summary", summary);
    }

    private static String nodeDetail(JSONObject node) {
        StringBuilder out = new StringBuilder();
        String route = node.optString("route", "").trim();
        if (!route.isEmpty()) out.append("route: ").append(route);
        JSONObject voice = node.optJSONObject("voice");
        if (voice != null) {
            JSONArray aliases = voice.optJSONArray("aliases");
            if (aliases != null && aliases.length() > 0) {
                if (out.length() > 0) out.append("\n");
                out.append("aliases: ").append(jsonList(aliases));
            }
        }
        if (out.length() == 0) out.append(node.optString("description", ""));
        return out.toString();
    }

    private static String join(java.util.List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append("、");
            out.append(value);
        }
        return out.toString();
    }

    private static String jsonList(JSONArray values) {
        if (values == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "").trim();
            if (value.isEmpty()) continue;
            if (out.length() > 0) out.append("、");
            out.append(value);
        }
        return out.toString();
    }

    private static String first(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}
