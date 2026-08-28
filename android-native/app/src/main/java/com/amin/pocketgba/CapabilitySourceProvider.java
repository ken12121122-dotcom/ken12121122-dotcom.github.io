package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Read-only mapping boundary. Capability Registry remains authoritative; findings never auto-register Connect. */
final class CapabilitySourceProvider {
    private static final String ASSET = "amin-capability-source-map.json";

    private CapabilitySourceProvider() { }

    static JSONObject evaluate(Context context, JSONArray capabilityNodes, JSONObject sourceGraph, JSONArray registeredRelations) {
        if (context == null) return CapabilitySourceMap.evaluate(new JSONObject(), capabilityNodes, sourceGraph, registeredRelations);
        try (InputStream input = context.getAssets().open(ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            JSONObject raw = new JSONObject(output.toString("UTF-8"));
            return CapabilitySourceMap.evaluate(raw, capabilityNodes, sourceGraph, registeredRelations);
        } catch (Exception error) {
            try {
                JSONObject fallback = new JSONObject();
                fallback.put("authority", "repository-baseline");
                fallback.put("loadError", "CAPABILITY_SOURCE_LOAD_FAILED:" + error.getClass().getSimpleName());
                return CapabilitySourceMap.evaluate(fallback, capabilityNodes, sourceGraph, registeredRelations);
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }
}
