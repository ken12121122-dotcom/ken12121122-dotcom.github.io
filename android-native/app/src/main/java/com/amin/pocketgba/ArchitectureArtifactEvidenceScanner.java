package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact GitHub-tree discovery for architecture-layer source artifacts.
 *
 * These artifacts are source evidence only. File existence may suggest a layer candidate, but it
 * never proves Scanner ownership, authority, Registry membership, Governance, Permission, or OWNER.
 * Therefore this scanner deliberately emits no semantic authority relations.
 */
final class ArchitectureArtifactEvidenceScanner {
    private static final String PROVIDER = "github-tree-architecture-artifacts";

    private static final Artifact[] TARGETS = new Artifact[] {
            new Artifact("command", "VoiceCommandCatalog.java"),
            new Artifact("capability", "CapabilitySourceMap.java"),
            new Artifact("registry", "NodeRegistry.java"),
            new Artifact("governance", "RegistryApprovalActivity.java"),
            new Artifact("permission", "PermissionCenterActivity.java")
    };

    private ArchitectureArtifactEvidenceScanner() { }

    static JSONObject scan(JSONArray tree, String revision) {
        try {
            JSONObject out = CanonicalEvidenceAdapter.empty();
            out.put("provider", PROVIDER);
            out.put("revision", clean(revision));
            out.put("status", "source_artifacts_discovered");

            JSONArray entities = out.getJSONArray("entities");
            Map<String, JSONObject> blobs = indexTree(tree);
            for (Artifact target : TARGETS) {
                String path = javaPath(target.fileName);
                JSONObject blob = blobs.get(path);
                if (blob == null) continue;
                String sha = clean(blob.optString("sha", ""));
                if (sha.isEmpty()) continue;

                String entityId = "source-artifact:" + path;
                JSONObject evidence = new JSONObject()
                        .put("repository", GitHubSourceGraphScanner.REPOSITORY)
                        .put("revision", clean(revision))
                        .put("path", path)
                        .put("blobSha", sha)
                        .put("evidenceKind", "tree_path_exact")
                        .put("candidateLayer", target.layer)
                        .put("authorityVerified", false);

                entities.put(new JSONObject()
                        .put("entityId", entityId)
                        .put("id", entityId)
                        .put("title", "Candidate · " + target.layer.toUpperCase() + " · " + target.fileName)
                        .put("kind", "source_artifact")
                        .put("parentId", "")
                        .put("verification", "static_verified")
                        .put("evidenceRevision", clean(revision))
                        .put("sourceKey", GitHubSourceGraphScanner.REPOSITORY + "|" + path)
                        .put("candidateLayer", target.layer)
                        .put("authorityVerified", false)
                        .put("evidence", evidence));
            }
            return out;
        } catch (Exception ignored) {
            return CanonicalEvidenceAdapter.empty();
        }
    }

    private static Map<String, JSONObject> indexTree(JSONArray tree) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        if (tree == null) return out;
        for (int i = 0; i < tree.length(); i++) {
            JSONObject entry = tree.optJSONObject(i);
            if (entry == null || !"blob".equals(entry.optString("type", ""))) continue;
            String path = clean(entry.optString("path", ""));
            if (!path.isEmpty()) out.put(path, entry);
        }
        return out;
    }

    private static String javaPath(String fileName) {
        return "android-native/app/src/main/java/com/amin/pocketgba/" + fileName;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static final class Artifact {
        final String layer;
        final String fileName;

        Artifact(String layer, String fileName) {
            this.layer = layer;
            this.fileName = fileName;
        }
    }
}
