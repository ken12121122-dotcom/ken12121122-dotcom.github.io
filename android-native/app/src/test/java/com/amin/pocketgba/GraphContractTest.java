package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class GraphContractTest {
    private static String graph(String parent, String explicitCapabilityId) throws Exception {
        JSONObject root = new JSONObject();
        root.put("rootId", "app-core");
        root.put("rootTitle", "Amin Pocket");
        JSONArray pages = new JSONArray();
        JSONObject page = new JSONObject();
        page.put("id", "graph");
        page.put("title", "關聯圖");
        page.put("parent", parent);
        page.put("route", "amin-graph://open");
        page.put("activity", "com.amin.pocketgba.WikiGraphActivity");
        if (explicitCapabilityId != null) page.put("capabilityId", explicitCapabilityId);
        pages.put(page);
        root.put("pages", pages);
        return root.toString();
    }

    @Test public void capabilityIdentityDoesNotDependOnParent() throws Exception {
        JSONObject before = new JSONObject(GraphContract.nodeRegistryJson(graph("control-center", null)));
        JSONObject after = new JSONObject(GraphContract.nodeRegistryJson(graph("voice", null)));
        String beforeId = before.getJSONArray("nodes").getJSONObject(1).getString("capabilityId");
        String afterId = after.getJSONArray("nodes").getJSONObject(1).getString("capabilityId");
        assertEquals("app:graph", beforeId);
        assertEquals(beforeId, afterId);
        assertTrue(before.getBoolean("valid"));
        assertTrue(after.getBoolean("valid"));
    }

    @Test public void explicitCapabilityIdentityIsPreserved() throws Exception {
        JSONObject registry = new JSONObject(GraphContract.nodeRegistryJson(graph("control-center", "capability.graph.open")));
        JSONObject node = registry.getJSONArray("nodes").getJSONObject(1);
        assertEquals("capability.graph.open", node.getString("capabilityId"));
        assertEquals("app:graph", node.getString("nodeId"));
    }

    @Test public void hierarchyIsRepresentedAsTypedEdge() throws Exception {
        JSONObject typed = new JSONObject(GraphContract.typedEdgesJson(graph("control-center", null)));
        JSONObject edge = typed.getJSONArray("edges").getJSONObject(0);
        assertEquals("app:control-center", edge.getString("source"));
        assertEquals("app:graph", edge.getString("target"));
        assertEquals("contains", edge.getString("relationshipType"));
        assertEquals("hierarchy", edge.getString("relationshipClass"));
        assertEquals("android_manifest", edge.getString("authority"));
        assertTrue(edge.getBoolean("structural"));
    }

    @Test public void duplicateCapabilityIdsAreRejectedFromRegistry() throws Exception {
        JSONObject root = new JSONObject();
        root.put("rootId", "app-core");
        JSONArray pages = new JSONArray();
        pages.put(new JSONObject().put("id", "a").put("capabilityId", "capability.shared"));
        pages.put(new JSONObject().put("id", "b").put("capabilityId", "capability.shared"));
        root.put("pages", pages);
        JSONObject registry = new JSONObject(GraphContract.nodeRegistryJson(root.toString()));
        assertFalse(registry.getBoolean("valid"));
        assertEquals(2, registry.getJSONArray("nodes").length());
        assertTrue(registry.getJSONArray("errors").getString(0).contains("duplicate capabilityId"));
    }
}
