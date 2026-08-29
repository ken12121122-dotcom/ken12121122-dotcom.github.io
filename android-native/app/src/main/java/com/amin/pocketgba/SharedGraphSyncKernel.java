package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single domain-neutral merge kernel shared by Step 9 graph adapters.
 *
 * Adapters validate domain schemas and relation semantics before constructing a batch. This kernel
 * only owns stable-identity merge, deterministic de-duplication, first/last seen timestamps and
 * stale-on-missing within one exact graph scope + sync owner + sync partition.
 */
final class SharedGraphSyncKernel {
    static final String BATCH_FORMAT = "amin-graph-sync-batch";
    static final String STATE_FORMAT = "amin-shared-graph-sync-state";
    static final String RESULT_FORMAT = "amin-graph-sync-result";
    static final int VERSION = 1;

    private SharedGraphSyncKernel() { }

    static JSONObject apply(JSONObject previous, JSONObject batch, long observedAt) {
        JSONObject preserved = copy(previous == null ? emptyState() : previous);
        try {
            String previousError = validatePrevious(preserved);
            if (!previousError.isEmpty()) return failure(preserved, previousError);

            String batchError = validateBatch(batch);
            if (!batchError.isEmpty()) return failure(preserved, batchError);

            String scope = clean(batch.getString("graph_scope"));
            JSONObject owner = batch.getJSONObject("sync_owner");
            String ownerKey = canonicalOwnerKey(owner);
            String partition = clean(batch.getString("sync_partition"));
            String ownershipKey = ownershipKey(scope, owner, partition);
            boolean complete = batch.getBoolean("complete_snapshot");

            Indexed priorEntities = indexPrevious(preserved.getJSONArray("entities"));
            Indexed priorRelations = indexPrevious(preserved.getJSONArray("relations"));
            Indexed incomingEntities = indexIncoming(batch.getJSONArray("entities"));
            Indexed incomingRelations = indexIncoming(batch.getJSONArray("relations"));
            if (hasOwnershipCollision(priorEntities.records, incomingEntities.records, ownershipKey)
                    || hasOwnershipCollision(priorRelations.records, incomingRelations.records, ownershipKey)) {
                return failure(preserved, "OWNERSHIP_COLLISION");
            }

            JSONObject out = emptyState();
            JSONArray entities = out.getJSONArray("entities");
            merge(entities, priorEntities.records, incomingEntities.records, batch,
                    ownerKey, ownershipKey, complete, observedAt);
            JSONArray relations = out.getJSONArray("relations");
            merge(relations, priorRelations.records, incomingRelations.records, batch,
                    ownerKey, ownershipKey, complete, observedAt);

            JSONObject stats = stats(
                    priorEntities.records, incomingEntities,
                    priorRelations.records, incomingRelations,
                    ownershipKey, complete);
            out.put("last_sync", new JSONObject()
                    .put("graph_scope", scope)
                    .put("sync_owner", copy(owner))
                    .put("sync_owner_key", ownerKey)
                    .put("sync_partition", partition)
                    .put("ownership_key", ownershipKey)
                    .put("revision", clean(batch.getString("revision")))
                    .put("complete_snapshot", complete)
                    .put("observedAt", observedAt));
            return success(out, stats);
        } catch (Exception error) {
            return failure(preserved, "SYNC_KERNEL_FAILURE");
        }
    }

