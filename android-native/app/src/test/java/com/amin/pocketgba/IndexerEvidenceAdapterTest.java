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

        JSONObject input = validInput(anchorEvidence, relationEvidence);
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

    @Test public void preservesStructuralReferencesAsDependencyEvidenceOnly() throws Exception {
        JSONObject input = validInput(new JSONObject(), new JSONObject())
                .put("artifacts", new JSONArray()
                        .put(new JSONObject()
                                .put("layer", "command")
                                .put("entityId", "source-artifact:VoiceCommandCatalog")
                                .put("className", "VoiceCommandCatalog")
                                .put("path", "VoiceCommandCatalog.java")
                                .put("verification", "ast_verified")
                                .put("authorityVerified", false)
                                .put("declaration", new JSONObject().put("nodeType", "class_declaration")))
                        .put(new JSONObject()
                                .put("layer", "registry")
                                .put("entityId", "source-artifact:NodeRegistry")
                                .put("className", "NodeRegistry")
                                .put("path", "NodeRegistry.java")
                                .put("verification", "ast_verified")
                                .put("authorityVerified", false)
                                .put("declaration", new JSONObject().put("nodeType", "class_declaration"))))
                .put("structuralRelations", new JSONArray().put(new JSONObject()
                        .put("relationId", "relation:references:source-artifact:VoiceCommandCatalog>source-artifact:NodeRegistry")
                        .put("from", "source-artifact:VoiceCommandCatalog")
                        .put("to", "source-artifact:NodeRegistry")
                        .put("type", "references")
                        .put("relationClass", "DEPENDENCY")
                        .put("verification", "ast_verified")
                        .put("authorityVerified", false)
                        .put("evidence", new JSONObject()
                                .put("provider", "tree-sitter-java")
                                .put("path", "SomeMediator.java")
                                .put("nodeType", "type_identifier"))));

        JSONObject canonical = IndexerEvidenceAdapter.toCanonical(input);
        JSONArray entities = canonical.getJSONArray("entities");
        JSONArray relations = canonical.getJSONArray("relations");
        assertTrue(containsEntity(entities, "source-artifact:VoiceCommandCatalog"));
        assertTrue(containsEntity(entities, "source-artifact:NodeRegistry"));

        JSONObject structural = findRelation(relations,
                "relation:references:source-artifact:VoiceCommandCatalog>source-artifact:NodeRegistry");
        assertEquals("references", structural.getString("type"));
        assertEquals("DEPENDENCY", structural.getString("relationClass"));
        assertEquals("ast_verified", structural.getString("verification"));
        assertFalse(structural.getBoolean("authorityVerified"));
    }

    @Test public void unsupportedIndexerVersionProducesEmptyCanonicalEvidence() throws Exception {
        JSONObject input = validInput(new JSONObject(), new JSONObject());
        input.put("version", IndexerEvidenceAdapter.VERSION + 1);
        JSONObject canonical = IndexerEvidenceAdapter.toCanonical(input);
        assertEquals(0, canonical.getJSONArray("entities").length());
        assertEquals(0, canonical.getJSONArray("relations").length());
    }

    private static boolean containsEntity(JSONArray entities, String id) {
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity != null && id.equals(entity.optString("entityId", ""))) return true;
        }
        return false;
    }

    private static JSONObject findRelation(JSONArray relations, String id) {
        for (int i = 0; i < relations.length(); i++) {
            JSONObject relation = relations.optJSONObject(i);
            if (relation != null && id.equals(relation.optString("relationId", ""))) return relation;
        }
        return new JSONObject();
    }

    private static JSONObject validInput(JSONObject anchorEvidence, JSONObject relationEvidence) throws Exception {
        return new JSONObject()
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
    }
}
