package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Evidence adapter facade for the single SharedGraphSyncKernel.
 *
 * The Evidence state format remains compatible with the existing store and UnifiedGraphProvider.
 * Stable merge, de-duplication and stale semantics live only in the shared kernel; Evidence field
 * selection and hashing stay in CanonicalEvidenceAdapter.
 */
final class GraphSyncEngine {
    static final String FORMAT = "amin-synced-graph-evidence";
    static final int VERSION = 1;

    private static final String GRAPH_SCOPE = "knowledge";
    private static final String SYNC_PARTITION = "scanner_evidence";
    private static final String CONTRACT_SCHEMA_VERSION = "canonical-evidence-v1";

    private GraphSyncEngine() { }

    static JSONObject sync(JSONObject previous, JSONObject incoming, long observedAt) {
        JSONObject preserved = validEvidenceState(previous) ? copy(previous) : empty();
        try {
            if (!validCanonicalEvidence(incoming)) return preserved;

            JSONObject result = SharedGraphSyncKernel.apply(
                    toGenericState(preserved, observedAt), toBatch(incoming), observedAt);
            if (!result.optBoolean("success", false)) return preserved;
            return fromGenericState(result.getJSONObject("state"), incoming,
                    result.getJSONObject("syncStats"), observedAt);
        } catch (Exception ignored) {
            return preserved;
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

    private static JSONObject toBatch(JSONObject incoming) throws Exception {
        return new JSONObject()
                .put("format", SharedGraphSyncKernel.BATCH_FORMAT)
                .put("version", SharedGraphSyncKernel.VERSION)
                .put("contract_schema_version", CONTRACT_SCHEMA_VERSION)
                .put("graph_scope", GRAPH_SCOPE)
                .put("sync_owner", evidenceOwner())
                .put("sync_partition", SYNC_PARTITION)
                .put("revision", clean(incoming.getString("revision")))
                .put("provenance", new JSONObject()
                        .put("adapter", "CanonicalEvidenceAdapter")
                        .put("source_format", CanonicalEvidenceAdapter.FORMAT))
                .put("committed", true)
                .put("batch_status", "complete")
                .put("complete_snapshot", true)
                .put("completeness_proof", new JSONObject()
                        .put("kind", "canonical_evidence_snapshot")
                        .put("entity_count", incoming.getJSONArray("entities").length())
                        .put("relation_count", incoming.getJSONArray("relations").length()))
                .put("entities", toGenericRecords(incoming.getJSONArray("entities"), "entityId"))
                .put("relations", toGenericRecords(incoming.getJSONArray("relations"), "relationId"));
    }

    private static JSONArray toGenericRecords(JSONArray evidence, String identityField) throws Exception {
        JSONArray out = new JSONArray();
        for (int i = 0; i < evidence.length(); i++) {
            JSONObject item = evidence.optJSONObject(i);
            if (item == null) {
                out.put(JSONObject.NULL);
                continue;
            }
            out.put(new JSONObject()
                    .put("stable_id", clean(item.optString(identityField, item.optString("id", ""))))
                    .put("content_fingerprint", CanonicalEvidenceAdapter.contentFingerprint(item))
                    .put("payload", copy(item)));
        }
        return out;
    }

    private static JSONObject toGenericState(JSONObject evidenceState, long observedAt) throws Exception {
        JSONObject out = SharedGraphSyncKernel.emptyState();
        JSONArray entities = evidenceState.getJSONArray("entities");
        for (int i = 0; i < entities.length(); i++) {
            JSONObject item = entities.optJSONObject(i);
            if (item != null) {
                out.getJSONArray("entities").put(toGenericPreviousRecord(item, "entityId", observedAt));
            }
        }
        JSONArray relations = evidenceState.getJSONArray("relations");
        for (int i = 0; i < relations.length(); i++) {
            JSONObject item = relations.optJSONObject(i);
            if (item != null) {
                out.getJSONArray("relations").put(toGenericPreviousRecord(item, "relationId", observedAt));
            }
        }
        return out;
    }

    private static JSONObject toGenericPreviousRecord(JSONObject item,
                                                      String identityField,
                                                      long observedAt) throws Exception {
        JSONObject owner = evidenceOwner();
        String fingerprint = clean(item.optString("sourceHash", ""));
        if (fingerprint.isEmpty()) fingerprint = CanonicalEvidenceAdapter.contentFingerprint(item);
        JSONObject out = new JSONObject()
                .put("stable_id", clean(item.optString(identityField, item.optString("id", ""))))
                .put("graph_scope", GRAPH_SCOPE)
                .put("sync_owner", owner)
                .put("sync_owner_key", SharedGraphSyncKernel.canonicalOwnerKey(owner))
                .put("sync_partition", SYNC_PARTITION)
                .put("ownership_key", SharedGraphSyncKernel.ownershipKey(GRAPH_SCOPE, owner, SYNC_PARTITION))
                .put("contract_schema_version", CONTRACT_SCHEMA_VERSION)
                .put("revision", clean(item.optString("evidenceRevision", "legacy")))
                .put("provenance", new JSONObject().put("adapter", "CanonicalEvidenceAdapter"))
                .put("content_fingerprint", fingerprint)
                .put("payload", evidencePayload(item))
                .put("firstSeen", item.optLong("firstSeen", observedAt))
                .put("lastSeen", item.optLong("lastSeen", observedAt))
                .put("sync_status", "stale".equals(item.optString("status", "")) ? "stale" : "active")
                .put("change", item.optString("change", "unchanged"));
        if (item.has("missingSince")) out.put("missingSince", item.optLong("missingSince", 0L));
        return out;
    }

    private static JSONObject fromGenericState(JSONObject generic,
                                               JSONObject incoming,
                                               JSONObject stats,
                                               long observedAt) throws Exception {
        JSONObject out = empty();
        copyEvidenceRecords(generic.getJSONArray("entities"), out.getJSONArray("entities"));
        copyEvidenceRecords(generic.getJSONArray("relations"), out.getJSONArray("relations"));
        out.put("revision", clean(incoming.getString("revision")));
        out.put("anchorId", incoming.optString("anchorId", ""));
        out.put("sourceStatus", incoming.optString("status", ""));
        out.put("observedAt", observedAt);
        out.put("syncStats", copy(stats));
        return out;
    }

    private static void copyEvidenceRecords(JSONArray generic, JSONArray evidence) throws Exception {
        for (int i = 0; i < generic.length(); i++) {
            JSONObject record = generic.optJSONObject(i);
            if (record == null) continue;
            JSONObject item = copy(record.getJSONObject("payload"));
            item.put("firstSeen", record.optLong("firstSeen", 0L));
            item.put("lastSeen", record.optLong("lastSeen", 0L));
            item.put("status", record.optString("sync_status", "active"));
            item.put("sourceHash", record.optString("content_fingerprint", ""));
            item.put("change", record.optString("change", "unchanged"));
            if (clean(item.optString("evidenceRevision", "")).isEmpty()) {
                item.put("evidenceRevision", clean(record.optString("revision", "")));
            }
            if (record.has("missingSince")) item.put("missingSince", record.optLong("missingSince", 0L));
            evidence.put(item);
        }
    }

    private static JSONObject evidencePayload(JSONObject item) {
        JSONObject out = copy(item);
        out.remove("firstSeen");
        out.remove("lastSeen");
        out.remove("status");
        out.remove("sourceHash");
        out.remove("change");
        out.remove("missingSince");
        return out;
    }

    private static JSONObject evidenceOwner() throws Exception {
        return new JSONObject()
                .put("provider", "amin_scanner")
                .put("adapter", "canonical_evidence_v1");
    }

    private static boolean validCanonicalEvidence(JSONObject incoming) {
        return incoming != null
                && CanonicalEvidenceAdapter.FORMAT.equals(incoming.optString("format", ""))
                && incoming.optInt("version", -1) == CanonicalEvidenceAdapter.VERSION
                && !clean(incoming.optString("revision", "")).isEmpty()
                && incoming.optJSONArray("entities") != null
                && incoming.optJSONArray("relations") != null;
    }

    private static boolean validEvidenceState(JSONObject state) {
        return state != null
                && FORMAT.equals(state.optString("format", ""))
                && state.optInt("version", -1) == VERSION
                && state.optJSONArray("entities") != null
                && state.optJSONArray("relations") != null;
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
}
