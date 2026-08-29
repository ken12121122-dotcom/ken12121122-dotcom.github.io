package com.amin.pocketgba;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Anchor-driven reverse discovery for the Scanner bootstrap capability.
 *
 * This collector never invents missing authority layers. It only records a reverse step when
 * source evidence proves it. The first unsupported required layer is emitted as an explicit Gap.
 */
final class ScannerReverseDiscovery {
    private static final String REPOSITORY = GitHubSourceGraphScanner.REPOSITORY;
    private static final String BRANCH = BuildConfig.SOURCE_REF;
    private static final String SCANNER_PATH = GitHubSourceGraphScanner.SCANNER_ANCHOR_PATH;
    private static final String CALLER_PATH = "android-native/app/src/main/java/com/amin/pocketgba/WikiGraphActivity.java";

    private ScannerReverseDiscovery() { }

    static JSONObject discover(JSONArray tree, String revision) throws Exception {
        String scannerBlob = blobSha(tree, SCANNER_PATH);
        if (scannerBlob.isEmpty()) throw new IllegalStateException("SCANNER_SOURCE_BLOB_NOT_FOUND");

        String scannerSource = fetchBlob(scannerBlob);
        boolean scannerClassVerified = scannerSource.contains("class GitHubSourceGraphScanner");
        boolean scannerEntryVerified = scannerSource.contains("static void syncAsync(");
        if (!scannerClassVerified || !scannerEntryVerified) {
            throw new IllegalStateException("SCANNER_SOURCE_DECLARATION_NOT_VERIFIED");
        }

        JSONObject anchorEvidence = evidence(
                SCANNER_PATH,
                scannerBlob,
                revision,
                "source-content-match",
                "class GitHubSourceGraphScanner + static void syncAsync("
        );

        JSONArray steps = new JSONArray();
        JSONArray inspected = new JSONArray();
        inspected.put(anchorEvidence);

        String callerBlob = blobSha(tree, CALLER_PATH);
        boolean callerVerified = false;
        if (!callerBlob.isEmpty()) {
            String callerSource = fetchBlob(callerBlob);
            boolean methodExists = callerSource.contains("void requestCloudSourceSync()");
            boolean callExists = callerSource.contains("GitHubSourceGraphScanner.syncAsync(");
            callerVerified = methodExists && callExists;
            JSONObject callerEvidence = evidence(
                    CALLER_PATH,
                    callerBlob,
                    revision,
                    "source-call-match",
                    "requestCloudSourceSync() -> GitHubSourceGraphScanner.syncAsync("
            );
            inspected.put(callerEvidence);
            if (callerVerified) {
                steps.put(new JSONObject()
                        .put("index", 1)
                        .put("direction", "reverse")
                        .put("from", "function:GitHubSourceGraphScanner.syncAsync")
                        .put("to", "function:WikiGraphActivity.requestCloudSourceSync")
                        .put("fromLabel", "Scanner · syncAsync")
                        .put("toLabel", "WikiGraphActivity · requestCloudSourceSync")
                        .put("relation", "invoked_by")
                        .put("verification", "static_verified")
                        .put("evidence", callerEvidence));
            }
        }

        // A caller is not automatically a COMMAND. No source/registry evidence currently proves
        // that this Scanner entry is owned by a formal Command, so reverse discovery must stop.
        JSONObject gap = new JSONObject()
                .put("after", callerVerified
                        ? "function:WikiGraphActivity.requestCloudSourceSync"
                        : "function:GitHubSourceGraphScanner.syncAsync")
                .put("expectedLayer", "command")
                .put("code", callerVerified
                        ? "SCANNER_COMMAND_EVIDENCE_NOT_FOUND"
                        : "SCANNER_CALLER_EVIDENCE_NOT_FOUND")
                .put("reason", callerVerified
                        ? "找到 Scanner 的真實呼叫者，但沒有證據證明它是一個正式 COMMAND；禁止自行補出 Command / Capability / Registry / Governance / OWNER。"
                        : "尚未取得 Scanner 呼叫者的可驗證 Source Evidence；反推在此停止。")
                .put("reviewStatus", "gap");

        JSONArray desiredLayers = new JSONArray()
                .put("scanner")
                .put("command")
                .put("capability_or_skill_definition")
                .put("registry")
                .put("governance")
                .put("owner");

        return new JSONObject()
                .put("format", "amin-scanner-reverse-discovery")
                .put("version", 1)
                .put("direction", "authority-reverse")
                .put("revision", revision)
                .put("anchorId", "function:GitHubSourceGraphScanner.syncAsync")
                .put("anchorLabel", "Scanner · syncAsync")
                .put("anchorVerified", true)
                .put("anchorEvidence", anchorEvidence)
                .put("desiredLayers", desiredLayers)
                .put("steps", steps)
                .put("inspectedEvidence", inspected)
                .put("gap", gap)
                .put("status", "stopped_at_gap");
    }

    private static JSONObject evidence(String path, String blobSha, String revision, String rule, String match) throws Exception {
        return new JSONObject()
                .put("provider", "github")
                .put("repository", REPOSITORY)
                .put("branch", BRANCH)
                .put("revision", revision)
                .put("path", path)
                .put("blobSha", blobSha)
                .put("scannerRule", rule)
                .put("match", match);
    }

    private static String blobSha(JSONArray tree, String wantedPath) {
        if (tree == null || wantedPath == null) return "";
        for (int i = 0; i < tree.length(); i++) {
            JSONObject item = tree.optJSONObject(i);
            if (item == null) continue;
            if (!"blob".equals(item.optString("type", ""))) continue;
            if (wantedPath.equals(item.optString("path", ""))) return item.optString("sha", "").trim();
        }
        return "";
    }

    private static String fetchBlob(String blobSha) throws Exception {
        String blobUrl = "https://api.github.com/repos/" + REPOSITORY + "/git/blobs/" + blobSha;
        HttpURLConnection connection = (HttpURLConnection) new URL(blobUrl).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AMIN-Android-ReverseDiscovery");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("GITHUB_BLOB_HTTP_" + code + ":" + blobSha);
        }
        StringBuilder raw = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) raw.append(line);
        } finally {
            connection.disconnect();
        }
        JSONObject payload = new JSONObject(raw.toString());
        if (!"base64".equals(payload.optString("encoding", ""))) {
            throw new IllegalStateException("GITHUB_BLOB_ENCODING_INVALID:" + blobSha);
        }
        String encoded = payload.optString("content", "");
        byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
