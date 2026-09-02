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
        batches.put(issueBatch(repositoryId, repositoryName,
                observation.optJSONArray("issues"), revision,
                completeness.optBoolean("issues", false), observedAt));
        batches.put(pullBatch(repositoryId, repositoryName,
                observation.optJSONArray("pull_requests"), revision,
                completeness.optBoolean("pull_requests", false), observedAt));
        batches.put(reviewBatch(repositoryId, repositoryName,
                observation.optJSONArray("pull_requests"), revision,
                completeness.optBoolean("reviews", false), observedAt));
        batches.put(runBatch(repositoryId, repositoryName,
                observation.optJSONArray("workflow_runs"), revision,
                completeness.optBoolean("workflow_runs", false), observedAt));
        batches.put(jobBatch(repositoryId, repositoryName,
                observation.optJSONArray("jobs"), revision,
                completeness.optBoolean("jobs", false), observedAt));
        batches.put(releaseBatch(repositoryId, repositoryName,
                observation.optJSONArray("releases"), revision,
                completeness.optBoolean("releases", false), observedAt));
        batches.put(artifactBatch(repositoryId, repositoryName,
                observation.optJSONArray("artifacts"), revision,
                completeness.optBoolean("artifacts", false), observedAt));
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
            String lifecycle = !pull.isNull("merged_at") && !pull.optString("merged_at", "").isEmpty()
                    ? "merged" : pull.optString("state", "unknown");
            JSONObject head = pull.optJSONObject("head");
            JSONObject base = pull.optJSONObject("base");
            JSONArray files = pull.optJSONArray("_files");
            JSONArray reviews = pull.optJSONArray("_reviews");
            JSONObject fileSummary = fileSummary(files);
            JSONObject reviewSummary = reviewSummary(reviews);
            entities.put(GitHubWorkContract.entity(prId, GitHubWorkContract.PULL_REQUEST,
                    String.valueOf(number), "PR #" + number + " · " + pull.optString("title", ""),
                    lifecycle, pull.optString("html_url", ""), repoId,
                    new JSONObject()
                            .put("number", number)
                            .put("draft", pull.optBoolean("draft", false))
                            .put("head_ref", head == null ? "" : head.optString("ref", ""))
                            .put("head_sha", head == null ? "" : head.optString("sha", ""))
                            .put("base_ref", base == null ? "" : base.optString("ref", ""))
                            .put("updated_at", pull.optString("updated_at", ""))
                            .put("changed_files", fileSummary.optInt("changed_files", 0))
                            .put("additions", fileSummary.optInt("additions", 0))
                            .put("deletions", fileSummary.optInt("deletions", 0))
                            .put("changed_file_names", fileSummary.optJSONArray("file_names"))
                            .put("review_count", reviewSummary.optInt("review_count", 0))
                            .put("approval_count", reviewSummary.optInt("approval_count", 0))
                            .put("changes_requested_count", reviewSummary.optInt("changes_requested_count", 0))));
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

    private static JSONObject issueBatch(long repositoryId, String name, JSONArray input,
                                         String revision, boolean complete, long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        JSONArray issues = input == null ? new JSONArray() : input;
        String repoId = GitHubWorkContract.repoId(repositoryId);
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.optJSONObject(i);
            if (issue == null || issue.optJSONObject("pull_request") != null) continue;
            int number = issue.optInt("number", 0);
            if (number <= 0) continue;
            String issueId = GitHubWorkContract.issueId(repositoryId, number);
            entities.put(GitHubWorkContract.entity(issueId, GitHubWorkContract.ISSUE,
                    String.valueOf(number), "Issue #" + number + " · " + issue.optString("title", ""),
                    issue.optString("state", "unknown"), issue.optString("html_url", ""), repoId,
                    new JSONObject()
                            .put("number", number)
                            .put("locked", issue.optBoolean("locked", false))
                            .put("comments", issue.optInt("comments", 0))
                            .put("created_at", issue.optString("created_at", ""))
                            .put("updated_at", issue.optString("updated_at", ""))
                            .put("closed_at", issue.optString("closed_at", ""))));
            relations.put(GitHubWorkContract.relation(
                    "github:relation:repo-issue:" + repositoryId + ":" + number,
                    repoId, GitHubWorkContract.REPOSITORY, "contains",
                    issueId, GitHubWorkContract.ISSUE, new JSONObject()));
        }
        return GitHubWorkContract.batch(repositoryId, name, "issues", revision,
                complete, entities, relations, observedAt);
    }

    private static JSONObject reviewBatch(long repositoryId, String name, JSONArray input,
                                          String revision, boolean complete, long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        JSONArray pulls = input == null ? new JSONArray() : input;
        for (int i = 0; i < pulls.length(); i++) {
            JSONObject pull = pulls.optJSONObject(i);
            int pullNumber = pull == null ? 0 : pull.optInt("number", 0);
            JSONArray reviews = pull == null ? null : pull.optJSONArray("_reviews");
            if (pullNumber <= 0 || reviews == null) continue;
            String prId = GitHubWorkContract.pullRequestId(repositoryId, pullNumber);
            for (int j = 0; j < reviews.length(); j++) {
                JSONObject review = reviews.optJSONObject(j);
                long reviewNumber = review == null ? 0L : review.optLong("id", 0L);
                if (reviewNumber <= 0L) continue;
                JSONObject user = review.optJSONObject("user");
                String state = review.optString("state", "observed").toLowerCase();
                String reviewId = GitHubWorkContract.reviewId(repositoryId, reviewNumber);
                entities.put(GitHubWorkContract.entity(reviewId, GitHubWorkContract.REVIEW,
                        String.valueOf(reviewNumber), "Review · "
                                + (user == null ? "unknown" : user.optString("login", "unknown"))
                                + " · " + state, state, review.optString("html_url", ""), prId,
                        new JSONObject()
                                .put("review_id", reviewNumber)
                                .put("pull_number", pullNumber)
                                .put("reviewer", user == null ? "" : user.optString("login", ""))
                                .put("state", state)
                                .put("submitted_at", review.optString("submitted_at", ""))
                                .put("commit_id", review.optString("commit_id", ""))));
                relations.put(GitHubWorkContract.relation(
                        "github:relation:pr-review:" + repositoryId + ":" + pullNumber + ":" + reviewNumber,
                        prId, GitHubWorkContract.PULL_REQUEST, "has_review",
                        reviewId, GitHubWorkContract.REVIEW, new JSONObject()));
            }
        }
        return GitHubWorkContract.batch(repositoryId, name, "reviews", revision,
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

    private static JSONObject releaseBatch(long repositoryId, String name, JSONArray input,
                                           String revision, boolean complete, long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        JSONArray releases = input == null ? new JSONArray() : input;
        String repoId = GitHubWorkContract.repoId(repositoryId);
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            long releaseNumber = release == null ? 0L : release.optLong("id", 0L);
            if (releaseNumber <= 0L) continue;
            String releaseId = GitHubWorkContract.releaseId(repositoryId, releaseNumber);
            String lifecycle = release.optBoolean("draft", false) ? "draft"
                    : release.optBoolean("prerelease", false) ? "prerelease" : "published";
            String tag = release.optString("tag_name", "");
            entities.put(GitHubWorkContract.entity(releaseId, GitHubWorkContract.RELEASE,
                    String.valueOf(releaseNumber), release.optString("name", tag), lifecycle,
                    release.optString("html_url", ""), repoId,
                    new JSONObject()
                            .put("release_id", releaseNumber)
                            .put("tag_name", tag)
                            .put("target_commitish", release.optString("target_commitish", ""))
                            .put("draft", release.optBoolean("draft", false))
                            .put("prerelease", release.optBoolean("prerelease", false))
                            .put("created_at", release.optString("created_at", ""))
                            .put("published_at", release.optString("published_at", ""))));
            relations.put(GitHubWorkContract.relation(
                    "github:relation:repo-release:" + repositoryId + ":" + releaseNumber,
                    repoId, GitHubWorkContract.REPOSITORY, "contains",
                    releaseId, GitHubWorkContract.RELEASE, new JSONObject()));
        }
        return GitHubWorkContract.batch(repositoryId, name, "releases", revision,
                complete, entities, relations, observedAt);
    }

    private static JSONObject artifactBatch(long repositoryId, String name, JSONArray input,
                                            String revision, boolean complete, long observedAt) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        JSONArray artifacts = input == null ? new JSONArray() : input;
        for (int i = 0; i < artifacts.length(); i++) {
            JSONObject artifact = artifacts.optJSONObject(i);
            long artifactNumber = artifact == null ? 0L : artifact.optLong("id", 0L);
            JSONObject workflowRun = artifact == null ? null : artifact.optJSONObject("workflow_run");
            long runNumber = workflowRun == null ? 0L : workflowRun.optLong("id", 0L);
            if (artifactNumber <= 0L) continue;
            String artifactId = GitHubWorkContract.artifactId(repositoryId, artifactNumber);
            String runId = runNumber <= 0L ? "" : GitHubWorkContract.runId(repositoryId, runNumber);
            String lifecycle = artifact.optBoolean("expired", false) ? "expired" : "available";
            entities.put(GitHubWorkContract.entity(artifactId, GitHubWorkContract.ARTIFACT,
                    String.valueOf(artifactNumber), artifact.optString("name", "Artifact"), lifecycle,
                    runNumber <= 0L ? "https://github.com/" + name + "/actions"
                            : "https://github.com/" + name + "/actions/runs/" + runNumber,
                    runId,
                    new JSONObject()
                            .put("artifact_id", artifactNumber)
                            .put("workflow_run_id", runNumber)
                            .put("size_in_bytes", artifact.optLong("size_in_bytes", 0L))
                            .put("expired", artifact.optBoolean("expired", false))
                            .put("created_at", artifact.optString("created_at", ""))
                            .put("expires_at", artifact.optString("expires_at", ""))));
            if (!runId.isEmpty()) {
                relations.put(GitHubWorkContract.relation(
                        "github:relation:run-artifact:" + repositoryId + ":" + runNumber + ":" + artifactNumber,
                        runId, GitHubWorkContract.WORKFLOW_RUN, "has_artifact",
                        artifactId, GitHubWorkContract.ARTIFACT, new JSONObject()));
            }
        }
        return GitHubWorkContract.batch(repositoryId, name, "artifacts", revision,
                complete, entities, relations, observedAt);
    }

    private static JSONObject fileSummary(JSONArray files) throws Exception {
        JSONArray names = new JSONArray();
        int additions = 0;
        int deletions = 0;
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.optJSONObject(i);
                if (file == null) continue;
                additions += file.optInt("additions", 0);
                deletions += file.optInt("deletions", 0);
                if (names.length() < 8) names.put(file.optString("filename", ""));
            }
        }
        return new JSONObject()
                .put("changed_files", files == null ? 0 : files.length())
                .put("additions", additions)
                .put("deletions", deletions)
                .put("file_names", names);
    }

    private static JSONObject reviewSummary(JSONArray reviews) throws Exception {
        int approvals = 0;
        int changesRequested = 0;
        if (reviews != null) {
            for (int i = 0; i < reviews.length(); i++) {
                JSONObject review = reviews.optJSONObject(i);
                String state = review == null ? "" : review.optString("state", "");
                if ("APPROVED".equals(state)) approvals++;
                if ("CHANGES_REQUESTED".equals(state)) changesRequested++;
            }
        }
        return new JSONObject()
                .put("review_count", reviews == null ? 0 : reviews.length())
                .put("approval_count", approvals)
                .put("changes_requested_count", changesRequested);
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
