package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Read-only Source Graph ingestion boundary. Runtime registries remain authoritative for capabilities. */
final class SourceGraphProvider {
    private static final String ASSET = "amin-source-graph.json";

    private SourceGraphProvider() { }

    static String graphJson(Context context) {
        return graph(context).toString();
    }

    static JSONObject graph(Context context) {
        if (context == null) return SourceGraphContract.empty("CONTEXT_REQUIRED");
        try (InputStream input = context.getAssets().open(ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            JSONObject normalized = SourceGraphContract.normalize(new JSONObject(output.toString("UTF-8")));
            if (!normalized.optBoolean("valid", false)) return normalized;
            return normalized;
        } catch (Exception error) {
            return SourceGraphContract.empty("SOURCE_GRAPH_LOAD_FAILED:" + error.getClass().getSimpleName());
        }
    }
}
