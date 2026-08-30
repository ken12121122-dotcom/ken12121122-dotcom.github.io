package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

/** Maps GitHub provider payloads into contract-valid, partitioned Work Graph batches. */
final class GitHubWorkAdapter {
    private GitHubWorkAdapter() { }

    static JSONArray toBatches(JSONObject observation) throws Exception {
        if (observation == null
                || !"amin-github-work-observation".equals(observation.optString("format", ""))
                || observation.optInt("version", -1) != 1) {
            throw new IllegalArgumentException("INVALID_GITHUB_WORK_OBSERVATION");
        }
        JSONObject repository = observation.getJSONObject("repository");
        long repositoryId = repository.optLong("id", 0L);
        String repositoryName = repository.optString("full_name", "").trim();
        if (repositoryId <= 0L || !GitHubWorkApi.REPOSITORY.equals(repositoryName)) {
            throw new SecurityException("GITHUB_WORK_OWNER_INVALID");
        }
        long observedAt = observation.optLong("observed_at", 0L);
        String revision = observation.optString("revision", "").trim();
        JSONObject completeness = observation.optJSONObject("completeness");
        if (completeness == null) completeness = new JSONObject();

        JSONArray batches = new JSONArray();
        batches.put(repositoryBatch(repositoryId, repositoryName, repository, revision,
                completeness.optBoolean("repositories", false), observedAt));
        batches.put(branchBatch(repositoryId, repositoryName, repository,
                observation.optJSONArray("branches"), revision,
                completeness.optBoolean("branches", false), observedAt));
        batches.put(commitBatch(repositoryId, repositoryName, repository,
                observation.optJSONArray("commits"), revision,
                completeness.optBoolean("commits", false), observedAt));
        batches.put(pullBatch(repositoryId, repositoryName,
                observation.optJSONArray("pull_requests"), revision,
                completeness.optBoolean("pull_requests", false), observedAt));
        batches.put(runBatch(repositoryId, repositoryName,
                observation.optJSONArray("workflow_runs"), revision,
                completeness.optBoolean("workflow_runs", false), observedAt));
        batches.put(jobBatch(repositoryId, repositoryName,
                observation.optJSONArray("jobs"), revision,
                completeness.optBoolean("jobs", false), observedAt));
        return batches;
    }

    private static JSONObject repositoryBatch(long repositoryId, String name, JSONObject repository,
                                              String revision, boolean complete, long observedAt) throws Exception {
        JSONArray entities = new JSONArray().put(GitHubWorkContract.entity(
                GitHubWorkContract.repoId(repositoryId), GitHubWorkContract.REPOSITORY,
                String.valueOf(repositoryId), name, "active", repository.optString("html_url", ""), "",
                new JSONObject()
                        .put("default_branch", repository.optString("default_branch", ""))
                        .put("visibility", repository.optString("visibility", "public"))
                        .put("pushed_at", repository.optString("pushed_at", ""))
                        .put("open_issues_count", repository.optInt("open_issues_count", 0))));
        return GitHubWorkContract.batch(repositoryId, name, "repositories", revision,
                complete, entities, new JSONArray(), observedAt);
    }

    private static JSONObject branchBatch(long repositoryId, String name, JSONObject repository,
                                          JSONArray input, String revision, boolean complete,
                                          long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        String repoId = GitHubWorkContract.repoId(repositoryId);
        JSONArray branches = input == null ? new JSONArray() : input;
        for (int i = 0; i < branches.length(); i++) {
            JSONObject branch = branches.optJSONObject(i);
            if (branch == null) continue;
            String branchName = branch.optString("name", "").trim();
            JSONObject commit = branch.optJSONObject("commit");
            String sha = commit == null ? "" : commit.optString("sha", "").trim();
            if (branchName.isEmpty() || sha.isEmpty()) continue;
            String branchId = GitHubWorkContract.branchId(repositoryId, branchName);
            entities.put(GitHubWorkContract.entity(branchId, GitHubWorkContract.BRANCH,
                    branchName, branchName, "active",
                    "https://github.com/" + name + "/tree/" + branchName, repoId,
                    new JSONObject().put("head_sha", sha).put("protected", branch.optBoolean("protected", false))));
            relations.put(GitHubWorkContract.relation(
                    "github:relation:repo-branch:" + repositoryId + ":" + branchName,
                    repoId, GitHubWorkContract.REPOSITORY, "contains",
                    branchId, GitHubWorkContract.BRANCH, new JSONObject()));
        }
        return GitHubWorkContract.batch(repositoryId, name, "branches", revision,
                complete, entities, relations, observedAt);
    }