    static JSONObject emptyState() {
        try {
            return new JSONObject()
                    .put("format", STATE_FORMAT)
                    .put("version", VERSION)
                    .put("entities", new JSONArray())
                    .put("relations", new JSONArray())
                    .put("last_sync", new JSONObject());
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    static String canonicalOwnerKey(JSONObject owner) {
        try {
            return canonicalJson(owner);
        } catch (Exception ignored) {
            return "";
        }
    }

    static String ownershipKey(String scope, JSONObject owner, String partition) {
        return clean(scope) + "|" + canonicalOwnerKey(owner) + "|" + clean(partition);
    }

    private static void merge(JSONArray out,
                              Map<String, JSONObject> prior,
                              Map<String, JSONObject> incoming,
                              JSONObject batch,
                              String ownerKey,
                              String ownershipKey,
                              boolean complete,
                              long observedAt) throws Exception {
        for (Map.Entry<String, JSONObject> entry : incoming.entrySet()) {
            JSONObject old = prior.get(entry.getKey());
            out.put(active(entry.getValue(), old, batch, ownerKey, ownershipKey, observedAt));
        }
        for (Map.Entry<String, JSONObject> entry : prior.entrySet()) {
            if (incoming.containsKey(entry.getKey())) continue;
            JSONObject old = entry.getValue();
            if (complete && ownershipKey.equals(clean(old.optString("ownership_key", "")))) {
                out.put(stale(old, observedAt));
            } else {
                out.put(copy(old));
            }
        }
    }

    private static JSONObject active(JSONObject incoming,
                                     JSONObject old,
                                     JSONObject batch,
                                     String ownerKey,
                                     String ownershipKey,
                                     long observedAt) throws Exception {
        String fingerprint = clean(incoming.getString("content_fingerprint"));
        JSONObject out = new JSONObject()
                .put("stable_id", clean(incoming.getString("stable_id")))
                .put("graph_scope", clean(batch.getString("graph_scope")))
                .put("sync_owner", copy(batch.getJSONObject("sync_owner")))
                .put("sync_owner_key", ownerKey)
                .put("sync_partition", clean(batch.getString("sync_partition")))
                .put("ownership_key", ownershipKey)
                .put("contract_schema_version", clean(batch.getString("contract_schema_version")))
                .put("revision", clean(batch.getString("revision")))
                .put("provenance", copy(batch.getJSONObject("provenance")))
                .put("content_fingerprint", fingerprint)
                .put("payload", copy(incoming.getJSONObject("payload")))
                .put("firstSeen", old == null ? observedAt : old.optLong("firstSeen", observedAt))
                .put("lastSeen", observedAt)
                .put("sync_status", "active");
        if (old == null) {
            out.put("change", "added");
        } else {
            out.put("change", fingerprint.equals(clean(old.optString("content_fingerprint", "")))
                    ? "unchanged" : "updated");
        }
        return out;
    }

    private static JSONObject stale(JSONObject old, long observedAt) throws Exception {
        JSONObject out = copy(old);
        out.put("sync_status", "stale");
        out.put("change", "missing");
        out.put("missingSince", old.optLong("missingSince", observedAt));
        return out;
    }

    private static String validatePrevious(JSONObject previous) {
        if (previous == null) return "INVALID_PREVIOUS_STATE";
        if (!STATE_FORMAT.equals(previous.optString("format", ""))) return "INVALID_PREVIOUS_STATE";
        if (previous.optInt("version", -1) != VERSION) return "INVALID_PREVIOUS_STATE";
        if (previous.optJSONArray("entities") == null || previous.optJSONArray("relations") == null) {
            return "INVALID_PREVIOUS_STATE";
        }
        return "";
    }

    private static String validateBatch(JSONObject batch) {
        if (batch == null) return "INVALID_BATCH";
        if (!BATCH_FORMAT.equals(batch.optString("format", "")) || batch.optInt("version", -1) != VERSION) {
            return "INVALID_BATCH_FORMAT";
        }
        if (!batch.optBoolean("committed", false)) return "UNCOMMITTED_BATCH";
        if (!batch.has("complete_snapshot")) return "INVALID_BATCH_COMPLETENESS";
        String batchStatus = clean(batch.optString("batch_status", ""));
        if ("failed".equals(batchStatus) || "cancelled".equals(batchStatus)) return "BATCH_NOT_APPLICABLE";
        boolean completeSnapshot = batch.optBoolean("complete_snapshot", false);
        if (completeSnapshot && !"complete".equals(batchStatus)) return "INVALID_BATCH_COMPLETENESS";
        if (!completeSnapshot && !"partial".equals(batchStatus)) return "INVALID_BATCH_COMPLETENESS";
        if (completeSnapshot) {
            JSONObject proof = batch.optJSONObject("completeness_proof");
            if (proof == null || proof.length() == 0) return "MISSING_COMPLETENESS_PROOF";
        }
        String scope = clean(batch.optString("graph_scope", ""));
        if (!("knowledge".equals(scope) || "work".equals(scope) || "bridge".equals(scope))) {
            return "INVALID_GRAPH_SCOPE";
        }
        JSONObject owner = batch.optJSONObject("sync_owner");
        if (owner == null || owner.length() == 0) return "INVALID_BATCH_SYNC_OWNER";
        if (clean(batch.optString("sync_partition", "")).isEmpty()) return "INVALID_BATCH_PARTITION";
        if (clean(batch.optString("contract_schema_version", "")).isEmpty()) return "INVALID_BATCH_SCHEMA_VERSION";
        if (clean(batch.optString("revision", "")).isEmpty()) return "INVALID_BATCH_REVISION";
        JSONObject provenance = batch.optJSONObject("provenance");
        if (provenance == null || provenance.length() == 0) return "INVALID_BATCH_PROVENANCE";
        JSONArray entities = batch.optJSONArray("entities");
        JSONArray relations = batch.optJSONArray("relations");
        if (entities == null || relations == null) return "INVALID_BATCH_RECORDS";
        String entityError = validateRecords(entities);
        if (!entityError.isEmpty()) return entityError;
        return validateRecords(relations);
    }

    private static String validateRecords(JSONArray records) {
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) return "INVALID_BATCH_RECORD";
            if (clean(record.optString("stable_id", "")).isEmpty()) return "INVALID_RECORD_IDENTITY";
            if (clean(record.optString("content_fingerprint", "")).isEmpty()) return "INVALID_RECORD_FINGERPRINT";
            if (record.optJSONObject("payload") == null) return "INVALID_RECORD_PAYLOAD";
        }
        return "";
    }

