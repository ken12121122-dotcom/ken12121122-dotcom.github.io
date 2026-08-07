package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class ResourceMappingStore {
    private static final String PREFS = "amin_resource_mapping_candidates";
    private final SharedPreferences prefs;

    ResourceMappingStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void save(String nodeId, String nodeTitle, String provider, String resourceTitle, String url, String status) throws JSONException {
        if (nodeId == null || nodeId.trim().isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (!ExternalResourcePolicy.isAllowedHttps(url)) throw new IllegalArgumentException("HTTPS URL required");
        String normalizedStatus = "active".equals(status) ? "active" : "candidate";
        JSONObject object = new JSONObject();
        object.put("nodeId", nodeId.trim());
        object.put("nodeTitle", safe(nodeTitle));
        object.put("provider", provider == null || provider.trim().isEmpty() ? "web" : provider.trim());
        object.put("title", resourceTitle == null || resourceTitle.trim().isEmpty() ? url.trim() : resourceTitle.trim());
        object.put("url", url.trim());
        object.put("status", normalizedStatus);
        object.put("scope", "local");
        object.put("updatedAt", System.currentTimeMillis());
        prefs.edit().putString(nodeId.trim(), object.toString()).apply();
    }

    void delete(String nodeId) {
        if (nodeId != null) prefs.edit().remove(nodeId.trim()).apply();
    }

    JSONObject get(String nodeId) {
        if (nodeId == null) return null;
        String raw = prefs.getString(nodeId.trim(), null);
        if (raw == null) return null;
        try { return new JSONObject(raw); } catch (JSONException ignored) { return null; }
    }

    String allJson() {
        JSONArray result = new JSONArray();
        List<String> keys = new ArrayList<>(prefs.getAll().keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Object raw = prefs.getAll().get(key);
            if (!(raw instanceof String)) continue;
            try { result.put(new JSONObject((String) raw)); } catch (JSONException ignored) {}
        }
        return result.toString();
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
}
