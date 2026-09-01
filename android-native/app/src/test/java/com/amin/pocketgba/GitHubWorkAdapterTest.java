package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class GitHubWorkAdapterTest {
    private static final long REPOSITORY_ID = 1095700954L;
    private static final String SHA = "15296c67f93abf1fa574196385647c7cea7b1051";

    @Test public void mapsRepositoryDeliveryStateAndStructuredFailedStep() throws Exception {
        JSONArray batches = GitHubWorkAdapter.toBatches(observation());

        assertEquals(10, batches.length());
        assertEquals("repositories", batches.getJSONObject(0).getString("sync_partition"));
        assertEquals("issues", batches.getJSONObject(3).getString("sync_partition"));
        assertEquals("reviews", batches.getJSONObject(5).getString("sync_partition"));
        assertEquals("jobs", batches.getJSONObject(7).getString("sync_partition"));
        assertEquals("releases", batches.getJSONObject(8).getString("sync_partition"));
        assertEquals("artifacts", batches.getJSONObject(9).getString("sync_partition"));
        assertTrue(batches.getJSONObject(0).getBoolean("complete_snapshot"));
        assertFalse(batches.getJSONObject(2).getBoolean("complete_snapshot"));
        assertFalse(batches.getJSONObject(6).getBoolean("complete_snapshot"));
        assertFalse(batches.getJSONObject(7).getBoolean("complete_snapshot"));

        JSONObject job = batches.getJSONObject(7).getJSONArray("entities").getJSONObject(0)
                .getJSONObject("payload");
        assertEquals(GitHubWorkContract.JOB, job.getString("work_type"));
        assertEquals("failure", job.getString("lifecycle_status"));
        assertEquals("unit tests", job.getJSONObject("attributes").getString("failed_step_name"));
        assertEquals(3, job.getJSONObject("attributes").getInt("failed_step_number"));

        JSONObject pullPayload = batches.getJSONObject(4).getJSONArray("entities").getJSONObject(0)
                .getJSONObject("payload");
        assertEquals(1, pullPayload.getJSONObject("attributes").getInt("changed_files"));
        assertEquals(1, pullPayload.getJSONObject("attributes").getInt("approval_count"));
        JSONObject agent = pullPayload.getJSONObject("attributes").getJSONObject("agent_execution");
        assertTrue(agent.getBoolean("valid"));
        assertEquals(AgentExecutionStatusContract.CODEX, agent.getString("agent_id"));
        assertFalse(pullPayload.getJSONObject("attributes").has("body"));

        JSONObject runBatch = batches.getJSONObject(6);
        assertEquals(2, runBatch.getJSONArray("relations").length());
        assertEquals("has_run", runBatch.getJSONArray("relations").getJSONObject(0)
                .getJSONObject("payload").getString("relation_semantic"));

        JSONObject state = SharedGraphSyncKernel.emptyState();
        for (int i = 0; i < batches.length(); i++) {
            JSONObject result = SharedGraphSyncKernel.apply(state, batches.getJSONObject(i), 1000L + i);
            assertTrue(result.optString("errorCode"), result.getBoolean("success"));
            state = result.getJSONObject("state");
        }
        assertEquals(10, state.getJSONArray("entities").length());
        assertEquals(11, state.getJSONArray("relations").length());
    }

    @Test public void forkPullRequestCannotClaimPermanentAgentIdentity() throws Exception {
        JSONObject input = observation();
        JSONObject pull = input.getJSONArray("pull_requests").getJSONObject(0);
        pull.put("author_association", "CONTRIBUTOR");
        pull.getJSONObject("head").getJSONObject("repo")
                .put("full_name", "external/fork");

        JSONObject attributes = GitHubWorkAdapter.toBatches(input).getJSONObject(4)
                .getJSONArray("entities").getJSONObject(0).getJSONObject("payload")
                .getJSONObject("attributes");
        JSONObject marker = attributes.getJSONObject("agent_execution");
        assertFalse(marker.optBoolean("valid", true));
        assertEquals("UNTRUSTED_AGENT_ASSIGNMENT_SOURCE", marker.optString("error", ""));
    }

    private static JSONObject observation() throws Exception {
        JSONObject repository = new JSONObject()
                .put("id", REPOSITORY_ID)
                .put("full_name", GitHubWorkApi.REPOSITORY)
                .put("default_branch", "release/android")
                .put("html_url", "https://github.com/" + GitHubWorkApi.REPOSITORY)
                .put("pushed_at", "2026-08-30T00:00:00Z");
        JSONObject branch = new JSONObject().put("name", "release/android")
                .put("commit", new JSONObject().put("sha", SHA));
        JSONObject commit = new JSONObject().put("sha", SHA).put("html_url", "https://example/commit")
                .put("commit", new JSONObject().put("message", "Phase 1")
                        .put("author", new JSONObject().put("name", "Amin").put("date", "now")));
        JSONObject pull = new JSONObject().put("number", 120).put("title", "Control layer")
                .put("state", "open").put("html_url", "https://example/pr")
                .put("body", "<!-- amin-agent-execution {\"agent_id\":\"agent:codex\","
                        + "\"agent_type\":\"codex\",\"role\":\"Backend / Platform Engineer\","
                        + "\"issue_number\":122,\"current_task\":\"Step 12 dashboard\","
                        + "\"status\":\"working\",\"owner_attention_required\":false} -->")
                .put("author_association", "OWNER")
                .put("head", new JSONObject().put("ref", "feat").put("sha", SHA)
                        .put("repo", new JSONObject().put("full_name", GitHubWorkApi.REPOSITORY)))
                .put("base", new JSONObject().put("ref", "release/android"))
                .put("_reviews", new JSONArray().put(new JSONObject().put("id", 7001L)
                        .put("state", "APPROVED").put("user", new JSONObject().put("login", "owner"))))
                .put("_files", new JSONArray().put(new JSONObject().put("filename", "A.java")
                        .put("additions", 5).put("deletions", 2)));
        JSONObject run = new JSONObject().put("id", 9001L).put("name", "Android CI")
                .put("status", "completed").put("conclusion", "failure").put("head_sha", SHA)
                .put("pull_requests", new JSONArray().put(new JSONObject().put("number", 120)));
        JSONObject job = new JSONObject().put("id", 8001L).put("workflow_run_id", 9001L)
                .put("name", "test").put("status", "completed").put("conclusion", "failure")
                .put("steps", new JSONArray()
                        .put(new JSONObject().put("number", 1).put("name", "checkout").put("conclusion", "success"))
                        .put(new JSONObject().put("number", 3).put("name", "unit tests").put("conclusion", "failure")));
        JSONObject issue = new JSONObject().put("number", 120).put("title", "Roadmap")
                .put("state", "open").put("html_url", "https://example/issue");
        JSONObject release = new JSONObject().put("id", 6001L).put("name", "Bridge 85")
                .put("tag_name", "bridge85").put("html_url", "https://example/release");
        JSONObject artifact = new JSONObject().put("id", 5001L).put("name", "debug-apk")
                .put("workflow_run", new JSONObject().put("id", 9001L));
        return new JSONObject()
                .put("format", "amin-github-work-observation").put("version", 1)
                .put("repository", repository)
                .put("branches", new JSONArray().put(branch))
                .put("commits", new JSONArray().put(commit))
                .put("issues", new JSONArray().put(issue))
                .put("pull_requests", new JSONArray().put(pull))
                .put("workflow_runs", new JSONArray().put(run))
                .put("jobs", new JSONArray().put(job))
                .put("releases", new JSONArray().put(release))
                .put("artifacts", new JSONArray().put(artifact))
                .put("revision", "r1").put("observed_at", 1000L)
                .put("completeness", new JSONObject().put("repositories", true)
                        .put("branches", true).put("commits", false).put("issues", true)
                        .put("pull_requests", true).put("reviews", true)
                        .put("workflow_runs", false).put("jobs", false)
                        .put("releases", true).put("artifacts", true));
    }
}
