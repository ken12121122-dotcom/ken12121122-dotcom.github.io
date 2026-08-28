package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public GitHub tree scanner that rebuilds a review-only, evidence-backed Source Graph snapshot. */
final class GitHubSourceGraphScanner {
    static final String REPOSITORY = "ken12121122-dotcom/ken12121122-dotcom.github.io";
    static final String BRANCH = "release/android";
    static final String SCANNER_ANCHOR_PATH = "android-native/app/src/main/java/com/amin/pocketgba/GitHubSourceGraphScanner.java";
    private static final String JAVA_ROOT = "android-native/app/src/main/java/";
    private static final AtomicBoolean SYNC_IN_FLIGHT = new AtomicBoolean(false);

    private GitHubSourceGraphScanner() { }

    static JSONObject scan() throws Exception {
        String encodedRef = URLEncoder.encode(BRANCH, "UTF-8").replace("+", "%20");
        String treeUrl = "https://api.github.com/repos/" + REPOSITORY + "/git/trees/" + encodedRef + "?recursive=1";
        HttpURLConnection connection = (HttpURLConnection) new URL(treeUrl).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AMIN-Android-SourceGraph");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("GITHUB_TREE_HTTP_" + code);
        StringBuilder raw = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) raw.append(line);
        } finally { connection.disconnect(); }

        JSONObject treeRoot = new JSONObject(raw.toString());
        String revision = treeRoot.optString("sha", "").trim();
        if (revision.isEmpty()) throw new IllegalStateException("GITHUB_TREE_REVISION_MISSING");

        JSONObject graph = buildGraph(treeRoot.optJSONArray("tree"), revision);
        JSONObject normalized = SourceGraphContract.normalize(graph);
        if (!normalized.optBoolean("valid", false)) throw new IllegalStateException("SOURCE_GRAPH_INVALID");
        normalized.put("revision", revision);
        normalized.put("generatedFrom", "github-cloud-tree");
        normalized.put("scanMode", "evidence-only-v1");
        normalized.put("anchorId", classIdFor(SCANNER_ANCHOR_PATH));
        return normalized;
    }

    static void syncAsync(Context context, Runnable onFinished) {
        if (context == null) return;
        if (!SYNC_IN_FLIGHT.compareAndSet(false, true)) {
            if (onFinished != null) onFinished.run();
            return;
        }
        Context app = context.getApplicationContext();
        GitHubSourceSyncState.syncing();
        new Thread(() -> {
            try {
                JSONObject graph = scan();
                String revision = graph.optString("revision", "").trim();
                CloudSourceGraphStore store = new CloudSourceGraphStore(app);
                store.stage(revision, graph);
                GitHubSourceSyncState.ready(revision);
            } catch (Exception error) {
                GitHubSourceSyncState.failed(error);
            } finally {
                SYNC_IN_FLIGHT.set(false);
                if (onFinished != null) onFinished.run();
            }
        }, "amin-source-sync").start();
    }

    private static JSONObject buildGraph(JSONArray tree, String revision) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        Set<String> ids = new HashSet<>();
        Set<String> relationIds = new HashSet<>();

        String repoId = "repo:amin";
        String branchId = "branch:" + BRANCH;
        addEntity(entities, ids, repoId, "repository", "AMIN Repository", "", revision, "repository-root", "");
        addEntity(entities, ids, branchId, "branch", BRANCH, "", revision, "branch-ref", "");
        addRelation(relations, relationIds, "contains", repoId, branchId, revision, "git-ref-membership");

        if (tree != null) {
            for (int i = 0; i < tree.length(); i++) {
                JSONObject item = tree.optJSONObject(i);
                if (item == null) continue;
                String filePath = item.optString("path", "").trim();
                if (!"blob".equals(item.optString("type", "")) || !filePath.startsWith(JAVA_ROOT) || !filePath.endsWith(".java")) continue;

                String blobSha = item.optString("sha", "").trim();
                String parentId = branchId;
                String[] parts = filePath.split("/");
                StringBuilder dirPath = new StringBuilder();
                for (int p = 0; p < parts.length - 1; p++) {
                    if (dirPath.length() > 0) dirPath.append('/');
                    dirPath.append(parts[p]);
                    String dirId = "dir:" + dirPath;
                    addEntity(entities, ids, dirId, "directory", parts[p], dirPath.toString(), revision, "tree-path", "");
                    addRelation(relations, relationIds, "contains", parentId, dirId, revision, "tree-parent-child");
                    parentId = dirId;
                }

                String relative = filePath.substring(JAVA_ROOT.length());
                String fileName = parts[parts.length - 1];
                String fileId = fileIdFor(relative);
                String classId = classIdFor(filePath);
                String base = fileName.substring(0, fileName.length() - 5);
                addEntity(entities, ids, fileId, "file", fileName, filePath, revision, "git-blob", blobSha);
                addEntity(entities, ids, classId, "class", base, filePath, revision, "java-file-class-candidate", blobSha);
                addRelation(relations, relationIds, "contains", parentId, fileId, revision, "tree-parent-child");
                addRelation(relations, relationIds, "declares", fileId, classId, revision, "java-file-class-candidate");
            }
        }

        if (!ids.contains(classIdFor(SCANNER_ANCHOR_PATH))) {
            throw new IllegalStateException("SCANNER_ANCHOR_NOT_FOUND");
        }

        return new JSONObject()
                .put("format", SourceGraphContract.FORMAT)
                .put("version", SourceGraphContract.VERSION)
                .put("authority", "github-cloud-review")
                .put("revision", revision)
                .put("entities", entities)
                .put("relations", relations);
    }

    private static String fileIdFor(String relative) {
        if (relative == null) return "";
        String clean = relative.trim();
        if (!clean.contains("/")) return "file:" + clean;
        return "file:" + clean;
    }

    private static String classIdFor(String filePath) {
        if (filePath == null || !filePath.startsWith(JAVA_ROOT) || !filePath.endsWith(".java")) return "";
        String relative = filePath.substring(JAVA_ROOT.length(), filePath.length() - 5);
        if (relative.startsWith("com/amin/pocketgba/") && relative.indexOf('/', "com/amin/pocketgba/".length()) < 0) {
            return "class:" + relative.substring("com/amin/pocketgba/".length());
        }
        return "class:" + relative.replace('/', '.');
    }

    private static void addEntity(JSONArray out, Set<String> ids, String id, String type, String title, String path,
                                  String revision, String rule, String blobSha) throws Exception {
        if (id == null || id.trim().isEmpty() || !ids.add(id)) return;
        JSONObject evidence = new JSONObject()
                .put("provider", "github")
                .put("repository", REPOSITORY)
                .put("branch", BRANCH)
                .put("revision", revision)
                .put("path", path == null ? "" : path)
                .put("scannerRule", rule == null ? "" : rule);
        if (blobSha != null && !blobSha.trim().isEmpty()) evidence.put("blobSha", blobSha.trim());
        out.put(new JSONObject()
                .put("id", id)
                .put("entityType", type)
                .put("title", title)
                .put("path", path)
                .put("reviewStatus", "candidate")
                .put("verification", "static_verified")
                .put("evidence", evidence));
    }

    private static void addRelation(JSONArray out, Set<String> relationIds, String type, String from, String to,
                                    String revision, String rule) throws Exception {
        String id = "source:" + type + ":" + from + ">" + to;
        if (!relationIds.add(id)) return;
        out.put(new JSONObject()
                .put("id", id)
                .put("type", type)
                .put("from", from)
                .put("to", to)
                .put("reviewStatus", "candidate")
                .put("verification", "static_verified")
                .put("evidence", new JSONObject()
                        .put("provider", "github")
                        .put("repository", REPOSITORY)
                        .put("branch", BRANCH)
                        .put("revision", revision)
                        .put("scannerRule", rule == null ? "" : rule)));
    }
}
