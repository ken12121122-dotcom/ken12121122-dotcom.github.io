package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class SharedGraphSyncKernelTest {
    @Test public void taskApprovalKeepsIdentityAndRunGetsDistinctIdentity() throws Exception {
        JSONObject suggested = batch(
                "work", owner("brain", "amin-agent-control"), "manual_tasks", true,
                new JSONArray().put(entity("task:42", "TASK", "suggested", "task-v1")),
                new JSONArray());
        JSONObject first = successState(SharedGraphSyncKernel.apply(null, suggested, 1000L));

        JSONObject approved = batch(
                "work", owner("brain", "amin-agent-control"), "manual_tasks", true,
                new JSONArray()
                        .put(entity("task:42", "TASK", "approved", "task-v2"))
                        .put(entity("run:42:1", "RUN", "queued", "run-v1")),
                new JSONArray());
        JSONObject second = successState(SharedGraphSyncKernel.apply(first, approved, 2000L));

        JSONObject task = find(second.getJSONArray("entities"), "task:42");
        JSONObject run = find(second.getJSONArray("entities"), "run:42:1");
        assertEquals("task:42", task.getString("stable_id"));
        assertEquals("approved", task.getJSONObject("payload").getString("lifecycle_status"));
        assertEquals(1000L, task.getLong("firstSeen"));
        assertEquals(2000L, task.getLong("lastSeen"));
        assertEquals("run:42:1", run.getString("stable_id"));
        assertEquals(2000L, run.getLong("firstSeen"));
    }

    @Test public void scopeOwnerAndPartitionIsolateStaleOnMissing() throws Exception {
        JSONObject state = successState(SharedGraphSyncKernel.apply(null,
                batch("knowledge", owner("brain", "amin-agent-control"), "knowledge_records", true,
                        new JSONArray().put(entity("knowledge:1", "KNOWLEDGE", "active", "k1")), new JSONArray()),
                1000L));
        state = successState(SharedGraphSyncKernel.apply(state,
                batch("work", owner("github", "ken12121122-dotcom/ken12121122-dotcom.github.io"), "pull_requests", true,
                        new JSONArray().put(entity("pr:111", "PR", "open", "pr1")), new JSONArray()),
                1100L));
        state = successState(SharedGraphSyncKernel.apply(state,
                batch("work", owner("github", "ken12121122-dotcom/ken12121122-dotcom.github.io"), "branches", true,
                        new JSONArray().put(entity("branch:release/android", "BRANCH", "active", "branch1")), new JSONArray()),
                1200L));
        state = successState(SharedGraphSyncKernel.apply(state,
                batch("work", owner("github", "ken12121122-dotcom/ken12121122-dotcom.github.io"), "check_runs", true,
                        new JSONArray().put(entity("ci:77", "CI_RUN", "success", "ci1")), new JSONArray()),
                1300L));
        state = successState(SharedGraphSyncKernel.apply(state,
                batch("work", owner("brain", "amin-agent-control"), "manual_tasks", true,
                        new JSONArray().put(entity("task:42", "TASK", "approved", "task1")), new JSONArray()),
                1400L));
        state = successState(SharedGraphSyncKernel.apply(state,
                batch("bridge", owner("brain", "amin-agent-control"), "task_knowledge_relations", true,
                        new JSONArray(), new JSONArray().put(relation(
                                "bridge:task:42>knowledge:1", "TASK", "uses_knowledge", "KNOWLEDGE", "bridge1"))),
                1500L));

        state = successState(SharedGraphSyncKernel.apply(state,
                batch("work", owner("github", "ken12121122-dotcom/ken12121122-dotcom.github.io"), "pull_requests", true,
                        new JSONArray(), new JSONArray()),
                2000L));

        assertEquals("stale", find(state.getJSONArray("entities"), "pr:111").getString("sync_status"));
        assertActive(state, "knowledge:1");
        assertActive(state, "branch:release/android");
        assertActive(state, "ci:77");
        assertActive(state, "task:42");
        assertEquals("active", find(state.getJSONArray("relations"), "bridge:task:42>knowledge:1")
                .getString("sync_status"));

        state = successState(SharedGraphSyncKernel.apply(state,
                batch("knowledge", owner("brain", "amin-agent-control"), "knowledge_records", true,
                        new JSONArray(), new JSONArray()),
                3000L));

        assertEquals("stale", find(state.getJSONArray("entities"), "knowledge:1").getString("sync_status"));
        assertEquals("active", find(state.getJSONArray("relations"), "bridge:task:42>knowledge:1")
                .getString("sync_status"));
    }

    @Test public void incompleteSnapshotUpsertsButCannotStaleMissingRecords() throws Exception {
        JSONObject initial = batch(
                "work", owner("github", "ken12121122-dotcom/ken12121122-dotcom.github.io"), "pull_requests", true,
                new JSONArray()
                        .put(entity("pr:1", "PR", "open", "pr1"))
                        .put(entity("pr:2", "PR", "open", "pr2")),
                new JSONArray());
        JSONObject state = successState(SharedGraphSyncKernel.apply(null, initial, 1000L));

        JSONObject partial = batch(
                "work", owner("github", "ken12121122-dotcom/ken12121122-dotcom.github.io"), "pull_requests", false,
                new JSONArray().put(entity("pr:1", "PR", "merged", "pr1-merged")), new JSONArray());
        JSONObject result = SharedGraphSyncKernel.apply(state, partial, 2000L);
        JSONObject next = successState(result);

        assertEquals("merged", find(next.getJSONArray("entities"), "pr:1")
                .getJSONObject("payload").getString("lifecycle_status"));
        assertEquals("active", find(next.getJSONArray("entities"), "pr:2").getString("sync_status"));
        assertEquals(0, result.getJSONObject("syncStats").getInt("entityMissing"));
    }

    @Test public void duplicateStableIdsUseDeterministicFirstWinsDedupe() throws Exception {
        JSONObject duplicates = batch(
                "knowledge", owner("brain", "amin-agent-control"), "knowledge_records", true,
                new JSONArray()
                        .put(entity("knowledge:1", "KNOWLEDGE", "active", "first"))
                        .put(entity("knowledge:1", "KNOWLEDGE", "active", "second")),
                new JSONArray());
        JSONObject result = SharedGraphSyncKernel.apply(null, duplicates, 1000L);
        JSONObject state = successState(result);

        assertEquals(1, state.getJSONArray("entities").length());
        assertEquals("first", state.getJSONArray("entities").getJSONObject(0).getString("content_fingerprint"));
        assertEquals(1, result.getJSONObject("syncStats").getInt("entityDuplicates"));
    }

    @Test public void invalidBatchPreservesLastCommittedStateAndReportsFailure() throws Exception {
        JSONObject state = successState(SharedGraphSyncKernel.apply(null,
                batch("knowledge", owner("brain", "amin-agent-control"), "knowledge_records", true,
                        new JSONArray().put(entity("knowledge:1", "KNOWLEDGE", "active", "k1")), new JSONArray()),
                1000L));
        JSONObject invalid = batch(
                "knowledge", owner("brain", "amin-agent-control"), "knowledge_records", true,
                new JSONArray().put(entity("knowledge:2", "KNOWLEDGE", "active", "k2")), new JSONArray());
        invalid.remove("provenance");

        JSONObject result = SharedGraphSyncKernel.apply(state, invalid, 2000L);

        assertFalse(result.getBoolean("success"));
        assertEquals("INVALID_BATCH_PROVENANCE", result.getString("errorCode"));
        assertEquals(state.toString(), result.getJSONObject("state").toString());
    }

    @Test public void uncommittedBatchCannotMutateOrStaleState() throws Exception {
        JSONObject state = successState(SharedGraphSyncKernel.apply(null,
                batch("work", owner("brain", "amin-agent-control"), "manual_tasks", true,
                        new JSONArray().put(entity("task:42", "TASK", "suggested", "task-v1")), new JSONArray()),
                1000L));
        JSONObject cancelled = batch(
                "work", owner("brain", "amin-agent-control"), "manual_tasks", true,
                new JSONArray(), new JSONArray()).put("committed", false);

        JSONObject result = SharedGraphSyncKernel.apply(state, cancelled, 2000L);

        assertFalse(result.getBoolean("success"));
        assertEquals("UNCOMMITTED_BATCH", result.getString("errorCode"));
        assertEquals(state.toString(), result.getJSONObject("state").toString());
    }

    @Test public void failedAndCancelledBatchesCannotMutateOrStaleState() throws Exception {
        JSONObject state = successState(SharedGraphSyncKernel.apply(null,
                batch("work", owner("brain", "amin-agent-control"), "manual_tasks", true,
                        new JSONArray().put(entity("task:42", "TASK", "suggested", "task-v1")), new JSONArray()),
                1000L));

        JSONObject failed = batch(
                "work", owner("brain", "amin-agent-control"), "manual_tasks", true,
                new JSONArray(), new JSONArray()).put("batch_status", "failed");
        JSONObject cancelled = batch(
                "work", owner("brain", "amin-agent-control"), "manual_tasks", true,
                new JSONArray(), new JSONArray()).put("batch_status", "cancelled");

        JSONObject failedResult = SharedGraphSyncKernel.apply(state, failed, 2000L);
        JSONObject cancelledResult = SharedGraphSyncKernel.apply(state, cancelled, 3000L);

        assertFalse(failedResult.getBoolean("success"));
        assertEquals("BATCH_NOT_APPLICABLE", failedResult.getString("errorCode"));
        assertEquals(state.toString(), failedResult.getJSONObject("state").toString());
        assertFalse(cancelledResult.getBoolean("success"));
        assertEquals("BATCH_NOT_APPLICABLE", cancelledResult.getString("errorCode"));
        assertEquals(state.toString(), cancelledResult.getJSONObject("state").toString());
    }

    @Test public void syncOwnerKeyIsCanonicalAcrossJsonPropertyOrder() throws Exception {
        JSONObject first = new JSONObject().put("provider", "github").put("repository", "repo");
        JSONObject second = new JSONObject().put("repository", "repo").put("provider", "github");

        assertEquals(SharedGraphSyncKernel.canonicalOwnerKey(first),
                SharedGraphSyncKernel.canonicalOwnerKey(second));
        assertEquals(SharedGraphSyncKernel.ownershipKey("work", first, "pull_requests"),
                SharedGraphSyncKernel.ownershipKey("work", second, "pull_requests"));
    }

    @Test public void completeSnapshotClaimRequiresInspectableProof() throws Exception {
        JSONObject state = successState(SharedGraphSyncKernel.apply(null,
                batch("knowledge", owner("brain", "amin-agent-control"), "knowledge_records", true,
                        new JSONArray().put(entity("knowledge:1", "KNOWLEDGE", "active", "k1")), new JSONArray()),
                1000L));
        JSONObject noProof = batch(
                "knowledge", owner("brain", "amin-agent-control"), "knowledge_records", true,
                new JSONArray(), new JSONArray());
        noProof.remove("completeness_proof");

        JSONObject result = SharedGraphSyncKernel.apply(state, noProof, 2000L);

        assertFalse(result.getBoolean("success"));
        assertEquals("MISSING_COMPLETENESS_PROOF", result.getString("errorCode"));
        assertEquals("active", find(result.getJSONObject("state").getJSONArray("entities"), "knowledge:1")
                .getString("sync_status"));
    }

    @Test public void stableIdentityCannotBeTakenOverByAnotherOwnershipKey() throws Exception {
        JSONObject ownerA = owner("brain", "amin-agent-control");
        JSONObject ownerB = owner("github", "ken12121122-dotcom/ken12121122-dotcom.github.io");
        JSONObject state = successState(SharedGraphSyncKernel.apply(null,
                batch("knowledge", ownerA, "knowledge_records", true,
                        new JSONArray().put(entity("knowledge:1", "KNOWLEDGE", "active", "brain-v1")),
                        new JSONArray()),
                1000L));

        JSONObject collision = SharedGraphSyncKernel.apply(state,
                batch("knowledge", ownerB, "repository_knowledge", true,
                        new JSONArray().put(entity("knowledge:1", "KNOWLEDGE", "active", "github-v1")),
                        new JSONArray()),
                2000L);

        assertFalse(collision.getBoolean("success"));
        assertEquals("OWNERSHIP_COLLISION", collision.getString("errorCode"));
        JSONObject preserved = find(collision.getJSONObject("state").getJSONArray("entities"), "knowledge:1");
        assertEquals(SharedGraphSyncKernel.ownershipKey("knowledge", ownerA, "knowledge_records"),
                preserved.getString("ownership_key"));
        assertEquals("brain-v1", preserved.getString("content_fingerprint"));

        JSONObject missingFromOwnerA = successState(SharedGraphSyncKernel.apply(
                collision.getJSONObject("state"),
                batch("knowledge", ownerA, "knowledge_records", true,
                        new JSONArray(), new JSONArray()),
                3000L));
        assertEquals("stale", find(missingFromOwnerA.getJSONArray("entities"), "knowledge:1")
                .getString("sync_status"));
    }

    private static JSONObject batch(String scope, JSONObject syncOwner, String partition,
                                    boolean complete, JSONArray entities, JSONArray relations) throws Exception {
        JSONObject out = new JSONObject()
                .put("format", "amin-graph-sync-batch")
                .put("version", 1)
                .put("contract_schema_version", "step9-core-v1")
                .put("graph_scope", scope)
                .put("sync_owner", syncOwner)
                .put("sync_partition", partition)
                .put("revision", "revision-1")
                .put("provenance", new JSONObject().put("adapter", "test"))
                .put("committed", true)
                .put("batch_status", complete ? "complete" : "partial")
                .put("complete_snapshot", complete)
                .put("entities", entities)
                .put("relations", relations);
        if (complete) {
            out.put("completeness_proof", new JSONObject()
                    .put("kind", "test_snapshot")
                    .put("entity_count", entities.length())
                    .put("relation_count", relations.length()));
        }
        return out;
    }

    private static JSONObject owner(String provider, String repository) throws Exception {
        return new JSONObject().put("provider", provider).put("repository", repository);
    }

    private static JSONObject entity(String id, String type, String lifecycle, String fingerprint) throws Exception {
        return new JSONObject()
                .put("stable_id", id)
                .put("content_fingerprint", fingerprint)
                .put("payload", new JSONObject()
                        .put("node_type", type)
                        .put("lifecycle_status", lifecycle));
    }

    private static JSONObject relation(String id, String sourceType, String semantic,
                                       String targetType, String fingerprint) throws Exception {
        return new JSONObject()
                .put("stable_id", id)
                .put("content_fingerprint", fingerprint)
                .put("payload", new JSONObject()
                        .put("source_type", sourceType)
                        .put("relation_semantic", semantic)
                        .put("target_type", targetType));
    }

    private static JSONObject successState(JSONObject result) throws Exception {
        assertTrue(result.optString("errorCode", ""), result.getBoolean("success"));
        return result.getJSONObject("state");
    }

    private static JSONObject find(JSONArray array, String id) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (id.equals(item.optString("stable_id", ""))) return item;
        }
        throw new AssertionError("missing record " + id);
    }

    private static void assertActive(JSONObject state, String id) throws Exception {
        assertEquals("active", find(state.getJSONArray("entities"), id).getString("sync_status"));
    }
}
