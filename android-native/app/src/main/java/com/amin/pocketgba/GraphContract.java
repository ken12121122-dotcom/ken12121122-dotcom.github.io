package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Stable UI-independent graph contract shared by navigation, voice, data and projection layers. */
final class GraphContract {
    static final int CONTRACT_VERSION = 2;
    static final String REGISTRY_FORMAT = "amin-node-registry";
    static final String EDGE_FORMAT = "amin-typed-edges";
    static final String APP_ORIGIN = "app";
    static final String HIERARCHY_RELATIONSHIP = "contains";
    static final String HIERARCHY_CLASS = "hierarchy";
    static final String MANIFEST_AUTHORITY = "android_manifest";
    static final Set<String> RELATIONSHIP_TYPES = new HashSet<>(Arrays.asList(
            "contains", "opens", "uses", "reads_from", "writes_to", "executes", "depends_on"));

    private GraphContract() {}

    static String capabilityId(String origin, String rawId, String explicitCapabilityId) {
        String explicit = clean(explicitCapabilityId);
        if (!explicit.isEmpty()) return explicit;
        String cleanOrigin = clean(origin);
        String cleanId = clean(rawId);
        if (cleanOrigin.isEmpty() || cleanId.isEmpty()) return "";
        return cleanOrigin + ":" + cleanId;
    }

    static String nodeId(String origin, String rawId) {
        String cleanOrigin = clean(origin);
        String cleanId = clean(rawId);
        if (cleanOrigin.isEmpty() || cleanId.isEmpty()) return "";
        return cleanOrigin + ":" + cleanId;
    }

    static boolean isRelationshipTypeAllowed(String type) { return RELATIONSHIP_TYPES.contains(clean(type)); }

