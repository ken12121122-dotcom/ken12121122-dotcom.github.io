package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Source Graph ingestion boundary. Accepted cloud graph wins; bundled asset remains rollback/fallback. */
final class SourceGraphProvider {
    private static final String ASSET = "amin-source-graph.json";

    private SourceGraphProvider() { }

    static String graphJson(Context context) { return graph(context).toString(); }

    static JSONObject graph(Context context) {
        if (context == null) return SourceGraphContract.empty("CONTEXT_REQUIRED");
        try {
            JSONObject accepted = new CloudSourceGraphStore(context).accepted();
            if (accepted != null) {
                JSONObject normalized = SourceGraphContract.normalize(accepted);
                if (normalized.optBoolean("valid", false)) {
                    normalized.put("authority", "github-cloud-accepted");
                    return normalized;
                }
            }
        } catch (Exception ignored) { }
        return bundled(context);
    }

    static JSONObject pending(Context context) {
        if (context == null) return null;
        try {
            JSONObject value = new CloudSourceGraphStore(context).pending();
            if (value == null) return null;
            JSONObject normalized = SourceGraphContract.normalize(value);
            return normalized.optBoolean("valid", false) ? normalized : null;
        } catch (Exception ignored) { return null; }
    }

    static JSONObject reviewState(Context context) {
        if (context == null) return new JSONObject();
        return new CloudSourceGraphStore(context).reviewState();
    }

    private static JSONObject bundled(Context context) {
        try (InputStream input = context.getAssets().open(ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            JSONObject normalized = SourceGraphContract.normalize(new JSONObject(output.toString("UTF-8")));
            if (!normalized.optBoolean("valid", false)) return normalized;
            normalized.put("authority", "bundled-fallback");
            return normalized;
        } catch (Exception error) {
            return SourceGraphContract.empty("SOURCE_GRAPH_LOAD_FAILED:" + error.getClass().getSimpleName());
        }
    }
}
