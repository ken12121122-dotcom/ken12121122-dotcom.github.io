package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GitHub evidence collector for Scanner reverse discovery.
 *
 * Scanner providers are merged into one canonical evidence batch before sync. This allows
 * authority-layer probes to run in one scan cycle without adding another graph or fabricating
 * semantic links that are not evidenced.
 */
final class GitHubSourceGraphScanner {
    static final String REPOSITORY = "ken12121122-dotcom/ken12121122-dotcom.github.io";
    static final String BRANCH = "release/android";
    static final String SCANNER_ANCHOR_PATH = "android-native/app/src/main/java/com/amin/pocketgba/GitHubSourceGraphScanner.java";
    static final String SCANNER_CALLER_PATH = "android-native/app/src/main/java/com/amin/pocketgba/WikiGraphActivity.java";
    private static final AtomicBoolean SYNC_IN_FLIGHT = new AtomicBoolean(false);

    private GitHubSourceGraphScanner() { }

    static JSONObject scan() throws Exception {
        String encodedRef = URLEncoder.encode(BRANCH, "UTF-8").replace("+", "%20");
        String treeUrl = "https://api.github.com/repos/" + REPOSITORY + "/git/trees/" + encodedRef + "?recursive=1";
        HttpURLConnection connection = (HttpURLConnection) new URL(treeUrl).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AMIN-Android-EvidenceCollector");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("GITHUB_TREE_HTTP_" + code);

        StringBuilder raw = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) raw.append(line);
        } finally {
            connection.disconnect();
        }

        JSONObject treeRoot = new JSONObject(raw.toString());
        String revision = treeRoot.optString("sha", "").trim();
        if (revision.isEmpty()) throw new IllegalStateException("GITHUB_TREE_REVISION_MISSING");
        JSONArray tree = treeRoot.optJSONArray("tree");

        JSONObject reverseDiscovery = ScannerReverseDiscovery.discover(tree, revision);
        JSONObject reverseCanonical = CanonicalEvidenceAdapter.fromReverseDiscovery(reverseDiscovery)
                .put("provider", "github-source-reverse");

        // Probe all known architecture layers from this exact tree snapshot. These are source
        // artifacts only; they intentionally contain no authority CONNECTs.
        JSONObject architectureArtifacts = ArchitectureArtifactEvidenceScanner.scan(tree, revision);

        // Live providers available without APK assets. Bundled mature-indexer evidence is merged
        // later in syncAsync because reading APK assets requires Context.
        JSONObject canonicalEvidence = CanonicalEvidenceBatchMerger.merge(
                revision,
                reverseCanonical,
                architectureArtifacts
        );
        JSONArray canonicalEntities = canonicalEvidence.optJSONArray("entities");
        if (canonicalEntities == null || canonicalEntities.length() == 0) {
            throw new IllegalStateException("CANONICAL_EVIDENCE_EMPTY");
        }

        JSONObject snapshot = new JSONObject()
                .put("format", SourceGraphContract.FORMAT)
                .put("version", SourceGraphContract.VERSION)
                .put("authority", "github-cloud-review")
                .put("revision", revision)
                .put("reviewScope", "scanner-evidence-batch-v3")
                .put("generatedFrom", "github-cloud-evidence")
                .put("scanMode", "evidence-only-reverse-v2")
                .put("anchorId", reverseDiscovery.optString("anchorId", ""))
                .put("entities", new JSONArray())
                .put("relations", new JSONArray())
                .put("reverseDiscovery", reverseDiscovery)
                .put("architectureArtifacts", architectureArtifacts)
                .put("canonicalEvidence", canonicalEvidence);

        JSONObject normalized = SourceGraphContract.normalize(snapshot);
        if (!normalized.optBoolean("valid", false)) throw new IllegalStateException("SOURCE_SNAPSHOT_INVALID");
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
                JSONObject snapshot = scan();
                String revision = snapshot.optString("revision", "").trim();
                JSONObject liveCanonical = snapshot.optJSONObject("canonicalEvidence");
                if (liveCanonical == null) throw new IllegalStateException("CANONICAL_EVIDENCE_MISSING");

                JSONObject bundledCanonical = BundledIndexerEvidenceProvider.canonical(app);
                JSONObject canonical = CanonicalEvidenceBatchMerger.merge(
                        revision,
                        liveCanonical,
                        bundledCanonical
                );
                snapshot.put("bundledIndexerEvidence", bundledCanonical);
                snapshot.put("canonicalEvidence", canonical);
                new CloudSourceGraphStore(app).stage(revision, snapshot);

                GraphEvidenceSyncStore syncStore = new GraphEvidenceSyncStore(app);
                JSONObject previous = syncStore.current();
                JSONObject next = syncStore.sync(canonical, System.currentTimeMillis());
                GitHubSourceSyncState.ready(revision);

                GraphGrowthPlaybackController.play(app, previous, next);
            } catch (Exception error) {
                GraphGrowthPlaybackStore.clear();
                GitHubSourceSyncState.failed(error);
            } finally {
                SYNC_IN_FLIGHT.set(false);
                if (onFinished != null) onFinished.run();
            }
        }, "amin-source-sync").start();
    }
}
