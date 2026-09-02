package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class CapabilityResolverTest {
    @Test public void detectsCapabilityQuestionsWithoutCapturingOrdinaryChat() {
        assertTrue(CapabilityResolver.isCapabilityQuestion("你目前有哪些能力？"));
        assertTrue(CapabilityResolver.isCapabilityQuestion("有沒有記帳能力"));
        assertFalse(CapabilityResolver.isCapabilityQuestion("今天天氣如何"));
        assertFalse(CapabilityResolver.isCapabilityQuestion("財務有什麼功能？"));
    }

    @Test public void listsInventoryThroughReadOnlyBoundary() throws Exception {
        JSONObject context = context();
        JSONObject result = CapabilityResolver.resolve("你目前有哪些能力？", context);

        assertEquals("resolved_existing_capability", result.getString("resolution_status"));
        assertEquals("existing_capability", result.getString("resolution_stage"));
        assertFalse(result.getJSONObject("boundary").getBoolean("execution_allowed"));
        assertFalse(result.getJSONObject("boundary").getBoolean("graph_mutation_allowed"));
        assertFalse(result.getJSONObject("boundary").getBoolean("github_write_allowed"));
        assertEquals(2, result.getJSONArray("matches").length());
        assertTrue(result.getString("answer").contains("2 項能力"));
        assertTrue(result.getString("answer").contains("沒有執行指令"));
        assertTrue(result.getString("spoken_answer").contains("完整清單已顯示在畫面上"));
    }

    @Test public void resolvesExistingCapabilityAndRoutesMissingToRepoInspection() throws Exception {
        JSONObject found = CapabilityResolver.resolve("有沒有記帳能力", context());
        assertEquals(1, found.getJSONArray("matches").length());
        assertEquals("existing_capability", found.getString("resolution_stage"));

        JSONObject missing = CapabilityResolver.resolve("有沒有火星採礦能力", context());
        assertEquals(0, missing.getJSONArray("matches").length());
        assertEquals("repository_implementation", missing.getString("resolution_stage"));
        assertTrue(missing.getString("answer").contains("沒有建立新能力"));
    }

    private static JSONObject context() throws Exception {
        JSONObject finance = new JSONObject()
                .put("stable_id", "finance.transaction.create")
                .put("payload", new JSONObject()
                        .put("entity_type", "CAPABILITY")
                        .put("title", "新增收支")
                        .put("description", "記帳")
                        .put("activity", ".FinanceTransactionActivity")
                        .put("input_context", new JSONObject().put("mode", "managed_md")
                                .put("context_node_id", "app:finance-transaction-create-md"))
                        .put("voice", new JSONObject().put("enabled", true)));
        JSONObject storage = new JSONObject()
                .put("stable_id", "finance.storage.transactions")
                .put("payload", new JSONObject()
                        .put("entity_type", "CAPABILITY")
                        .put("title", "Transactions Sheet")
                        .put("voice", new JSONObject().put("enabled", false)));
        return new JSONObject()
                .put("summary", new JSONObject().put("total", 2)
                        .put("chat_addressable", 1).put("bridge_required", 1))
                .put("capabilities", new JSONArray().put(finance).put(storage))
                .put("source_records", new JSONArray().put(new JSONObject()
                        .put("source_id", "node_registry").put("authority", "android_manifest")))
                .put("unresolved_gaps", new JSONArray());
    }
}