    static String nodeRegistryJson(String appNavigationJson) {
        JSONObject out = new JSONObject();
        JSONArray nodes = new JSONArray();
        JSONArray errors = new JSONArray();
        try {
            JSONObject graph = new JSONObject(appNavigationJson == null ? "{}" : appNavigationJson);
            Set<String> capabilityIds = new HashSet<>();
            String rootRawId = clean(graph.optString("rootId", "app-core"));
            if (rootRawId.isEmpty()) rootRawId = "app-core";
            JSONObject rootSource = new JSONObject();
            rootSource.put("id", rootRawId);
            rootSource.put("title", graph.optString("rootTitle", "Amin Pocket"));
            rootSource.put("description", graph.optString("rootDescription", ""));
            rootSource.put("capabilityId", graph.optString("rootCapabilityId", ""));
            rootSource.put("route", graph.optString("rootRoute", "amin-home://open"));
            rootSource.put("nodeType", "capability");
            rootSource.put("status", "active");
            rootSource.put("nodeVersion", "1");
            rootSource.put("actions", new JSONArray().put("open"));
            rootSource.put("voice", new JSONObject().put("enabled", false).put("aliases", new JSONArray()));
            rootSource.put("storage", emptyStorage());
            appendUnique(nodes, errors, capabilityIds, registryNode(rootSource, ""));

            JSONArray pages = graph.optJSONArray("pages");
            if (pages != null) {
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject page = pages.optJSONObject(i);
                    if (page == null) continue;
                    String rawId = clean(page.optString("id", ""));
                    if (rawId.isEmpty()) { errors.put("page[" + i + "] missing id"); continue; }
                    appendUnique(nodes, errors, capabilityIds,
                            registryNode(page, page.optString("parent", rootRawId)));
                }
            }
            out.put("format", REGISTRY_FORMAT);
            out.put("version", CONTRACT_VERSION);
            out.put("identityRule", "capability_id_is_independent_of_name_parent_route_and_position");
            out.put("sourceAuthority", MANIFEST_AUTHORITY);
            out.put("valid", errors.length() == 0);
            out.put("errors", errors);
            out.put("nodes", nodes);
        } catch (JSONException e) {
            safePut(out, "format", REGISTRY_FORMAT); safePut(out, "version", CONTRACT_VERSION);
            safePut(out, "valid", false); safePut(out, "errors", new JSONArray().put("invalid app navigation json"));
            safePut(out, "nodes", nodes);
        }
        return out.toString();
    }

    static String typedEdgesJson(String appNavigationJson) {
        JSONObject out = new JSONObject();
        JSONArray edges = new JSONArray();
        try {
            JSONObject graph = new JSONObject(appNavigationJson == null ? "{}" : appNavigationJson);
            String rootRawId = clean(graph.optString("rootId", "app-core"));
            if (rootRawId.isEmpty()) rootRawId = "app-core";
            JSONArray pages = graph.optJSONArray("pages");
            if (pages != null) {
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject page = pages.optJSONObject(i);
                    if (page == null) continue;
                    String childRawId = clean(page.optString("id", ""));
                    if (childRawId.isEmpty()) continue;
                    String parentRawId = clean(page.optString("parent", rootRawId));
                    if (parentRawId.isEmpty()) parentRawId = rootRawId;
                    JSONObject edge = edge(
                            "edge:manifest:" + parentRawId + ":" + childRawId,
                            nodeId(APP_ORIGIN, parentRawId), nodeId(APP_ORIGIN, childRawId),
                            HIERARCHY_RELATIONSHIP, "active", "1");
                    edge.put("relationshipClass", HIERARCHY_CLASS);
                    edge.put("authority", MANIFEST_AUTHORITY);
                    edge.put("structural", true);
                    edges.put(edge);
                }
            }
            out.put("format", EDGE_FORMAT);
            out.put("version", CONTRACT_VERSION);
            out.put("hierarchyAuthority", MANIFEST_AUTHORITY);
            out.put("relationshipTypes", new JSONArray(RELATIONSHIP_TYPES));
            out.put("edges", edges);
        } catch (JSONException e) {
            safePut(out, "format", EDGE_FORMAT); safePut(out, "version", CONTRACT_VERSION); safePut(out, "edges", edges);
        }
        return out.toString();
    }

    static JSONObject edge(String edgeId, String sourceNodeId, String targetNodeId,
                           String relationshipType, String status, String version) throws JSONException {
        if (!isRelationshipTypeAllowed(relationshipType)) throw new JSONException("unsupported relationship type");
        JSONObject edge = new JSONObject();
        edge.put("edge_id", edgeId);
        edge.put("edgeId", edgeId);
        edge.put("source_node_id", sourceNodeId);
        edge.put("source", sourceNodeId);
        edge.put("target_node_id", targetNodeId);
        edge.put("target", targetNodeId);
        edge.put("relationship_type", relationshipType);
        edge.put("relationshipType", relationshipType);
        edge.put("status", clean(status).isEmpty() ? "active" : status);
        edge.put("version", clean(version).isEmpty() ? "1" : version);
        return edge;
    }

    private static JSONObject registryNode(JSONObject source, String parentRawId) throws JSONException {
        String rawId = clean(source.optString("id", ""));
        String id = nodeId(APP_ORIGIN, rawId);
        String cap = capabilityId(APP_ORIGIN, rawId, source.optString("capabilityId", ""));
        String name = source.optString("title", rawId);
        String parentId = clean(parentRawId).isEmpty() ? "" : nodeId(APP_ORIGIN, parentRawId);
        JSONObject node = new JSONObject();
        node.put("node_id", id); node.put("nodeId", id); node.put("rawId", rawId);
        node.put("capability_id", cap); node.put("capabilityId", cap);
        node.put("name", name); node.put("title", name);
        node.put("description", source.optString("description", ""));
        node.put("node_type", source.optString("nodeType", "capability"));
        node.put("parent_id", parentId); node.put("parentNodeId", parentId);
        node.put("status", source.optString("status", "active"));
        node.put("version", source.optString("nodeVersion", "1"));
        node.put("actions", copyArray(source.optJSONArray("actions")));
        node.put("input_contract", source.optString("inputContract", ""));
        node.put("output_contract", source.optString("outputContract", ""));
        node.put("route", source.optString("route", ""));
        node.put("activity", source.optString("activity", ""));
        node.put("origin", APP_ORIGIN);
        node.put("locked", source.optBoolean("locked", true));
        node.put("voice", source.optJSONObject("voice") == null
                ? new JSONObject().put("enabled", false).put("aliases", new JSONArray())
                : new JSONObject(source.optJSONObject("voice").toString()));
        node.put("storage", source.optJSONObject("storage") == null
                ? emptyStorage() : new JSONObject(source.optJSONObject("storage").toString()));
        return node;
    }

    private static JSONObject emptyStorage() throws JSONException {
        return new JSONObject().put("adapter", "").put("source_id", "").put("table", "");
    }

    private static JSONArray copyArray(JSONArray source) throws JSONException {
        return source == null ? new JSONArray() : new JSONArray(source.toString());
    }

    private static void appendUnique(JSONArray nodes, JSONArray errors, Set<String> ids, JSONObject node) throws JSONException {
        String id = node.optString("capability_id", "");
        if (id.isEmpty()) { errors.put("node missing capabilityId: " + node.optString("rawId", "")); return; }
        if (!ids.add(id)) { errors.put("duplicate capabilityId: " + id); return; }
        nodes.put(node);
    }

    private static void safePut(JSONObject target, String key, Object value) { try { target.put(key, value); } catch (JSONException ignored) {} }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
