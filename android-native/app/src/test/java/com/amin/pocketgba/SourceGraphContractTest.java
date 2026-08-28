package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class SourceGraphContractTest {
    @Test public void acceptsRepositoryHierarchyAndDeclarations() throws Exception {
        JSONObject graph = new JSONObject();
        JSONArray entities = new JSONArray()
                .put(entity("repo:amin", "repository"))
                .put(entity("branch:release/android", "branch"))
                .put(entity("file:WikiGraphActivity.java", "file"))
                .put(entity("class:WikiGraphActivity", "class"));
        JSONArray relations = new JSONArray()
                .put(relation("repo:amin", "branch:release/android", "contains"))
                .put(relation("branch:release/android", "file:WikiGraphActivity.java", "contains"))
                .put(relation("file:WikiGraphActivity.java", "class:WikiGraphActivity", "declares"));
        graph.put("entities", entities).put("relations", relations);

        JSONObject normalized = SourceGraphContract.normalize(graph);
        assertEquals(SourceGraphContract.FORMAT, normalized.getString("format"));
        assertEquals(SourceGraphContract.VERSION, normalized.getInt("version"));
        assertTrue(normalized.getBoolean("valid"));
        assertEquals(0, normalized.getJSONArray("errors").length());
    }

    @Test public void rejectsFlatUnknownEntityType() throws Exception {
        JSONObject graph = new JSONObject()
                .put("entities", new JSONArray().put(entity("x", "capability")))
                .put("relations", new JSONArray());
        JSONObject normalized = SourceGraphContract.normalize(graph);
        assertFalse(normalized.getBoolean("valid"));
        assertTrue(normalized.getJSONArray("errors").getString(0).contains("entity type invalid"));
    }

    @Test public void rejectsRelationsToMissingSourceEntities() throws Exception {
        JSONObject graph = new JSONObject()
                .put("entities", new JSONArray().put(entity("repo:amin", "repository")))
                .put("relations", new JSONArray().put(relation("repo:amin", "missing", "contains")));
        JSONObject normalized = SourceGraphContract.normalize(graph);
        assertFalse(normalized.getBoolean("valid"));
        assertTrue(normalized.getJSONArray("errors").toString().contains("relation target missing"));
    }

    private static JSONObject entity(String id, String type) throws Exception {
        return new JSONObject().put("id", id).put("entityType", type);
    }

    private static JSONObject relation(String from, String to, String type) throws Exception {
        return new JSONObject().put("from", from).put("to", to).put("type", type);
    }
}
