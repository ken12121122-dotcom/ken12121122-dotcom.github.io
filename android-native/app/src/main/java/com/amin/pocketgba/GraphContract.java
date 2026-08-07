package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Stable, UI-independent graph contract used by navigation, voice, search and future workflow layers.
 *
 * <p>The Android Manifest graph id remains the legacy node identity. Capability identity is derived
 * from origin + graph id unless an explicit capabilityId is supplied. Parent/position changes never
 * participate in capability identity.</p>
 */
final class GraphContract {
    static final int CONTRACT_VERSION = 1;
    static final String REGISTRY_FORMAT = "amin-node-registry";
    static final String EDGE_FORMAT = "amin-typed-edges";
    static final String APP_ORIGIN = "app";
    static final String HIERARCHY_RELATIONSHIP = "contains";
    static final String HIERARCHY_CLASS = "hierarchy";
    static final String MANIFEST_AUTHORITY = "android_manifest";

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

    static String nodeRegistryJson(String appNavigationJson) {
        JSONObject out = new JSONObject();
        JSONArray nodes = new JSONArray();
        JSONArray errors = new JSONArray();
        try {
            JSONObject graph = new JSONObject(appNavigationJson == null ? "{}" : appNavigationJson);
            Set<String> capabilityIds = new HashSet<>();

            String rootRawId = clean(graph.optString("rootId", "app-core"));
            if (rootRawId.isEmpty()) rootRawId = "app-core";
            JSONObject root = registryNode(
                    rootRawId,
                    graph.optString("rootTitle", "Amin Pocket"),
                    graph.optString("rootCapabilityId", ""),
                    "",
                    graph.optString("rootRoute", "amin-home://open"),
                    "",
                    true);
            appendUnique(nodes, errors, capabilityIds, root);

            JSONArray pages = graph.optJSONArray("pages");
            if (pages != null) {
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject page = pages.optJSONObject(i);
                    if (page == null) continue;
                    String rawId = clean(page.optString("id", ""));
                    if (rawId.isEmpty()) {
                        errors.put("page[" + i + "] missing id");
                        continue;
                    }
                    JSONObject node = registryNode(
                            rawId,
                            page.optString("title", rawId),
                            page.optString("capabilityId", ""),
                            page.optString("parent", rootRawId),
                            page.optString("route", ""),
                            page.optString("activity", ""),
                            page.optBoolean("locked", true));
                    appendUnique(nodes, errors, capabilityIds, node);
                }
            }

            out.put("format", REGISTRY_FORMAT);
            out.put("version", CONTRACT_VERSION);
            out.put("identityRule", "capability_id_is_independent_of_parent_and_position");
            out.put("sourceAuthority", MANIFEST_AUTHORITY);
            out.put("valid", errors.length() == 0);
            out.put("errors", errors);
            out.put("nodes", nodes);
        } catch (JSONException e) {
            try {
                out.put("format", REGISTRY_FORMAT);
                out.put("version", CONTRACT_VERSION);
                out.put("valid", false);
                out.put("errors", new JSONArray().put("invalid app navigation json"));
                out.put("nodes", nodes);
            } catch (JSONException ignored) {}
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
                    JSONObject edge = new JSONObject();
                    edge.put("source", nodeId(APP_ORIGIN, parentRawId));
                    edge.put("target", nodeId(APP_ORIGIN, childRawId));
                    edge.put("relationshipType", HIERARCHY_RELATIONSHIP);
                    edge.put("relationshipClass", HIERARCHY_CLASS);
                    edge.put("authority", MANIFEST_AUTHORITY);
                    edge.put("structural", true);
                    edges.put(edge);
                }
            }
            out.put("format", EDGE_FORMAT);
            out.put("version", CONTRACT_VERSION);
            out.put("hierarchyAuthority", MANIFEST_AUTHORITY);
            out.put("edges", edges);
        } catch (JSONException e) {
            try {
                out.put("format", EDGE_FORMAT);
                out.put("version", CONTRACT_VERSION);
                out.put("edges", edges);
            } catch (JSONException ignored) {}
        }
        return out.toString();
    }

    private static JSONObject registryNode(
            String rawId,
            String title,
            String explicitCapabilityId,
            String parentRawId,
            String route,
            String activity,
            boolean locked) throws JSONException {
        JSONObject node = new JSONObject();
        node.put("nodeId", nodeId(APP_ORIGIN, rawId));
        node.put("rawId", rawId);
        node.put("capabilityId", capabilityId(APP_ORIGIN, rawId, explicitCapabilityId));
        node.put("origin", APP_ORIGIN);
        node.put("title", title == null ? rawId : title);
        node.put("parentNodeId", clean(parentRawId).isEmpty() ? "" : nodeId(APP_ORIGIN, parentRawId));
        node.put("route", route == null ? "" : route);
        node.put("activity", activity == null ? "" : activity);
        node.put("locked", locked);
        return node;
    }

    private static void appendUnique(
            JSONArray nodes,
            JSONArray errors,
            Set<String> capabilityIds,
            JSONObject node) throws JSONException {
        String id = node.optString("capabilityId", "");
        if (id.isEmpty()) {
            errors.put("node missing capabilityId: " + node.optString("rawId", ""));
            return;
        }
        if (!capabilityIds.add(id)) {
            errors.put("duplicate capabilityId: " + id);
            return;
        }
        nodes.put(node);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