    private static JSONObject commitBatch(long repositoryId, String name, JSONObject repository,
                                          JSONArray input, String revision, boolean complete,
                                          long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        JSONArray commits = input == null ? new JSONArray() : input;
        String defaultBranch = repository.optString("default_branch", "main");
        String branchId = GitHubWorkContract.branchId(repositoryId, defaultBranch);
        for (int i = 0; i < commits.length(); i++) {
            JSONObject item = commits.optJSONObject(i);
            if (item == null) continue;
            String sha = item.optString("sha", "").trim();
            JSONObject commit = item.optJSONObject("commit");
            if (sha.isEmpty() || commit == null) continue;
            JSONObject author = commit.optJSONObject("author");
            String message = commit.optString("message", sha);
            String title = firstLine(message);
            String commitId = GitHubWorkContract.commitId(repositoryId, sha);
            entities.put(GitHubWorkContract.entity(commitId, GitHubWorkContract.COMMIT,
                    sha, title, "observed", item.optString("html_url", ""), branchId,
                    new JSONObject()
                            .put("sha", sha)
                            .put("short_sha", sha.substring(0, Math.min(10, sha.length())))
                            .put("message", message)
                            .put("author", author == null ? "" : author.optString("name", ""))
                            .put("committed_at", author == null ? "" : author.optString("date", ""))));
            if (i == 0) {
                relations.put(GitHubWorkContract.relation(
                        "github:relation:branch-head:" + repositoryId + ":" + defaultBranch,
                        branchId, GitHubWorkContract.BRANCH, "points_to",
                        commitId, GitHubWorkContract.COMMIT, new JSONObject().put("head", true)));
            }
        }
        return GitHubWorkContract.batch(repositoryId, name, "commits", revision,
                complete, entities, relations, observedAt);
    }

    private static JSONObject pullBatch(long repositoryId, String name, JSONArray input,
                                        String revision, boolean complete, long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        JSONArray pulls = input == null ? new JSONArray() : input;
        String repoId = GitHubWorkContract.repoId(repositoryId);
        for (int i = 0; i < pulls.length(); i++) {
            JSONObject pull = pulls.optJSONObject(i);
            if (pull == null) continue;
            int number = pull.optInt("number", 0);
            if (number <= 0) continue;
            String prId = GitHubWorkContract.pullRequestId(repositoryId, number);
            String lifecycle = pull.optBoolean("merged", false) ? "merged" : pull.optString("state", "unknown");
            JSONObject head = pull.optJSONObject("head");
            JSONObject base = pull.optJSONObject("base");
            entities.put(GitHubWorkContract.entity(prId, GitHubWorkContract.PULL_REQUEST,
                    String.valueOf(number), "PR #" + number + " · " + pull.optString("title", ""),
                    lifecycle, pull.optString("html_url", ""), repoId,
                    new JSONObject()
                            .put("number", number)
                            .put("draft", pull.optBoolean("draft", false))
                            .put("head_ref", head == null ? "" : head.optString("ref", ""))
                            .put("head_sha", head == null ? "" : head.optString("sha", ""))
                            .put("base_ref", base == null ? "" : base.optString("ref", ""))
                            .put("updated_at", pull.optString("updated_at", ""))));
            relations.put(GitHubWorkContract.relation(
                    "github:relation:repo-pr:" + repositoryId + ":" + number,
                    repoId, GitHubWorkContract.REPOSITORY, "contains",
                    prId, GitHubWorkContract.PULL_REQUEST, new JSONObject()));
            String headSha = head == null ? "" : head.optString("sha", "").trim();
            if (!headSha.isEmpty()) {
                relations.put(GitHubWorkContract.relation(
                        "github:relation:pr-head:" + repositoryId + ":" + number + ":" + headSha,
                        prId, GitHubWorkContract.PULL_REQUEST, "head_commit",
                        GitHubWorkContract.commitId(repositoryId, headSha), GitHubWorkContract.COMMIT,
                        new JSONObject()));
            }
        }
        return GitHubWorkContract.batch(repositoryId, name, "pull_requests", revision,
                complete, entities, relations, observedAt);
    }