    private static Indexed indexIncoming(JSONArray array) {
        Indexed out = new Indexed();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = clean(item.optString("stable_id", ""));
            if (out.records.containsKey(id)) {
                out.duplicates++;
            } else {
                out.records.put(id, item);
            }
        }
        return out;
    }

    private static Indexed indexPrevious(JSONArray array) {
        Indexed out = new Indexed();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = clean(item.optString("stable_id", ""));
            if (!id.isEmpty() && !out.records.containsKey(id)) out.records.put(id, item);
        }
        return out;
    }

    private static boolean hasOwnershipCollision(Map<String, JSONObject> prior,
                                                 Map<String, JSONObject> incoming,
                                                 String incomingOwnershipKey) {
        for (String id : incoming.keySet()) {
            JSONObject existing = prior.get(id);
            if (existing != null
                    && !incomingOwnershipKey.equals(clean(existing.optString("ownership_key", "")))) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject stats(Map<String, JSONObject> priorEntities,
                                    Indexed incomingEntities,
                                    Map<String, JSONObject> priorRelations,
                                    Indexed incomingRelations,
                                    String ownershipKey,
                                    boolean complete) throws Exception {
        int entityAdded = 0, entityKept = 0, entityMissing = 0;
        for (String id : incomingEntities.records.keySet()) {
            if (priorEntities.containsKey(id)) entityKept++; else entityAdded++;
        }
        if (complete) {
            for (Map.Entry<String, JSONObject> entry : priorEntities.entrySet()) {
                if (ownershipKey.equals(entry.getValue().optString("ownership_key", ""))
                        && !incomingEntities.records.containsKey(entry.getKey())) entityMissing++;
            }
        }

        int relationAdded = 0, relationKept = 0, relationMissing = 0;
        for (String id : incomingRelations.records.keySet()) {
            if (priorRelations.containsKey(id)) relationKept++; else relationAdded++;
        }
        if (complete) {
            for (Map.Entry<String, JSONObject> entry : priorRelations.entrySet()) {
                if (ownershipKey.equals(entry.getValue().optString("ownership_key", ""))
                        && !incomingRelations.records.containsKey(entry.getKey())) relationMissing++;
            }
        }

        return new JSONObject()
                .put("entityAdded", entityAdded)
                .put("entityKept", entityKept)
                .put("entityMissing", entityMissing)
                .put("entityDuplicates", incomingEntities.duplicates)
                .put("relationAdded", relationAdded)
                .put("relationKept", relationKept)
                .put("relationMissing", relationMissing)
                .put("relationDuplicates", incomingRelations.duplicates);
    }

    private static JSONObject success(JSONObject state, JSONObject stats) {
        try {
            return new JSONObject()
                    .put("format", RESULT_FORMAT)
                    .put("version", VERSION)
                    .put("success", true)
                    .put("errorCode", "")
                    .put("state", state)
                    .put("syncStats", stats);
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static JSONObject failure(JSONObject preserved, String errorCode) {
        try {
            return new JSONObject()
                    .put("format", RESULT_FORMAT)
                    .put("version", VERSION)
                    .put("success", false)
                    .put("errorCode", errorCode)
                    .put("state", copy(preserved))
                    .put("syncStats", new JSONObject());
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static String canonicalJson(Object value) throws Exception {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                String key = keys.get(i);
                out.append(JSONObject.quote(key)).append(':').append(canonicalJson(object.get(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonicalJson(array.get(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof String) return JSONObject.quote((String) value);
        return String.valueOf(value);
    }

    private static JSONObject copy(JSONObject object) {
        if (object == null) return new JSONObject();
        try {
            return new JSONObject(object.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static final class Indexed {
        final Map<String, JSONObject> records = new LinkedHashMap<>();
        int duplicates;
    }
}
