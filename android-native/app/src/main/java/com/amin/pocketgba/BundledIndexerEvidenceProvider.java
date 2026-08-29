package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads CI-produced indexer evidence bundled in the APK. Missing or mismatched evidence is a valid no-op. */
final class BundledIndexerEvidenceProvider {
    static final String ASSET_PATH = "scanner-evidence/tree-sitter-scanner.json";
    static final String REVISION_MISMATCH = "BUNDLED_EVIDENCE_REVISION_MISMATCH";
    static final String REVISION_MISSING = "BUNDLED_EVIDENCE_REVISION_MISSING";

    private BundledIndexerEvidenceProvider() { }

    static JSONObject loadForRevision(Context context, String liveRevision) {
        if (context == null) return unavailable("CONTEXT_MISSING", "", clean(liveRevision));
        try (InputStream input = context.getAssets().open(ASSET_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) raw.append(line).append('\n');
            return canonicalForRevision(new JSONObject(raw.toString()), liveRevision);
        } catch (Exception ignored) {
            return unavailable("BUNDLED_EVIDENCE_UNAVAILABLE", "", clean(liveRevision));
        }
    }

    static JSONObject canonicalForRevision(JSONObject indexed, String liveRevision) {
        String expected = clean(liveRevision);
        String bundled = indexed == null ? "" : clean(indexed.optString("revision", ""));
        if (expected.isEmpty() || bundled.isEmpty()) {
            return unavailable(REVISION_MISSING, bundled, expected);
        }
        if (!expected.equals(bundled)) {
            return unavailable(REVISION_MISMATCH, bundled, expected);
        }

        JSONObject canonical = IndexerEvidenceAdapter.toCanonical(indexed);
        try {
            canonical.put("revisionGate", "matched");
            canonical.put("bundledRevision", bundled);
            canonical.put("liveRevision", expected);
        } catch (Exception ignored) { }
        return canonical;
    }

    private static JSONObject unavailable(String code, String bundledRevision, String liveRevision) {
        JSONObject out = CanonicalEvidenceAdapter.empty();
        try {
            out.put("provider", "tree-sitter-java");
            out.put("status", "revision_rejected");
            out.put("revisionGate", code);
            out.put("bundledRevision", clean(bundledRevision));
            out.put("liveRevision", clean(liveRevision));
            out.put("gap", new JSONObject()
                    .put("code", code)
                    .put("expectedLayer", "indexer_evidence")
                    .put("reason", "Bundled AST evidence was not merged because its revision could not be proven equal to the live GitHub scan revision."));
        } catch (Exception ignored) { }
        return out;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
