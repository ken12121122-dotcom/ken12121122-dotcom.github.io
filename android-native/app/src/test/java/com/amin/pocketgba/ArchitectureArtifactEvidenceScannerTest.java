package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class ArchitectureArtifactEvidenceScannerTest {
    @Test public void emitsOnlyExactExistingArtifactsAndNoAuthorityRelations() throws Exception {
        JSONArray tree = new JSONArray()
                .put(blob("android-native/app/src/main/java/com/amin/pocketgba/VoiceCommandCatalog.java", "sha-command"))
                .put(blob("android-native/app/src/main/java/com/amin/pocketgba/NodeRegistry.java", "sha-registry"))
                .put(blob("android-native/app/src/main/java/com/amin/pocketgba/PermissionCenterActivity.java", "sha-permission"))
                .put(new JSONObject()
                        .put("path", "android-native/app/src/main/java/com/amin/pocketgba/CapabilitySourceMap.java")
                        .put("type", "tree")
                        .put("sha", "not-a-blob"));

        JSONObject evidence = ArchitectureArtifactEvidenceScanner.scan(tree, "revision-1");
        JSONArray entities = evidence.getJSONArray("entities");
        assertEquals(3, entities.length());
        assertEquals(0, evidence.getJSONArray("relations").length());

        String payload = evidence.toString();
        assertTrue(payload.contains("VoiceCommandCatalog.java"));
        assertTrue(payload.contains("NodeRegistry.java"));
        assertTrue(payload.contains("PermissionCenterActivity.java"));
        assertFalse(payload.contains("CapabilitySourceMap.java"));
        assertFalse(payload.contains("owner:"));

        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.getJSONObject(i);
            assertEquals("source_artifact", entity.getString("kind"));
            assertEquals("static_verified", entity.getString("verification"));
            assertFalse(entity.getBoolean("authorityVerified"));
            assertEquals("tree_path_exact", entity.getJSONObject("evidence").getString("evidenceKind"));
        }
    }

    private static JSONObject blob(String path, String sha) throws Exception {
        return new JSONObject().put("path", path).put("type", "blob").put("sha", sha);
    }
}
