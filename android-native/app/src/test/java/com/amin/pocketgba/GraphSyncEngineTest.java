package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class GraphSyncEngineTest {
    @Test public void repeatedSyncKeepsStableIdentityWithoutDuplicates() throws Exception {
        JSONObject input = canonical("r1", true);
        JSONObject first = GraphSyncEngine.sync(null, input, 1000L);
        JSONObject second = GraphSyncEngine.sync(first, input, 2000L);

        assertEquals(2, first.getJSONArray("entities").length());
        assertEquals(1, first.getJSONArray("relations").length());
        assertEquals(2, second.getJSONArray("entities").length());
        assertEquals(1, second.getJSONArray("relations").length());
        assertEquals(0, second.getJSONObject("syncStats").getInt("entityAdded"));
        assertEquals(2, second.getJSONObject("syncStats").getInt("entityKept"));
        assertEquals(0, second.getJSONObject("syncStats").getInt("relationAdded"));
        assertEquals(1, second.getJSONObject("syncStats").getInt("relationKept"));
        assertEquals(1000L, find(second.getJSONArray("entities"), "scanner").getLong("firstSeen"));
        assertEquals(2000L, find(second.getJSONArray("entities"), "scanner").getLong("lastSeen"));
        assertEquals("unchanged", find(second.getJSONArray("entities"), "scanner").getString("change"));
    }

    @Test public void duplicateIncomingIdsCollapseToOneStableEntity() throws Exception {
        JSONObject input = canonical("r1", true);
        input.getJSONArray("entities").put(entity("caller", "r1"));
        JSONObject synced = GraphSyncEngine.sync(null, input, 1000L);
        assertEquals(2, synced.getJSONArray("entities").length());
        assertEquals(1, synced.getJSONArray("relations").length());
    }

    @Test public void missingEvidenceBecomesStaleInsteadOfDeletion() throws Exception {
        JSONObject first = GraphSyncEngine.sync(null, canonical("r1", true), 1000L);
        JSONObject second = GraphSyncEngine.sync(first, canonical("r2", false), 2000L);

        assertEquals(2, second.getJSONArray("entities").length());
        assertEquals(1, second.getJSONArray("relations").length());
        assertEquals("stale", find(second.getJSONArray("entities"), "caller").getString("status"));
        assertEquals("missing", find(second.getJSONArray("entities"), "caller").getString("change"));
        assertEquals(2000L, find(second.getJSONArray("entities"), "caller").getLong("missingSince"));
        assertEquals("stale", second.getJSONArray("relations").getJSONObject(0).getString("status"));
        assertEquals(1, second.getJSONObject("syncStats").getInt("entityMissing"));
        assertEquals(1, second.getJSONObject("syncStats").getInt("relationMissing"));
    }

    @Test public void recoveredEvidenceReturnsSameIdentityToActive() throws Exception {
        JSONObject first = GraphSyncEngine.sync(null, canonical("r1", true), 1000L);
        JSONObject missing = GraphSyncEngine.sync(first, canonical("r2", false), 2000L);
        JSONObject recovered = GraphSyncEngine.sync(missing, canonical("r3", true), 3000L);

        JSONObject caller = find(recovered.getJSONArray("entities"), "caller");
        assertEquals("caller", caller.getString("entityId"));
        assertEquals("active", caller.getString("status"));
        assertEquals(1000L, caller.getLong("firstSeen"));
        assertEquals(3000L, caller.getLong("lastSeen"));
        assertEquals("unchanged", caller.getString("change"));
        assertEquals("active", recovered.getJSONArray("relations").getJSONObject(0).getString("status"));
        assertEquals(0, recovered.getJSONObject("syncStats").getInt("entityAdded"));
        assertEquals(2, recovered.getJSONObject("syncStats").getInt("entityKept"));
    }

    @Test public void changedEvidenceUpdatesHashWithoutChangingEntityIdentity() throws Exception {
        JSONObject firstInput = canonical("r1", true);
        JSONObject first = GraphSyncEngine.sync(null, firstInput, 1000L);
        JSONObject changed = canonical("r2", true);
        changed.getJSONArray("entities").getJSONObject(0).getJSONObject("evidence").put("blobSha", "changed");
        JSONObject second = GraphSyncEngine.sync(first, changed, 2000L);

        JSONObject before = find(first.getJSONArray("entities"), "scanner");
        JSONObject after = find(second.getJSONArray("entities"), "scanner");
        assertEquals(before.getString("entityId"), after.getString("entityId"));
        assertNotEquals(before.getString("sourceHash"), after.getString("sourceHash"));
        assertEquals("updated", after.getString("change"));
    }

    @Test public void malformedIncomingEvidencePreservesLastCommittedState() throws Exception {
        JSONObject first = GraphSyncEngine.sync(null, canonical("r1", true), 1000L);
        JSONObject malformed = new JSONObject().put("format", "wrong-format");
        JSONObject second = GraphSyncEngine.sync(first, malformed, 2000L);

        assertEquals(first.toString(), second.toString());
    }

    private static JSONObject canonical(String revision, boolean includeCaller) throws Exception {
        JSONArray entities = new JSONArray().put(entity("scanner", revision));
        JSONArray relations = new JSONArray();
        if (includeCaller) {
            entities.put(entity("caller", revision));
            relations.put(new JSONObject()
                    .put("relationId", "rel:scanner>caller")
                    .put("id", "rel:scanner>caller")
                    .put("from", "scanner")
                    .put("to", "caller")
                    .put("type", "invoked_by")
                    .put("verification", "ast_verified")
                    .put("evidenceRevision", revision)
                    .put("evidence", new JSONObject().put("path", "Caller.java").put("nodeType", "method_invocation")));
        }
        return new JSONObject()
                .put("format", CanonicalEvidenceAdapter.FORMAT)
                .put("version", CanonicalEvidenceAdapter.VERSION)
                .put("revision", revision)
                .put("anchorId", "scanner")
                .put("status", "indexed")
                .put("entities", entities)
                .put("relations", relations);
    }

    private static JSONObject entity(String id, String revision) throws Exception {
        return new JSONObject()
                .put("entityId", id)
                .put("id", id)
                .put("title", id)
                .put("kind", "source")
                .put("verification", "ast_verified")
                .put("sourceKey", "repo|" + id)
                .put("evidenceRevision", revision)
                .put("evidence", new JSONObject().put("path", id + ".java").put("nodeType", "method_declaration"));
    }

    private static JSONObject find(JSONArray array, String id) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (id.equals(item.optString("entityId", item.optString("id", "")))) return item;
        }
        throw new AssertionError("missing entity " + id);
    }
}
