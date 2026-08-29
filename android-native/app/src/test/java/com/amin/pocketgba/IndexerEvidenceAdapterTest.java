package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class IndexerEvidenceAdapterTest {
    @Test public void mapsTreeSitterEvidenceWithoutInventingAuthority() throws Exception {
        JSONObject anchorEvidence = new JSONObject()
                .put("provider", "tree-sitter-java")
                .put("path", "android-native/app/src/main/java/com/amin/pocketgba/GitHubSourceGraphScanner.java")
                .put("nodeType", "method_declaration")
                .put("symbol", "GitHubSourceGraphScanner.syncAsync");
        JSONObject relationEvidence = new JSONObject()
                .put("provider", "tree-sitter-java")
                .put("path", "android-native/app/src/main/java/com/amin/pocketgba/WikiGraphActivity.java")
                .put("nodeType", "method_invocation")
                .put("callerSymbol", "WikiGraphActivity.requestCloudSourceSync")
                .put("calleeSymbol", "GitHubSourceGraphScanner.syncAsync");

        JSONObject input = new JSONObject()
                .put("format", IndexerEvidenceAdapter.FORMAT)
                .put("version", IndexerEvidenceAdapter.VERSION)
                .put("provider", "tree-sitter-java")
                .put("providerVersion", "0.23.5")
                .put("parserVersion", "0.21.1")
                .put("revision", "abc123")
                .put("anchor", new JSONObject()
                        .put("entityId", "function:GitHubSourceGraphScanner.syncAsync")
                        .put("title", "Scanner · syncAsync")
                        .put("kind", "function")
                        .put("verification", "ast_verified")
                        .put("evidence", anchorEvidence))
                .put("relations", new JSONArray().put(new JSONObject()
                        .put("relationId", "relation:invoked_by:function:GitHubSourceGraphScanner.syncAsync>function:WikiGraphActivity.requestCloudSourceSync")
                        .put("from", "function:GitHubSourceGraphScanner.syncAsync")
                        .put("to", "function:WikiGraphActivity.requestCloudSourceSync")
                        .put("type", "invoked_by")
                        .put("verification", "ast_verified")
                        .put("evidence", relationEvidence)))
                .put("gaps", new JSONArray().put(new JSONObject()
                        .put("after", "function:WikiGraphActivity.requestCloudSourceSync")
                        .put("expectedLayer", "command")
                        .put("code", "SCANNER_COMMAND_EVIDENCE_NOT_FOUND")
                        .put("reason", "No formal command ownership evidence")));

        JSONObject canonical = IndexerEvidenceAdapter.toCanonical(input);
        assertEquals(CanonicalEvidenceAdapter.FORMAT, canonical.getString("format"));
        assertEquals("tree-sitter-java", canonical.getString("provider"));
        assertEquals("abc123", canonical.getString("revision"));
        assertEquals(3, canonical.getJSONArray("entities").length());
        assertEquals(2, canonical.getJSONArray("relations").length());
        assertEquals("SCANNER_COMMAND_EVIDENCE_NOT_FOUND", canonical.getJSONObject("gap").getString("code"));
        assertTrue(canonical.toString().contains("ast_verified"));
        assertTrue(canonical.toString().contains("COMMAND GAP"));

        JSONArray entities = canonical.getJSONArray("entities");
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.getJSONObject(i);
            String id = entity.optString("entityId", entity.optString("id", ""));
            String kind = entity.optString("kind", "");
            assertFalse(id.startsWith("owner:"));
            assertFalse(id.startsWith("governance:"));
            assertFalse("owner".equals(kind));
            assertFalse("governance".equals(kind));
        }
    }
}
