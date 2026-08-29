package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges evidence emitted by independent scanners/indexers into one canonical batch.
 *
 * This is graph-neutral. It does not infer missing authority layers, choose coordinates, or render.
 * Stable entityId/relationId identity is the only merge authority.
 */
final class CanonicalEvidenceBatchMerger {
    private CanonicalEvidenceBatchMerger() { }

    static JSONObject merge(String revision, JSONObject... sources) {
        try {
            Map<String, JSONObject> entities = new LinkedHashMap<>();
            Map<String, JSONObject> relations = new LinkedHashMap<>();
            JSONArray providers = new JSONArray();
            JSONObject firstGap = null;
            String anchorId = "";
            String status = "";

            if (sources != null) {
                for (JSONObject source : sources) {
                    if (source == null) continue;
                    if (!CanonicalEvidenceAdapter.FORMAT.equals(source.optString("format", ""))) continue;

                    String provider = clean(source.optString("provider", ""));
                    if (!provider.isEmpty() && !contains(providers, provider)) providers.put(provider);
                    if (anchorId.isEmpty()) anchorId = clean(source.optString("anchorId", ""));
                    if (status.isEmpty()) status = clean(source.optString("status", ""));
                    if (firstGap == null && source.optJSONObject("gap") != null) {
                        firstGap = new JSONObject(source.optJSONObject("gap").toString());
                    }

                    mergeArray(entities, source.optJSONArray("entities"), "entityId");
                    mergeArray(relations, source.optJSONArray("relations"), "relationId");
                }
            }

            JSONObject out = CanonicalEvidenceAdapter.empty();
            out.put("revision", clean(revision));
            out.put("anchorId", anchorId);
            out.put("status", status);
            out.put("providers", providers);
            out.put("entities", toArray(entities));
            out.put("relations", toArray(relations));
            if (firstGap != null) out.put("gap", firstGap);
            return out;
        } catch (Exception ignored) {
            return CanonicalEvidenceAdapter.empty();
        }
    }

    private static void mergeArray(Map<String, JSONObject> out, JSONArray values, String primaryId) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) continue;
            String id = clean(item.optString(primaryId, item.optString("id", "")));
            if (id.isEmpty()) continue;
            JSONObject existing = out.get(id);
            if (existing == null || evidenceRank(item) > evidenceRank(existing)) {
                try { out.put(id, new JSONObject(item.toString())); } catch (Exception ignored) { }
            }
        }
    }

    private static int evidenceRank(JSONObject item) {
        String verification = clean(item == null ? "" : item.optString("verification", ""));
        if ("ast_verified".equals(verification)) return 4;
        if ("static_verified".equals(verification)) return 3;
        if ("verified".equals(verification)) return 2;
        if ("gap".equals(verification)) return 1;
        return 0;
    }

    private static JSONArray toArray(Map<String, JSONObject> values) {
        JSONArray out = new JSONArray();
        for (JSONObject value : values.values()) out.put(value);
        return out;
    }

    private static boolean contains(JSONArray values, String target) {
        for (int i = 0; i < values.length(); i++) if (target.equals(values.optString(i, ""))) return true;
        return false;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
