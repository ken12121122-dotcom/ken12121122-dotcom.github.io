package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads CI-produced indexer evidence bundled in the APK. Missing evidence is a valid no-op. */
final class BundledIndexerEvidenceProvider {
    static final String ASSET_PATH = "scanner-evidence/tree-sitter-scanner.json";

    private BundledIndexerEvidenceProvider() { }

    static JSONObject canonical(Context context) {
        if (context == null) return CanonicalEvidenceAdapter.empty();
        try (InputStream input = context.getAssets().open(ASSET_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) raw.append(line).append('\n');
            JSONObject indexed = new JSONObject(raw.toString());
            return IndexerEvidenceAdapter.toCanonical(indexed);
        } catch (Exception ignored) {
            return CanonicalEvidenceAdapter.empty();
        }
    }
}
