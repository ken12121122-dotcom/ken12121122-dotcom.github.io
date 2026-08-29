package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class BundledIndexerEvidenceProviderTest {
    @Test
    public void matchingRevisionAllowsCanonicalEvidence() throws Exception {
        JSONObject indexed = indexed("rev-123");
        JSONObject canonical = BundledIndexerEvidenceProvider.canonicalForRevision(indexed, "rev-123");

        assertEquals("matched", canonical.optString("revisionGate"));
        assertEquals("rev-123", canonical.optString("revision"));
        assertEquals(1, canonical.getJSONArray("entities").length());
    }

    @Test
    public void mismatchedRevisionReturnsMetadataWithoutGraphEvidence() throws Exception {
        JSONObject canonical = BundledIndexerEvidenceProvider.canonicalForRevision(indexed("feature-sha"), "release-sha");

        assertEquals(BundledIndexerEvidenceProvider.REVISION_MISMATCH, canonical.optString("revisionGate"));
        assertEquals("feature-sha", canonical.optString("bundledRevision"));
        assertEquals("release-sha", canonical.optString("liveRevision"));
        assertEquals(0, canonical.getJSONArray("entities").length());
        assertEquals(0, canonical.getJSONArray("relations").length());
        assertNotNull(canonical.optJSONObject("gap"));
    }

    @Test
    public void missingRevisionReturnsMetadataWithoutGraphEvidence() throws Exception {
        JSONObject canonical = BundledIndexerEvidenceProvider.canonicalForRevision(indexed(""), "release-sha");

        assertEquals(BundledIndexerEvidenceProvider.REVISION_MISSING, canonical.optString("revisionGate"));
        assertEquals(0, canonical.getJSONArray("entities").length());
        assertEquals(0, canonical.getJSONArray("relations").length());
    }

    private static JSONObject indexed(String revision) throws Exception {
        return new JSONObject()
                .put("format", IndexerEvidenceAdapter.FORMAT)
                .put("version", IndexerEvidenceAdapter.VERSION)
                .put("provider", "tree-sitter-java")
                .put("revision", revision)
                .put("anchor", new JSONObject()
                        .put("entityId", "function:GitHubSourceGraphScanner.syncAsync")
                        .put("title", "Scanner · syncAsync")
                        .put("kind", "function")
                        .put("verification", "ast_verified"))
                .put("relations", new JSONArray())
                .put("artifacts", new JSONArray())
                .put("structuralRelations", new JSONArray())
                .put("gaps", new JSONArray());
    }
}
