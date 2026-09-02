package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class GitHubWorkContractTest {
    private static final long REPOSITORY_ID = 1095700954L;

    @Test public void validBatchUsesExistingSharedIdentityAndDedupeKernel() throws Exception {
        String repoId = GitHubWorkContract.repoId(REPOSITORY_ID);
        String branchId = GitHubWorkContract.branchId(REPOSITORY_ID, "release/android");
        JSONArray entities = new JSONArray()
                .put(GitHubWorkContract.entity(repoId, GitHubWorkContract.REPOSITORY,
                        String.valueOf(REPOSITORY_ID), GitHubWorkApi.REPOSITORY, "active", "", "",
                        new JSONObject()))
                .put(GitHubWorkContract.entity(branchId, GitHubWorkContract.BRANCH,
                        "release/android", "release/android", "active", "", repoId,
                        new JSONObject()));
        JSONArray relations = new JSONArray().put(GitHubWorkContract.relation(
                "github:relation:repo-branch:test", repoId, GitHubWorkContract.REPOSITORY,
                "contains", branchId, GitHubWorkContract.BRANCH, new JSONObject()));
        JSONObject batch = GitHubWorkContract.batch(REPOSITORY_ID, GitHubWorkApi.REPOSITORY,
                "branches", "r1", true, entities, relations, 1000L);

        JSONObject first = SharedGraphSyncKernel.apply(null, batch, 1000L);
        assertTrue(first.optString("errorCode"), first.getBoolean("success"));
        JSONObject second = SharedGraphSyncKernel.apply(first.getJSONObject("state"), batch, 2000L);

        assertTrue(second.optString("errorCode"), second.getBoolean("success"));
        assertEquals(2, second.getJSONObject("state").getJSONArray("entities").length());
        assertEquals(0, second.getJSONObject("syncStats").getInt("entityDuplicates"));
        assertEquals("work", batch.getString("graph_scope"));
        assertEquals("github", batch.getJSONObject("sync_owner").getString("provider"));
    }

    @Test public void workGraphV1AcceptsEveryApprovedPhaseOneNodeType() throws Exception {
        String[] types = {GitHubWorkContract.REPOSITORY, GitHubWorkContract.BRANCH,
                GitHubWorkContract.COMMIT, GitHubWorkContract.ISSUE, GitHubWorkContract.PULL_REQUEST,
                GitHubWorkContract.REVIEW, GitHubWorkContract.WORKFLOW_RUN, GitHubWorkContract.JOB,
                GitHubWorkContract.RELEASE, GitHubWorkContract.ARTIFACT};
        for (int i = 0; i < types.length; i++) {
            JSONObject record = GitHubWorkContract.entity("github:test:" + i, types[i],
                    String.valueOf(i), types[i], "observed", "", "", new JSONObject());
            assertEquals(types[i], record.getJSONObject("payload").getString("work_type"));
        }
        assertEquals("github:issue:" + REPOSITORY_ID + ":120",
                GitHubWorkContract.issueId(REPOSITORY_ID, 120));
        assertEquals("github:review:" + REPOSITORY_ID + ":9",
                GitHubWorkContract.reviewId(REPOSITORY_ID, 9L));
        assertEquals("github:release:" + REPOSITORY_ID + ":8",
                GitHubWorkContract.releaseId(REPOSITORY_ID, 8L));
        assertEquals("github:artifact:" + REPOSITORY_ID + ":7",
                GitHubWorkContract.artifactId(REPOSITORY_ID, 7L));
    }

    @Test public void partialPageCannotMarkMissingRecordStale() throws Exception {
        JSONArray initial = new JSONArray()
                .put(commit("a", "first"))
                .put(commit("b", "second"));
        JSONObject state = SharedGraphSyncKernel.apply(null, GitHubWorkContract.batch(
                REPOSITORY_ID, GitHubWorkApi.REPOSITORY, "commits", "r1", true,
                initial, new JSONArray(), 1000L), 1000L).getJSONObject("state");
        JSONObject result = SharedGraphSyncKernel.apply(state, GitHubWorkContract.batch(
                REPOSITORY_ID, GitHubWorkApi.REPOSITORY, "commits", "r2", false,
                new JSONArray().put(commit("a", "updated")), new JSONArray(), 2000L), 2000L);

        assertTrue(result.optString("errorCode"), result.getBoolean("success"));
        assertEquals("active", find(result.getJSONObject("state").getJSONArray("entities"),
                GitHubWorkContract.commitId(REPOSITORY_ID, "b")).getString("sync_status"));
        assertEquals(0, result.getJSONObject("syncStats").getInt("entityMissing"));
    }

    @Test public void unregisteredRelationDirectionIsRejected() throws Exception {
        try {
            GitHubWorkContract.relation("bad", "job", GitHubWorkContract.JOB, "has_job",
                    "run", GitHubWorkContract.WORKFLOW_RUN, new JSONObject());
            fail("Expected contract rejection");
        } catch (IllegalArgumentException expected) {
            assertEquals("UNREGISTERED_GITHUB_WORK_RELATION", expected.getMessage());
        }
    }

    @Test public void fingerprintIsStableAcrossAttributePropertyOrder() throws Exception {
        JSONObject first = GitHubWorkContract.entity("github:test", GitHubWorkContract.JOB,
                "1", "job", "failure", "", "", new JSONObject().put("a", 1).put("b", 2));
        JSONObject second = GitHubWorkContract.entity("github:test", GitHubWorkContract.JOB,
                "1", "job", "failure", "", "", new JSONObject().put("b", 2).put("a", 1));

        assertEquals(first.getString("content_fingerprint"), second.getString("content_fingerprint"));
        assertFalse(first.getString("content_fingerprint").isEmpty());
    }

    private static JSONObject commit(String sha, String title) throws Exception {
        return GitHubWorkContract.entity(GitHubWorkContract.commitId(REPOSITORY_ID, sha),
                GitHubWorkContract.COMMIT, sha, title, "observed", "", "", new JSONObject());
    }

    private static JSONObject find(JSONArray array, String id) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (id.equals(item.optString("stable_id", ""))) return item;
        }
        throw new AssertionError("Missing " + id);
    }
}