    private static JSONObject runBatch(long repositoryId, String name, JSONArray input,
                                       String revision, boolean complete, long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        JSONArray runs = input == null ? new JSONArray() : input;
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;
            long runNumber = run.optLong("id", 0L);
            if (runNumber <= 0L) continue;
            String runId = GitHubWorkContract.runId(repositoryId, runNumber);
            JSONArray pullRequests = run.optJSONArray("pull_requests");
            String parent = "";
            int pullNumber = 0;
            if (pullRequests != null && pullRequests.length() > 0) {
                JSONObject pull = pullRequests.optJSONObject(0);
                pullNumber = pull == null ? 0 : pull.optInt("number", 0);
                if (pullNumber > 0) parent = GitHubWorkContract.pullRequestId(repositoryId, pullNumber);
            }
            String lifecycle = run.optString("conclusion", "").trim();
            if (lifecycle.isEmpty()) lifecycle = run.optString("status", "unknown");
            entities.put(GitHubWorkContract.entity(runId, GitHubWorkContract.WORKFLOW_RUN,
                    String.valueOf(runNumber), run.optString("name", "Workflow") + " · " + lifecycle,
                    lifecycle, run.optString("html_url", ""), parent,
                    new JSONObject()
                            .put("run_id", runNumber)
                            .put("event", run.optString("event", ""))
                            .put("head_branch", run.optString("head_branch", ""))
                            .put("head_sha", run.optString("head_sha", ""))
                            .put("status", run.optString("status", ""))
                            .put("conclusion", run.optString("conclusion", ""))
                            .put("created_at", run.optString("created_at", ""))
                            .put("updated_at", run.optString("updated_at", ""))));
            String headSha = run.optString("head_sha", "").trim();
            if (!headSha.isEmpty()) {
                relations.put(GitHubWorkContract.relation(
                        "github:relation:commit-run:" + repositoryId + ":" + headSha + ":" + runNumber,
                        GitHubWorkContract.commitId(repositoryId, headSha), GitHubWorkContract.COMMIT, "has_run",
                        runId, GitHubWorkContract.WORKFLOW_RUN, new JSONObject()));
            }
            if (pullNumber > 0) {
                relations.put(GitHubWorkContract.relation(
                        "github:relation:pr-run:" + repositoryId + ":" + pullNumber + ":" + runNumber,
                        parent, GitHubWorkContract.PULL_REQUEST, "has_run",
                        runId, GitHubWorkContract.WORKFLOW_RUN, new JSONObject()));
            }
        }
        return GitHubWorkContract.batch(repositoryId, name, "workflow_runs", revision,
                complete, entities, relations, observedAt);
    }

    private static JSONObject jobBatch(long repositoryId, String name, JSONArray input,
                                       String revision, boolean complete, long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        JSONArray jobs = input == null ? new JSONArray() : input;
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job == null) continue;
            long jobNumber = job.optLong("id", 0L);
            long runNumber = job.optLong("workflow_run_id", 0L);
            if (jobNumber <= 0L || runNumber <= 0L) continue;
            String lifecycle = job.optString("conclusion", "").trim();
            if (lifecycle.isEmpty()) lifecycle = job.optString("status", "unknown");
            JSONObject failedStep = failedStep(job.optJSONArray("steps"));
            String jobId = GitHubWorkContract.jobId(repositoryId, jobNumber);
            String runId = GitHubWorkContract.runId(repositoryId, runNumber);
            entities.put(GitHubWorkContract.entity(jobId, GitHubWorkContract.JOB,
                    String.valueOf(jobNumber), job.optString("name", "Job") + " · " + lifecycle,
                    lifecycle, job.optString("html_url", ""), runId,
                    new JSONObject()
                            .put("job_id", jobNumber)
                            .put("workflow_run_id", runNumber)
                            .put("status", job.optString("status", ""))
                            .put("conclusion", job.optString("conclusion", ""))
                            .put("failed_step_name", failedStep.optString("name", ""))
                            .put("failed_step_number", failedStep.optInt("number", 0))
                            .put("started_at", job.optString("started_at", ""))
                            .put("completed_at", job.optString("completed_at", ""))));
            relations.put(GitHubWorkContract.relation(
                    "github:relation:run-job:" + repositoryId + ":" + runNumber + ":" + jobNumber,
                    runId, GitHubWorkContract.WORKFLOW_RUN, "has_job",
                    jobId, GitHubWorkContract.JOB, new JSONObject()));
        }
        return GitHubWorkContract.batch(repositoryId, name, "jobs", revision,
                complete, entities, relations, observedAt);
    }

    private static JSONObject failedStep(JSONArray steps) {
        if (steps == null) return new JSONObject();
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null && "failure".equals(step.optString("conclusion", ""))) return step;
        }
        return new JSONObject();
    }

    private static String firstLine(String value) {
        String clean = value == null ? "" : value.trim();
        int newline = clean.indexOf('\n');
        return newline < 0 ? clean : clean.substring(0, newline).trim();
    }
}
