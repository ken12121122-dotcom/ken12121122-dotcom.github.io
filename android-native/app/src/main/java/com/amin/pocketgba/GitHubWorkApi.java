package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Public, unauthenticated and GET-only GitHub API client for Phase 1 acceptance. */
final class GitHubWorkApi {
    static final String REPOSITORY = "ken12121122-dotcom/ken12121122-dotcom.github.io";
    private static final String API = "https://api.github.com";
    private static final String REPOSITORY_PATH = "/repos/" + REPOSITORY;
    private static final int MAX_JOB_RUNS = 5;
    private static final int MAX_ENRICHED_PULLS = 3;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final GitHubHttpTransport transport;
    private int requestCount;
    private int rateLimit = -1;
    private int rateRemaining = -1;
    private long rateResetAt;
    private String rateResource = "";

    GitHubWorkApi(GitHubHttpTransport transport) {
        if (transport == null) throw new IllegalArgumentException("GITHUB_TRANSPORT_REQUIRED");
        this.transport = transport;
    }

    JSONObject fetchSnapshot(long observedAt) throws Exception {
        GitHubHttpResponse repositoryResponse = get(REPOSITORY_PATH);
        JSONObject repository = object(repositoryResponse);
        if (!REPOSITORY.equals(repository.optString("full_name", ""))) {
            throw new SecurityException("GITHUB_REPOSITORY_MISMATCH");
        }

        GitHubHttpResponse branchesResponse = get(REPOSITORY_PATH + "/branches?per_page=100");
        JSONArray branches = array(branchesResponse);
        GitHubHttpResponse commitsResponse = get(REPOSITORY_PATH + "/commits?per_page=20");
        JSONArray commits = array(commitsResponse);
        GitHubHttpResponse issuesResponse = get(REPOSITORY_PATH
                + "/issues?state=all&sort=updated&direction=desc&per_page=30");
        JSONArray issues = onlyIssues(array(issuesResponse));
        GitHubHttpResponse pullsResponse = get(REPOSITORY_PATH
                + "/pulls?state=all&sort=updated&direction=desc&per_page=30");
        JSONArray pulls = array(pullsResponse);

        boolean reviewsComplete = !hasNext(pullsResponse) && pulls.length() <= MAX_ENRICHED_PULLS;
        boolean pullFilesComplete = reviewsComplete;
        int pullLimit = Math.min(MAX_ENRICHED_PULLS, pulls.length());
        for (int i = 0; i < pullLimit; i++) {
            JSONObject pull = pulls.optJSONObject(i);
            int number = pull == null ? 0 : pull.optInt("number", 0);
            if (number <= 0) continue;
            GitHubHttpResponse reviewsResponse = get(REPOSITORY_PATH + "/pulls/" + number
                    + "/reviews?per_page=100");
            GitHubHttpResponse filesResponse = get(REPOSITORY_PATH + "/pulls/" + number
                    + "/files?per_page=100");
            pull.put("_reviews", array(reviewsResponse));
            pull.put("_files", array(filesResponse));
            if (hasNext(reviewsResponse)) reviewsComplete = false;
            if (hasNext(filesResponse)) pullFilesComplete = false;
        }

        GitHubHttpResponse runsResponse = get(REPOSITORY_PATH + "/actions/runs?per_page=30");
        JSONObject runsRoot = object(runsResponse);
        JSONArray runs = runsRoot.optJSONArray("workflow_runs");
        if (runs == null) runs = new JSONArray();

        JSONArray jobs = new JSONArray();
        boolean jobsComplete = !hasNext(runsResponse) && runs.length() <= MAX_JOB_RUNS;
        int runLimit = Math.min(MAX_JOB_RUNS, runs.length());
        for (int i = 0; i < runLimit; i++) {
            JSONObject run = runs.optJSONObject(i);
            long runId = run == null ? 0L : run.optLong("id", 0L);
            if (runId <= 0L) continue;
            GitHubHttpResponse jobsResponse = get(REPOSITORY_PATH + "/actions/runs/" + runId
                    + "/jobs?per_page=100");
            if (hasNext(jobsResponse)) jobsComplete = false;
            JSONArray runJobs = object(jobsResponse).optJSONArray("jobs");
            if (runJobs == null) continue;
            for (int j = 0; j < runJobs.length(); j++) {
                JSONObject job = runJobs.optJSONObject(j);
                if (job == null) continue;
                jobs.put(new JSONObject(job.toString())
                        .put("workflow_run_id", runId)
                        .put("workflow_run_head_sha", run.optString("head_sha", "")));
            }
        }

        GitHubHttpResponse releasesResponse = get(REPOSITORY_PATH + "/releases?per_page=20");
        JSONArray releases = array(releasesResponse);
        GitHubHttpResponse artifactsResponse = get(REPOSITORY_PATH + "/actions/artifacts?per_page=30");
        JSONObject artifactsRoot = object(artifactsResponse);
        JSONArray artifacts = artifactsRoot.optJSONArray("artifacts");
        if (artifacts == null) artifacts = new JSONArray();

        JSONObject completeness = new JSONObject()
                .put("repositories", true)
                .put("branches", !hasNext(branchesResponse))
                .put("commits", !hasNext(commitsResponse))
                .put("issues", !hasNext(issuesResponse))
                .put("pull_requests", !hasNext(pullsResponse) && pullFilesComplete)
                .put("reviews", reviewsComplete)
                .put("workflow_runs", !hasNext(runsResponse))
                .put("jobs", jobsComplete)
                .put("releases", !hasNext(releasesResponse))
                .put("artifacts", !hasNext(artifactsResponse));
        JSONArray partial = new JSONArray();
        String[] partitions = {"repositories", "branches", "commits", "issues", "pull_requests",
                "reviews", "workflow_runs", "jobs", "releases", "artifacts"};
        for (String partition : partitions) {
            if (!completeness.optBoolean(partition, false)) partial.put(partition);
        }

        String pushedAt = repository.optString("pushed_at", "").trim();
        String revision = (pushedAt.isEmpty() ? "observed" : pushedAt) + ":" + observedAt;
        return new JSONObject()
                .put("format", "amin-github-work-observation")
                .put("version", 1)
                .put("repository", repository)
                .put("branches", branches)
                .put("commits", commits)
                .put("issues", issues)
                .put("pull_requests", pulls)
                .put("workflow_runs", runs)
                .put("jobs", jobs)
                .put("releases", releases)
                .put("artifacts", artifacts)
                .put("revision", revision)
                .put("observed_at", observedAt)
                .put("completeness", completeness)
                .put("sync_metadata", new JSONObject()
                        .put("request_count", requestCount)
                        .put("rate_limit", rateLimit)
                        .put("rate_remaining", rateRemaining)
                        .put("rate_reset_at", rateResetAt)
                        .put("rate_resource", rateResource)
                        .put("partial_partitions", partial)
                        .put("raw_logs_enabled", false));
    }

