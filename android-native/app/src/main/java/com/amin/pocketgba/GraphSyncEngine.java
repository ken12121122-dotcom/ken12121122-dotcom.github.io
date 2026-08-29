package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Incrementally resolves canonical evidence snapshots into a stable graph state.
 *
 * Identity is authoritative: entityId and relationId are the merge keys. Missing evidence becomes
 * stale instead of being deleted. Rendering and coordinates are intentionally outside this class.
 */
final class GraphSyncEngine {
    static final String FORMAT = "amin-synced-graph-evidence";
    static final int VERSION = 1;

    private GraphSyncEngine() { }

    static JSONObject sync(JSONObject previous, JSONObject incoming, long observedAt) {
        try {
            JSONObject out = empty();
            JSONObject prior = previous == null ? empty() : previous;
            JSONObject next = incoming == null ? CanonicalEvidenceAdapter.empty() : incoming;
            String revision = clean(next.optString("revision", ""));

            Map<String, JSONObject> priorEntities = index(prior.optJSONArray("entities"), "entityId", "id");
            Map<String, JSONObject> incomingEntities = index(next.optJSONArray("entities"), "entityId", "id");
            Map<String, JSONObject> priorRelations = index(prior.optJSONArray("relations"), "relationId", "id");
            Map<String, JSONObject> incomingRelations = index(next.optJSONArray("relations"), "relationId", "id");

            JSONArray entities = out.getJSONArray("entities");
            for (Map.Entry<String, JSONObject> entry : incomingEntities.entrySet()) {
                JSONObject old = priorEntities.get(entry.getKey());
                entities.put(active(entry.getValue(), old, revision, observedAt));
            }
            for (Map.Entry<String, JSONObject> entry : priorEntities.entrySet()) {
                if (!incomingEntities.containsKey(entry.getKey())) entities.put(stale(entry.getValue(), observedAt));
            }

            JSONArray relations = out.getJSONArray("relations");
            for (Map.Entry<String, JSONObject> entry : incomingRelations.entrySet()) {
                JSONObject old = priorRelations.get(entry.getKey());
                relations.put(active(entry.getValue(), old, revision, observedAt));
            }
            for (Map.Entry<String, JSONObject> entry : priorRelations.entrySet()) {
                if (!incomingRelations.containsKey(entry.getKey())) relations.put(stale(entry.getValue(), observedAt));
            }

            out.put("revision", revision);
            out.put("anchorId", next.optString("anchorId", ""));
            out.put("sourceStatus", next.optString("status", ""));
            out.put("observedAt", observedAt);
            out.put("syncStats", stats(priorEntities, incomingEntities, priorRelations, incomingRelations));
            return out;
        } catch (Exception ignored) {
            return empty();
        }
    }

    static JSONObject empty() {
        try {
            return new JSONObject()
                    .put("format", FORMAT)
                    .put("version", VERSION)
                    .put("revision", "")
                    .put("anchorId", "")
                    .put("sourceStatus", "")
                    .put("observedAt", 0L)
                    .put("entities", new JSONArray())
                    .put("relations", new JSONArray())
                    .put("syncStats", new JSONObject());
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static JSONObject active(JSONObject incoming, JSONObject old, String revision, long observedAt) throws Exception {
        JSONObject out = new JSONObject(incoming.toString());
        long firstSeen = old == null ? observedAt : old.optLong("firstSeen", observedAt);
        out.put("firstSeen", firstSeen);
        out.put("lastSeen", observedAt);
        out.put("status", "active");
        out.put("evidenceRevision", clean(incoming.optString("evidenceRevision", revision)));
        out.put("sourceHash", hashEvidence(incoming));
        if (old != null) {
            String oldHash = clean(old.optString("sourceHash", ""));
            out.put("change", oldHash.equals(out.optString("sourceHash", "")) ? "unchanged" : "updated");
        } else {
            out.put("change", "added");
        }
        return out;
    }

    private static JSONObject stale(JSONObject old, long observedAt) throws Exception {
        JSONObject out = new JSONObject(old.toString());
        out.put("status", "stale");
        out.put("change", "missing");
        out.put("missingSince", old.optLong("missingSince", observedAt));
        return out;
    }

    private static JSONObject stats(Map<String, JSONObject> priorEntities,
                                    Map<String, JSONObject> incomingEntities,
                                    Map<String, JSONObject> priorRelations,
                                    Map<String, JSONObject> incomingRelations) throws Exception {
        int entityAdded = 0, entityKept = 0, entityMissing = 0;
        for (String id : incomingEntities.keySet()) {
            if (priorEntities.containsKey(id)) entityKept++; else entityAdded++;
        }
        for (String id : priorEntities.keySet()) if (!incomingEntities.containsKey(id)) entityMissing++;

        int relationAdded = 0, relationKept = 0, relationMissing = 0;
        for (String id : incomingRelations.keySet()) {
            if (priorRelations.containsKey(id)) relationKept++; else relationAdded++;
        }
        for (String id : priorRelations.keySet()) if (!incomingRelations.containsKey(id)) relationMissing++;

        return new JSONObject()
                .put("entityAdded", entityAdded)
                .put("entityKept", entityKept)
                .put("entityMissing", entityMissing)
                .put("relationAdded", relationAdded)
                .put("relationKept", relationKept)
                .put("relationMissing", relationMissing);
    }

    private static Map<String, JSONObject> index(JSONArray array, String primary, String fallback) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = clean(item.optString(primary, item.optString(fallback, "")));
            if (!id.isEmpty() && !out.containsKey(id)) out.put(id, item);
        }
        return out;
    }

    private static String hashEvidence(JSONObject object) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sourceKey", object.optString("sourceKey", ""));
            payload.put("from", object.optString("from", ""));
            payload.put("to", object.optString("to", ""));
            payload.put("type", object.optString("type", ""));
            payload.put("verification", object.optString("verification", ""));
            payload.put("evidence", object.optJSONObject("evidence") == null ? new JSONObject() : object.optJSONObject("evidence"));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
