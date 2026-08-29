package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class CanonicalEvidenceBatchMergerTest {
    @Test public void mergesProvidersByStableIdentityWithoutInventingLayers() throws Exception {
        JSONObject a = CanonicalEvidenceAdapter.empty()
                .put("provider", "source-text")
                .put("revision", "r1")
                .put("anchorId", "function:Scanner.sync")
                .put("entities", new JSONArray()
                        .put(new JSONObject()
                                .put("entityId", "function:Scanner.sync")
                                .put("title", "Scanner.sync")
                                .put("kind", "scanner")
                                .put("verification", "static_verified")
                                .put("sourceKey", "repo|Scanner.java|sync")))
                .put("relations", new JSONArray());

        JSONObject b = CanonicalEvidenceAdapter.empty()
                .put("provider", "tree-sitter-java")
                .put("revision", "r1")
                .put("anchorId", "function:Scanner.sync")
                .put("entities", new JSONArray()
                        .put(new JSONObject()
                                .put("entityId", "function:Scanner.sync")
                                .put("title", "Scanner.sync")
                                .put("kind", "scanner")
                                .put("verification", "ast_verified")
                                .put("sourceKey", "repo|Scanner.java|sync"))
                        .put(new JSONObject()
                                .put("entityId", "function:Caller.run")
                                .put("title", "Caller.run")
                                .put("kind", "source")
                                .put("verification", "ast_verified")
                                .put("sourceKey", "repo|Caller.java|run")))
                .put("relations", new JSONArray()
                        .put(new JSONObject()
                                .put("relationId", "relation:invoked_by:function:Scanner.sync>function:Caller.run")
                                .put("from", "function:Scanner.sync")
                                .put("to", "function:Caller.run")
                                .put("type", "invoked_by")
                                .put("verification", "ast_verified")))
                .put("gap", new JSONObject()
                        .put("code", "SCANNER_COMMAND_EVIDENCE_NOT_FOUND")
                        .put("expectedLayer", "command"));

        JSONObject merged = CanonicalEvidenceBatchMerger.merge("r1", a, b);
        assertEquals(2, merged.getJSONArray("entities").length());
        assertEquals(1, merged.getJSONArray("relations").length());
        assertEquals(2, merged.getJSONArray("providers").length());
        assertEquals("ast_verified", merged.getJSONArray("entities").getJSONObject(0).getString("verification"));
        assertEquals("SCANNER_COMMAND_EVIDENCE_NOT_FOUND", merged.getJSONObject("gap").getString("code"));
        String payload = merged.toString();
        assertTrue(!payload.contains("owner:"));
        assertTrue(!payload.contains("governance:"));
        assertTrue(!payload.contains("registry:"));
    }
}
