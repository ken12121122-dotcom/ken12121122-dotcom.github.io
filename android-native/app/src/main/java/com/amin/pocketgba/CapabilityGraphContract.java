package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Executable Step 11 contract. It validates capability records before the shared merge kernel. */
final class CapabilityGraphContract {
    static final String SCHEMA_VERSION = "capability-graph-v1";
    static final String CAPABILITY = "CAPABILITY";
    static final String SKILL = "SKILL";
    static final String WORKFLOW = "WORKFLOW";
    static final String TOOL = "TOOL";
    static final String CONNECTOR = "CONNECTOR";
    static final String COMMAND = "COMMAND";
    static final String CERTIFICATION = "CERTIFICATION";

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            CAPABILITY, SKILL, WORKFLOW, TOOL, CONNECTOR, COMMAND, CERTIFICATION));
    private static final Set<String> LIFECYCLES = new HashSet<>(Arrays.asList(
            "discovered", "proposed", "approved", "training", "certified",
            "suspended", "revoked"));
    private static final Set<String> REVIEW_STATUSES = new HashSet<>(Arrays.asList(
            "generated", "reviewed", "approved", "rejected", "deprecated"));
    private static final Set<String> CERTIFICATION_STATUSES = new HashSet<>(Arrays.asList(
            "not_certified", "training", "certified", "expired", "suspended", "revoked"));
    private static final Set<String> CAPABILITY_RELATIONS = new HashSet<>(Arrays.asList(
            "contains", "opens", "uses", "reads_from", "writes_to", "executes", "depends_on"));
    private static final Set<String> CERTIFICATION_RELATIONS = new HashSet<>(Arrays.asList(
            "certified_by", "certifies"));

    private CapabilityGraphContract() { }

    static JSONObject entity(String stableId, String type, JSONObject attributes) throws Exception {
        String id = clean(stableId);
        String normalizedType = normalizeType(type);
        JSONObject source = attributes == null ? new JSONObject() : new JSONObject(attributes.toString());
        if (id.isEmpty()) throw new IllegalArgumentException("CAPABILITY_ID_REQUIRED");
        if (!TYPES.contains(normalizedType)) throw new IllegalArgumentException("UNREGISTERED_CAPABILITY_TYPE");

        String lifecycle = clean(source.optString("lifecycle_status", "approved")).toLowerCase();
        String review = clean(source.optString("review_status", "approved")).toLowerCase();
        String certification = clean(source.optString("certification_status", "not_certified")).toLowerCase();
        if (!LIFECYCLES.contains(lifecycle)) throw new IllegalArgumentException("INVALID_CAPABILITY_LIFECYCLE");
        if (!REVIEW_STATUSES.contains(review)) throw new IllegalArgumentException("INVALID_REVIEW_STATUS");
        if (!CERTIFICATION_STATUSES.contains(certification)) {
            throw new IllegalArgumentException("INVALID_CERTIFICATION_STATUS");
        }
        JSONObject certificationScope = source.optJSONObject("certification_scope");
        if (certificationScope == null) certificationScope = emptyCertificationScope();
        boolean certified = "certified".equals(lifecycle) || "certified".equals(certification);
        if (certified) {
            JSONObject approval = source.optJSONObject("human_approval");
            JSONArray evidence = source.optJSONArray("certification_evidence");
            if (approval == null || !"approved".equals(approval.optString("review_status", ""))
                    || !validSourceRecords(evidence)) {
                throw new IllegalArgumentException("CERTIFICATION_REQUIRES_APPROVAL_AND_EVIDENCE");
            }
            if (!validCertificationScope(certificationScope)) {
                throw new IllegalArgumentException("CERTIFICATION_SCOPE_REQUIRED");
            }
        }

        JSONArray records = source.optJSONArray("source_records");
        if (!validSourceRecords(records)) {
            throw new IllegalArgumentException("CAPABILITY_SOURCE_REQUIRED");
        }
        source.put("entity_type", normalizedType)
                .put("capability_id", id)
                .put("lifecycle_status", lifecycle)
                .put("review_status", review)
                .put("certification_status", certification)
                .put("certification_scope", certificationScope)
                .put("trust_status", certified ? "time_bounded" : "not_evaluated")
                .put("trust_expires_at", certified ? certificationScope.optLong("expires_at", 0L) : 0L)
                .put("autonomy_level", "none")
                .put("execution_enabled", false);
        return record(id, source);
    }

    static JSONObject relation(String stableId, String sourceId, String sourceType,
                               String semantic, String targetId, String targetType,
                               JSONObject attributes) throws Exception {
        String id = clean(stableId);
        String from = clean(sourceId);
        String to = clean(targetId);
        String relation = clean(semantic).toLowerCase();
        String fromType = normalizeType(sourceType);
        String toType = normalizeType(targetType);
        if (id.isEmpty() || from.isEmpty() || to.isEmpty()) {
            throw new IllegalArgumentException("CAPABILITY_RELATION_IDENTITY_REQUIRED");
        }
        if (from.equals(to)) throw new IllegalArgumentException("CAPABILITY_SELF_RELATION_NOT_ALLOWED");
        if (!TYPES.contains(fromType) || !TYPES.contains(toType)) {
            throw new IllegalArgumentException("UNREGISTERED_CAPABILITY_TYPE");
        }
        if (!allowedRelation(fromType, relation, toType)) {
            throw new IllegalArgumentException("UNREGISTERED_CAPABILITY_RELATION");
        }
        JSONObject payload = attributes == null ? new JSONObject() : new JSONObject(attributes.toString());
        payload.put("source_id", from).put("source_type", fromType)
                .put("relation_semantic", relation)
                .put("target_id", to).put("target_type", toType)
                .put("execution_enabled", false);
        return record(id, payload);
    }

    static JSONObject batch(String authority, String partition, String revision,
                            JSONArray entities, JSONArray relations, long observedAt) throws Exception {
        JSONArray safeEntities = entities == null ? new JSONArray() : entities;
        JSONArray safeRelations = relations == null ? new JSONArray() : relations;
        String error = validationError(safeEntities, safeRelations);
        if (!error.isEmpty()) throw new IllegalArgumentException(error);
        return new JSONObject()
                .put("format", SharedGraphSyncKernel.BATCH_FORMAT)
                .put("version", SharedGraphSyncKernel.VERSION)
                .put("graph_scope", "work")
                .put("sync_owner", new JSONObject()
                        .put("provider", "amin_registry")
                        .put("authority", clean(authority)))
                .put("sync_partition", clean(partition))
                .put("contract_schema_version", SCHEMA_VERSION)
                .put("revision", clean(revision))
                .put("batch_status", "complete")
                .put("complete_snapshot", true)
                .put("completeness_proof", new JSONObject()
                        .put("source", clean(authority))
                        .put("record_count", safeEntities.length() + safeRelations.length()))
                .put("committed", true)
                .put("provenance", new JSONObject()
                        .put("adapter", "CapabilityInventoryAdapter")
                        .put("observed_at", observedAt)
                        .put("read_only", true))
                .put("entities", safeEntities)
                .put("relations", safeRelations);
    }

    static String validationError(JSONArray entities, JSONArray relations) {
        try {
            if (entities == null || relations == null) return "INVALID_CAPABILITY_RECORDS";
            for (int i = 0; i < entities.length(); i++) {
                JSONObject record = entities.optJSONObject(i);
                JSONObject payload = record == null ? null : record.optJSONObject("payload");
                if (record == null || payload == null) return "INVALID_CAPABILITY_ENTITY";
                String type = normalizeType(payload.optString("entity_type", ""));
                if (!TYPES.contains(type)) return "UNREGISTERED_CAPABILITY_TYPE";
                if (!record.optString("stable_id", "").equals(payload.optString("capability_id", ""))) {
                    return "CAPABILITY_IDENTITY_MISMATCH";
                }
                String lifecycle = payload.optString("lifecycle_status", "");
                String review = payload.optString("review_status", "");
                String certification = payload.optString("certification_status", "");
                if (!LIFECYCLES.contains(lifecycle)) return "INVALID_CAPABILITY_LIFECYCLE";
                if (!REVIEW_STATUSES.contains(review)) return "INVALID_REVIEW_STATUS";
                if (!CERTIFICATION_STATUSES.contains(certification)) return "INVALID_CERTIFICATION_STATUS";
                JSONArray sources = payload.optJSONArray("source_records");
                if (!validSourceRecords(sources)) return "CAPABILITY_SOURCE_REQUIRED";
                if ("certified".equals(lifecycle) || "certified".equals(certification)) {
                    JSONObject approval = payload.optJSONObject("human_approval");
                    JSONArray evidence = payload.optJSONArray("certification_evidence");
                    if (approval == null || !"approved".equals(approval.optString("review_status", ""))
                            || !validSourceRecords(evidence)) {
                        return "CERTIFICATION_REQUIRES_APPROVAL_AND_EVIDENCE";
                    }
                    if (!validCertificationScope(payload.optJSONObject("certification_scope"))) {
                        return "CERTIFICATION_SCOPE_REQUIRED";
                    }
                    if (!"time_bounded".equals(payload.optString("trust_status", ""))
                            || payload.optLong("trust_expires_at", 0L) != payload
                            .getJSONObject("certification_scope").optLong("expires_at", 0L)) {
                        return "INVALID_TRUST_VALIDITY_WINDOW";
                    }
                } else if (!"not_evaluated".equals(payload.optString("trust_status", ""))
                        || payload.optLong("trust_expires_at", -1L) != 0L) {
                    return "INVALID_TRUST_VALIDITY_WINDOW";
                }
                if (!"none".equals(payload.optString("autonomy_level", ""))
                        || payload.optBoolean("execution_enabled", true)) {
                    return "CAPABILITY_EXECUTION_NOT_ALLOWED";
                }
            }
            for (int i = 0; i < relations.length(); i++) {
                JSONObject record = relations.optJSONObject(i);
                JSONObject payload = record == null ? null : record.optJSONObject("payload");
                if (payload == null) return "INVALID_CAPABILITY_RELATION";
                if (!allowedRelation(normalizeType(payload.optString("source_type", "")),
                        payload.optString("relation_semantic", ""),
                        normalizeType(payload.optString("target_type", "")))) {
                    return "UNREGISTERED_CAPABILITY_RELATION";
                }
                if (payload.optBoolean("execution_enabled", true)) return "CAPABILITY_EXECUTION_NOT_ALLOWED";
            }
            return "";
        } catch (Exception error) {
            return "INVALID_CAPABILITY_RECORDS";
        }
    }

    static String contentFingerprint(JSONObject payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson(payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte value : digest) out.append(String.format("%02x", value & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JSONObject record(String stableId, JSONObject payload) throws Exception {
        return new JSONObject()
                .put("stable_id", stableId)
                .put("content_fingerprint", contentFingerprint(payload))
                .put("payload", payload);
    }

    private static boolean allowedRelation(String sourceType, String semantic, String targetType) {
        if (CAPABILITY.equals(sourceType) && CAPABILITY.equals(targetType)) {
            return CAPABILITY_RELATIONS.contains(semantic);
        }
        if (!CERTIFICATION_RELATIONS.contains(semantic)) return false;
        if (CAPABILITY.equals(sourceType) && CERTIFICATION.equals(targetType)) {
            return "certified_by".equals(semantic);
        }
        return CERTIFICATION.equals(sourceType) && CAPABILITY.equals(targetType)
                && "certifies".equals(semantic);
    }

    private static JSONObject emptyCertificationScope() throws Exception {
        return new JSONObject().put("scope_id", "").put("version", "")
                .put("issued_at", 0L).put("expires_at", 0L);
    }

    private static boolean validCertificationScope(JSONObject scope) {
        if (scope == null || clean(scope.optString("scope_id", "")).isEmpty()
                || clean(scope.optString("version", "")).isEmpty()) return false;
        long issuedAt = scope.optLong("issued_at", 0L);
        long expiresAt = scope.optLong("expires_at", 0L);
        return issuedAt > 0L && expiresAt > issuedAt;
    }

    private static boolean validSourceRecords(JSONArray records) {
        if (records == null || records.length() == 0) return false;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null || clean(record.optString("source_id", "")).isEmpty()
                    || clean(record.optString("authority", "")).isEmpty()) return false;
        }
        return true;
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

    private static String normalizeType(String value) { return clean(value).toUpperCase(); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