    JSONObject rateLimitSnapshot() {
        try {
            return new JSONObject()
                    .put("request_count", requestCount)
                    .put("rate_limit", rateLimit)
                    .put("rate_remaining", rateRemaining)
                    .put("rate_reset_at", rateResetAt)
                    .put("rate_resource", rateResource);
        } catch (Exception impossible) {
            return new JSONObject();
        }
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
        requestCount++;
        captureRateLimit(response);
        if (response.statusCode() != 200) {
            throw new IllegalStateException(response.statusCode() == 403 || response.statusCode() == 429
                    ? "GITHUB_RATE_OR_PERMISSION_LIMIT" : "GITHUB_READ_HTTP_" + response.statusCode());
        }
        return response;
    }

    private void captureRateLimit(GitHubHttpResponse response) {
        rateLimit = integer(response.header("X-RateLimit-Limit"), rateLimit);
        rateRemaining = integer(response.header("X-RateLimit-Remaining"), rateRemaining);
        rateResetAt = longValue(response.header("X-RateLimit-Reset"), rateResetAt);
        String resource = response.header("X-RateLimit-Resource").trim();
        if (!resource.isEmpty()) rateResource = resource;
    }

    private static boolean hasNext(GitHubHttpResponse response) {
        String link = response == null ? "" : response.header("Link");
        return link.contains("rel=\"next\"") || link.contains("rel=next");
    }

    private static JSONArray onlyIssues(JSONArray input) throws Exception {
        JSONArray out = new JSONArray();
        for (int i = 0; i < input.length(); i++) {
            JSONObject item = input.optJSONObject(i);
            if (item != null && item.optJSONObject("pull_request") == null) {
                out.put(new JSONObject(item.toString()));
            }
        }
        return out;
    }

    private static JSONObject object(GitHubHttpResponse response) {
        try { return new JSONObject(response.body()); }
        catch (Exception error) { throw new IllegalStateException("GITHUB_OBJECT_INVALID"); }
    }

    private static JSONArray array(GitHubHttpResponse response) {
        try { return new JSONArray(response.body()); }
        catch (Exception error) { throw new IllegalStateException("GITHUB_ARRAY_INVALID"); }
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private static long longValue(String value, long fallback) {
        try { return Long.parseLong(value.trim()); }
        catch (Exception ignored) { return fallback; }
    }
}
