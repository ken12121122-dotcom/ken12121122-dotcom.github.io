package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class CapabilityInventoryAdapterTest {
    @Test public void approvedRegistryAndCommandsUseExistingStableIds() throws Exception {
        JSONObject registry = registry("Graph");
        JSONObject edges = edges();
        JSONObject registryBatch = CapabilityInventoryAdapter.registryBatch(registry, edges, 1000L);
        JSONObject commandBatch = CapabilityInventoryAdapter.commandBatch(commands(), 1000L);

        assertEquals("app:graph", registryBatch.getJSONArray("entities").getJSONObject(1)
                .getString("stable_id"));
        assertEquals("edge:root:graph", registryBatch.getJSONArray("relations").getJSONObject(0)
                .getString("stable_id"));
        assertEquals("command:open_graph", commandBatch.getJSONArray("entities").getJSONObject(0)
                .getString("stable_id"));
        assertFalse(commandBatch.getJSONArray("entities").getJSONObject(0)
                .getJSONObject("payload").getBoolean("execution_enabled"));
    }

    @Test public void sharedKernelPreservesIdentityAndIsolatesCommandStaleState() throws Exception {
        JSONObject first = SharedGraphSyncKernel.apply(null,
                CapabilityInventoryAdapter.registryBatch(registry("Graph"), edges(), 1000L), 1000L);
        assertTrue(first.optString("errorCode"), first.getBoolean("success"));
        JSONObject second = SharedGraphSyncKernel.apply(first.getJSONObject("state"),
                CapabilityInventoryAdapter.commandBatch(commands(), 1100L), 1100L);
        assertTrue(second.optString("errorCode"), second.getBoolean("success"));
        JSONObject third = SharedGraphSyncKernel.apply(second.getJSONObject("state"),
                CapabilityInventoryAdapter.commandBatch(new JSONArray(), 2000L), 2000L);
        assertTrue(third.optString("errorCode"), third.getBoolean("success"));

        JSONObject state = third.getJSONObject("state");
        assertEquals("active", find(state.getJSONArray("entities"), "app:graph").getString("sync_status"));
        assertEquals("stale", find(state.getJSONArray("entities"), "command:open_graph").getString("sync_status"));
        assertEquals(1000L, find(state.getJSONArray("entities"), "app:graph").getLong("firstSeen"));
    }

    @Test public void mutableRegistryTitleUpdatesFingerprintButNotIdentity() throws Exception {
        JSONObject before = CapabilityInventoryAdapter.registryBatch(registry("Graph"), edges(), 1000L)
                .getJSONArray("entities").getJSONObject(1);
        JSONObject after = CapabilityInventoryAdapter.registryBatch(registry("Capability Graph"), edges(), 2000L)
                .getJSONArray("entities").getJSONObject(1);
        assertEquals(before.getString("stable_id"), after.getString("stable_id"));
        assertFalse(before.getString("content_fingerprint").equals(after.getString("content_fingerprint")));
    }

    private static JSONObject registry(String title) throws Exception {
        return new JSONObject().put("format", "amin-node-registry").put("version", 1)
                .put("valid", true).put("sourceAuthority", "android_manifest")
                .put("nodes", new JSONArray()
                        .put(node("app:app-core", "app:app-core", "Amin"))
                        .put(node("app:graph", "app:graph", title)));
    }

    private static JSONObject node(String nodeId, String capabilityId, String title) throws Exception {
        return new JSONObject().put("node_id", nodeId).put("capability_id", capabilityId)
                .put("name", title).put("node_type", "capability").put("status", "active")
                .put("version", "1").put("actions", new JSONArray().put("open"));
    }

    private static JSONObject edges() throws Exception {
        return new JSONObject().put("edges", new JSONArray().put(new JSONObject()
                .put("edge_id", "edge:root:graph").put("source", "app:app-core")
                .put("target", "app:graph").put("relationship_type", "contains")
                .put("authority", "android_manifest")));
    }

    private static JSONArray commands() throws Exception {
        return new JSONArray().put(new JSONObject().put("id", "open_graph")
                .put("title", "Open Graph").put("action", "OPEN_GRAPH")
                .put("phrases", new JSONArray().put("open graph"))
                .put("authority", "test"));
    }

    private static JSONObject find(JSONArray array, String id) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (id.equals(item.optString("stable_id", ""))) return item;
        }
        throw new AssertionError("Missing " + id);
    }
}
