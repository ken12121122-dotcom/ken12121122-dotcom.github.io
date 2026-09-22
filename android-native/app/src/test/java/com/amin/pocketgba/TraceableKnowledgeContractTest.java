package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class TraceableKnowledgeContractTest {
    @Test public void resultIsCandidateOfflineAndSourceBacked() throws Exception {
        JSONObject result = TraceableKnowledgeContract.result("query:1", "安全設備",
                new JSONArray().put(new JSONObject().put("chunk_id", "chunk:1")),
                new JSONArray().put(new JSONObject().put("source_id", "source:1")),
                new JSONArray());
        assertEquals(TraceableKnowledgeContract.RESULT_FORMAT, result.getString("format"));
        assertEquals("generated", result.getString("review_status"));
        assertTrue(result.getBoolean("offline"));
        assertEquals(1, result.getJSONArray("source_records").length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSourceWithoutAuthority() throws Exception {
        TraceableKnowledgeContract.requireSource(new JSONObject()
                .put("source_id", "source:1").put("source_type", "fixture"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonHttpsSourceUrl() throws Exception {
        TraceableKnowledgeContract.requireSource(new JSONObject()
                .put("source_id", "source:1").put("source_type", "fixture")
                .put("authority", "test_fixture").put("source_url", "http://example.test"));
    }
}
