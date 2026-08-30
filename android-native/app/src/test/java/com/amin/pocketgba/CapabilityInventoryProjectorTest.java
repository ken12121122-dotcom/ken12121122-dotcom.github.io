package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class CapabilityInventoryProjectorTest {
    @Test public void projectsReadOnlyInventoryIntoExistingUnifiedGraphShape() throws Exception {
        JSONObject batch = CapabilityInventoryAdapter.commandBatch(new JSONArray().put(new JSONObject()
                .put("id", "open_graph").put("title", "Open Graph")
                .put("action", "OPEN_GRAPH").put("phrases", new JSONArray().put("open graph"))), 1000L);
        JSONObject result = SharedGraphSyncKernel.apply(null, batch, 1000L);
        assertTrue(result.optString("errorCode"), result.getBoolean("success"));
        JSONObject unified = new JSONObject().put("nodes", new JSONArray())
                .put("commands", new JSONArray()).put("relations", new JSONArray());

        CapabilityInventoryProjector.append(unified, result.getJSONObject("state"));

        assertEquals(1, unified.getJSONArray("nodes").length());
        assertEquals(1, unified.getJSONArray("commands").length());
        JSONObject node = unified.getJSONArray("nodes").getJSONObject(0);
        assertEquals("command:open_graph", node.getString("id"));
        assertEquals("none", new JSONObject(node.getString("detail")).getString("autonomy"));
        assertFalse(new JSONObject(node.getString("detail")).getBoolean("execution_enabled"));
        assertEquals("amin-shared-graph-sync-state",
                unified.getJSONObject("capabilityInventory").getString("format"));
    }
}
