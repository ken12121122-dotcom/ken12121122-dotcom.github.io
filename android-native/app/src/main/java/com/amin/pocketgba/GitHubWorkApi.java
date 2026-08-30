package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Public, unauthenticated and GET-only GitHub API client for the Phase 1 slice. */
final class GitHubWorkApi {
    static final String REPOSITORY = "ken12121122-dotcom/ken12121122-dotcom.github.io";
    private static final String API = "https://api.github.com";
    private static final String REPOSITORY_PATH = "/repos/" + REPOSITORY;
    private static final int MAX_JOB_RUNS = 8;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final GitHubHttpTransport transport;

    GitHubWorkApi(GitHubHttpTransport transport) {
        if (transport == null) throw new IllegalArgumentException("GITHUB_TRANSPORT_REQUIRED");
        this.transport = transport;
    }

    JSONObject fetchSnapshot(long observedAt) throws Exception {
        JSONObject repository = object(get(REPOSITORY_PATH));
        if (!REPOSITORY.equals(repository.optString("full_name", ""))) {
            throw new SecurityException("GITHUB_REPOSITORY_MISMATCH");
        }

        JSONArray branches = array(get(REPOSITORY_PATH + "/branches?per_page=100"));
        JSONArray commits = array(get(REPOSITORY_PATH + "/commits?per_page=20"));
        JSONArray pulls = array(get(REPOSITORY_PATH + "/pulls?state=all&sort=updated&direction=desc&per_page=30"));
        JSONObject runsRoot = object(get(REPOSITORY_PATH + "/actions/runs?per_page=30"));
        JSONArray runs = runsRoot.optJSONArray("workflow_runs");
        if (runs == null) runs = new JSONArray();

        JSONArray jobs = new JSONArray();
        int runLimit = Math.min(MAX_JOB_RUNS, runs.length());
        for (int i = 0; i < runLimit; i++) {
            JSONObject run = runs.optJSONObject(i);
            long runId = run == null ? 0L : run.optLong("id", 0L);
            if (runId <= 0L) continue;
            JSONObject jobsRoot = object(get(REPOSITORY_PATH + "/actions/runs/" + runId + "/jobs?per_page=100"));
            JSONArray runJobs = jobsRoot.optJSONArray("jobs");
            if (runJobs == null) continue;
            for (int j = 0; j < runJobs.length(); j++) {
                JSONObject job = runJobs.optJSONObject(j);
                if (job == null) continue;
                JSONObject copy = new JSONObject(job.toString())
                        .put("workflow_run_id", runId)
                        .put("workflow_run_head_sha", run.optString("head_sha", ""));
                jobs.put(copy);
            }
        }

        String revision = repository.optString("pushed_at", "").trim();
        if (revision.isEmpty()) revision = "observed:" + observedAt;
        return new JSONObject()
                .put("format", "amin-github-work-observation")
                .put("version", 1)
                .put("repository", repository)
                .put("branches", branches)
                .put("commits", commits)
                .put("pull_requests", pulls)
                .put("workflow_runs", runs)
                .put("jobs", jobs)
                .put("revision", revision)
                .put("observed_at", observedAt)
                .put("completeness", new JSONObject()
                        .put("repositories", true)
                        .put("branches", branches.length() < 100)
                        .put("commits", false)
                        .put("pull_requests", pulls.length() < 30)
                        .put("workflow_runs", false)
                        .put("jobs", false));
    }

    private GitHubHttpResponse get(String path) throws Exception {
        if (!(path.equals(REPOSITORY_PATH) || path.startsWith(REPOSITORY_PATH + "/"))) {
            throw new SecurityException("GITHUB_PATH_NOT_ALLOWED");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        headers.put("X-GitHub-Api-Version", "2022-11-28");
        headers.put("User-Agent", "Amin-Pocket-GitHub-Observer/1");
        GitHubHttpResponse response = transport.execute(new GitHubHttpRequest(
                "GET", API + path, headers, "", MAX_RESPONSE_BYTES));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GITHUB_READ_HTTP_" + response.statusCode());
        }
        return response;
    }

    private static JSONObject object(GitHubHttpResponse response) {
        try { return new JSONObject(response.body()); }
        catch (Exception error) { throw new IllegalStateException("GITHUB_OBJECT_INVALID"); }
    }

    private static JSONArray array(GitHubHttpResponse response) {
        try { return new JSONArray(response.body()); }
        catch (Exception error) { throw new IllegalStateException("GITHUB_ARRAY_INVALID"); }
    }
}
