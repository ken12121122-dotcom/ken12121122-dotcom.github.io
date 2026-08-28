package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/** Public GitHub tree scanner that rebuilds a review-only Source Graph snapshot. */
final class GitHubSourceGraphScanner {
    static final String REPOSITORY = "ken12121122-dotcom/ken12121122-dotcom.github.io";
    static final String BRANCH = "release/android";
    private static final String TREE_URL = "https://api.github.com/repos/ken12121122-dotcom/ken12121122-dotcom.github.io/git/trees/release/android?recursive=1";
    private static final String JAVA_ROOT = "android-native/app/src/main/java/com/amin/pocketgba/";

    private GitHubSourceGraphScanner() { }

    static JSONObject scan() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(TREE_URL).openConnection();
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
        return normalized;
    }

    static void syncAsync(Context context, Runnable onFinished) {
        if (context == null) return;
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
                if (onFinished != null) onFinished.run();
            }
        }, "amin-source-sync").start();
    }

    private static JSONObject buildGraph(JSONArray tree, String revision) throws Exception {
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        Set<String> ids = new HashSet<>();
        addEntity(entities, ids, "repo:amin", "repository", "AMIN Repository", REPOSITORY);
        addEntity(entities, ids, "branch:release-android", "branch", BRANCH, BRANCH);
        addRelation(relations, "contains", "repo:amin", "branch:release-android");

        String parent = "branch:release-android";
        String[] dirs = {"android-native", "app", "src", "main", "java", "com", "amin", "pocketgba"};
        StringBuilder path = new StringBuilder();
        for (String dir : dirs) {
            if (path.length() > 0) path.append('/');
            path.append(dir);
            String id = "dir:" + path;
            String title = dir.equals("android-native") ? "Android Application" : dir.equals("app") ? "App Module" : dir.equals("pocketgba") ? "com.amin.pocketgba" : dir;
            addEntity(entities, ids, id, "directory", title, path.toString());
            addRelation(relations, "contains", parent, id);
            parent = id;
        }

        if (tree != null) {
            for (int i = 0; i < tree.length(); i++) {
                JSONObject item = tree.optJSONObject(i);
                if (item == null || !"blob".equals(item.optString("type", ""))) continue;
                String filePath = item.optString("path", "");
                if (!filePath.startsWith(JAVA_ROOT) || !filePath.endsWith(".java")) continue;
                String relative = filePath.substring(JAVA_ROOT.length());
                if (relative.contains("/")) continue;
                String base = relative.substring(0, relative.length() - 5);
                String fileId = "file:" + filePath;
                String classId = "class:com.amin.pocketgba." + base;
                addEntity(entities, ids, fileId, "file", relative, filePath);
                addEntity(entities, ids, classId, "class", base, filePath);
                addRelation(relations, "contains", parent, fileId);
                addRelation(relations, "declares", fileId, classId);
            }
        }

        return new JSONObject()
                .put("format", SourceGraphContract.FORMAT)
                .put("version", SourceGraphContract.VERSION)
                .put("authority", "github-cloud-review")
                .put("revision", revision)
                .put("entities", entities)
                .put("relations", relations);
    }

    private static void addEntity(JSONArray out, Set<String> ids, String id, String type, String title, String path) throws Exception {
        if (!ids.add(id)) return;
        out.put(new JSONObject().put("id", id).put("entityType", type).put("title", title).put("path", path));
    }

    private static void addRelation(JSONArray out, String type, String from, String to) throws Exception {
        out.put(new JSONObject().put("id", "source:" + type + ":" + from + ">" + to).put("type", type).put("from", from).put("to", to));
    }
}
