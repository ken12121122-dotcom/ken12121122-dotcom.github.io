package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Executable Phase 1 contract for provider-backed, read-only GitHub Work observations. */
final class GitHubWorkContract {
    static final String SCHEMA_VERSION = "github-work-v1";
    static final String GRAPH_SCOPE = "work";
    static final String PROVIDER = "github";

    static final String REPOSITORY = "REPOSITORY";
    static final String BRANCH = "BRANCH";
    static final String COMMIT = "COMMIT";
    static final String PULL_REQUEST = "PULL_REQUEST";
    static final String WORKFLOW_RUN = "WORKFLOW_RUN";
    static final String JOB = "JOB";

    private static final Set<String> NODE_TYPES = new HashSet<>();
    private static final Set<String> RELATIONS = new HashSet<>();

    static {
        Collections.addAll(NODE_TYPES, REPOSITORY, BRANCH, COMMIT, PULL_REQUEST, WORKFLOW_RUN, JOB);
        register(REPOSITORY, "contains", BRANCH);
        register(REPOSITORY, "contains", PULL_REQUEST);
        register(BRANCH, "points_to", COMMIT);
        register(PULL_REQUEST, "head_commit", COMMIT);
        register(PULL_REQUEST, "has_run", WORKFLOW_RUN);
        register(COMMIT, "has_run", WORKFLOW_RUN);
        register(WORKFLOW_RUN, "has_job", JOB);
    }

    private GitHubWorkContract() { }

    static JSONObject entity(String stableId, String type, String providerId, String title,
                             String lifecycle, String url, String parentId, JSONObject attributes) throws Exception {
        String cleanType = clean(type).toUpperCase();
        if (clean(stableId).isEmpty() || !NODE_TYPES.contains(cleanType) || clean(providerId).isEmpty()) {
            throw new IllegalArgumentException("INVALID_GITHUB_WORK_ENTITY");
        }
        JSONObject payload = new JSONObject()
                .put("provider", PROVIDER)
                .put("work_type", cleanType)
                .put("provider_id", clean(providerId))
                .put("title", clean(title).isEmpty() ? stableId : title)
                .put("lifecycle_status", clean(lifecycle).isEmpty() ? "observed" : lifecycle)
                .put("url", clean(url))
                .put("parent_id", clean(parentId))
                .put("attributes", attributes == null ? new JSONObject() : copy(attributes));
        return record(stableId, payload);
    }

    static JSONObject relation(String stableId, String from, String fromType, String semantic,
                               String to, String toType, JSONObject attributes) throws Exception {
        String sourceType = clean(fromType).toUpperCase();
        String targetType = clean(toType).toUpperCase();
        String relation = clean(semantic).toLowerCase();
        if (clean(stableId).isEmpty() || clean(from).isEmpty() || clean(to).isEmpty()
                || !RELATIONS.contains(key(sourceType, relation, targetType))) {
            throw new IllegalArgumentException("UNREGISTERED_GITHUB_WORK_RELATION");
        }
        JSONObject payload = new JSONObject()
                .put("provider", PROVIDER)
                .put("from", from)
                .put("from_type", sourceType)
                .put("relation_semantic", relation)
                .put("to", to)
                .put("to_type", targetType)
                .put("lifecycle_status", "observed")
                .put("attributes", attributes == null ? new JSONObject() : copy(attributes));
        return record(stableId, payload);
    }

    static JSONObject batch(long repositoryId, String repository, String partition, String revision,
                            boolean complete, JSONArray entities, JSONArray relations, long observedAt) throws Exception {
        String cleanPartition = clean(partition);
        String cleanRevision = clean(revision);
        if (repositoryId <= 0 || clean(repository).isEmpty() || cleanPartition.isEmpty() || cleanRevision.isEmpty()) {
            throw new IllegalArgumentException("INVALID_GITHUB_WORK_BATCH");
        }
        JSONObject batch = new JSONObject()
                .put("format", SharedGraphSyncKernel.BATCH_FORMAT)
                .put("version", SharedGraphSyncKernel.VERSION)
                .put("contract_schema_version", SCHEMA_VERSION)
                .put("graph_scope", GRAPH_SCOPE)
                .put("sync_owner", new JSONObject()
                        .put("provider", PROVIDER)
                        .put("repository_id", repositoryId)
                        .put("repository", repository))
                .put("sync_partition", cleanPartition)
                .put("revision", cleanRevision)
                .put("provenance", new JSONObject()
                        .put("provider", PROVIDER)
                        .put("repository", repository)
                        .put("observed_at", observedAt)
                        .put("read_only", true))
                .put("committed", true)
                .put("batch_status", complete ? "complete" : "partial")
                .put("complete_snapshot", complete)
                .put("entities", entities == null ? new JSONArray() : new JSONArray(entities.toString()))
                .put("relations", relations == null ? new JSONArray() : new JSONArray(relations.toString()));
        if (complete) {
            batch.put("completeness_proof", new JSONObject()
                    .put("kind", "github_api_single_page_without_next")
                    .put("partition", cleanPartition));
        }
        return batch;
    }

    static String repoId(long repositoryId) { return "github:repo:" + repositoryId; }
    static String branchId(long repositoryId, String name) {
        return "github:ref:" + repositoryId + ":refs/heads/" + clean(name);
    }
    static String commitId(long repositoryId, String sha) {
        return "github:commit:" + repositoryId + ":" + clean(sha);
    }
    static String pullRequestId(long repositoryId, int number) {
        return "github:pr:" + repositoryId + ":" + number;
    }
    static String runId(long repositoryId, long runId) {
        return "github:run:" + repositoryId + ":" + runId;
    }
    static String jobId(long repositoryId, long jobId) {
        return "github:job:" + repositoryId + ":" + jobId;
    }

    private static JSONObject record(String stableId, JSONObject payload) throws Exception {
        return new JSONObject()
                .put("stable_id", clean(stableId))
                .put("content_fingerprint", fingerprint(payload))
                .put("payload", payload);
    }

    private static void register(String source, String semantic, String target) {
        RELATIONS.add(key(source, semantic, target));
    }
    private static String key(String source, String semantic, String target) {
        return clean(source).toUpperCase() + "|" + clean(semantic).toLowerCase() + "|" + clean(target).toUpperCase();
    }

    private static String fingerprint(JSONObject payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(canonical(payload).getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    private static String canonical(Object value) throws Exception {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                String key = keys.get(i);
                out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonical(array.get(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof String) return JSONObject.quote((String) value);
        return String.valueOf(value);
    }

    private static JSONObject copy(JSONObject object) throws Exception {
        return object == null ? new JSONObject() : new JSONObject(object.toString());
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
