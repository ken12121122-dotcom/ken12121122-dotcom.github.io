package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Projects Capability Graph state into the existing Unified Graph read model. Never creates layout. */
final class CapabilityInventoryProjector {
    private CapabilityInventoryProjector() { }

    static void append(JSONObject unified, JSONObject state) throws Exception {
        if (unified == null || state == null
                || !SharedGraphSyncKernel.STATE_FORMAT.equals(state.optString("format", ""))) return;
        JSONArray nodes = unified.getJSONArray("nodes");
        JSONArray commands = unified.getJSONArray("commands");
        JSONArray relations = unified.getJSONArray("relations");
        Set<String> nodeIds = ids(nodes);
        Set<String> relationIds = ids(relations);

        JSONArray entities = state.optJSONArray("entities");
        if (entities != null) {
            for (int i = 0; i < entities.length(); i++) {
                JSONObject record = entities.optJSONObject(i);
                JSONObject payload = record == null ? null : record.optJSONObject("payload");
                if (payload == null) continue;
                String id = record.optString("stable_id", "");
                if (id.isEmpty() || !nodeIds.add(id)) continue;
                String type = payload.optString("entity_type", CapabilityGraphContract.CAPABILITY);
                String lifecycle = payload.optString("lifecycle_status", "approved");
                String certification = payload.optString("certification_status", "not_certified");
                JSONObject certificationScope = payload.optJSONObject("certification_scope");
                if (certificationScope == null) certificationScope = new JSONObject();
                nodes.put(new JSONObject()
                        .put("id", id)
                        .put("title", payload.optString("title", id))
                        .put("summary", type + " · " + lifecycle + " · " + certification)
                        .put("detail", new JSONObject()
                                .put("lifecycle", lifecycle)
                                .put("review_status", payload.optString("review_status", ""))
                                .put("certification", certification)
                                .put("certification_scope", certificationScope)
                                .put("trust_status", payload.optString("trust_status", "not_evaluated"))
                                .put("trust_expires_at", payload.optLong("trust_expires_at", 0L))
                                .put("autonomy", payload.optString("autonomy_level", "none"))
                                .put("execution_enabled", payload.optBoolean("execution_enabled", false))
                                .put("source_records", payload.optJSONArray("source_records") == null
                                        ? new JSONArray() : payload.optJSONArray("source_records")).toString())
                        .put("entityType", "capability")
                        .put("nodeType", type.toLowerCase())
                        .put("parentId", "")
                        .put("status", visualStatus(lifecycle, record.optString("sync_status", "active")))
                        .put("capabilityType", type)
                        .put("lifecycleStatus", lifecycle)
                        .put("certificationStatus", certification)
                        .put("certificationScope", new JSONObject(certificationScope.toString()))
                        .put("trustStatus", payload.optString("trust_status", "not_evaluated"))
                        .put("trustExpiresAt", payload.optLong("trust_expires_at", 0L))
                        .put("reviewStatus", payload.optString("review_status", ""))
                        .put("autonomyLevel", payload.optString("autonomy_level", "none"))
                        .put("executionEnabled", payload.optBoolean("execution_enabled", false))
                        .put("firstSeen", record.optLong("firstSeen", 0L))
                        .put("lastSeen", record.optLong("lastSeen", 0L)));
                if (CapabilityGraphContract.COMMAND.equals(type)) {
                    commands.put(new JSONObject()
                            .put("id", id)
                            .put("title", payload.optString("title", id))
                            .put("description", payload.optString("description", ""))
                            .put("action", payload.optString("action", ""))
                            .put("phrases", payload.optJSONArray("phrases") == null
                                    ? new JSONArray() : payload.optJSONArray("phrases"))
                            .put("status", "active"));
                }
            }
        }

        JSONArray sourceRelations = state.optJSONArray("relations");
        if (sourceRelations != null) {
            for (int i = 0; i < sourceRelations.length(); i++) {
                JSONObject record = sourceRelations.optJSONObject(i);
                JSONObject payload = record == null ? null : record.optJSONObject("payload");
                if (payload == null) continue;
                String id = record.optString("stable_id", "");
                if (id.isEmpty() || !relationIds.add(id)) continue;
                String syncStatus = record.optString("sync_status", "active");
                relations.put(new JSONObject()
                        .put("id", id)
                        .put("from", payload.optString("source_id", ""))
                        .put("to", payload.optString("target_id", ""))
                        .put("type", payload.optString("relation_semantic", "depends_on"))
                        .put("status", "stale".equals(syncStatus) ? "pending" : "active")
                        .put("syncStatus", syncStatus)
                        .put("gate", new JSONObject().put("enabled", false))
                        .put("commandChain", new JSONArray())
                        .put("readOnly", true));
            }
        }
        unified.put("capabilityInventory", new JSONObject(state.toString()));
    }

    private static Set<String> ids(JSONArray records) {
        Set<String> out = new HashSet<>();
        if (records == null) return out;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && !record.optString("id", "").isEmpty()) out.add(record.optString("id", ""));
        }
        return out;
    }

    private static String visualStatus(String lifecycle, String syncStatus) {
        if ("revoked".equals(lifecycle) || "suspended".equals(lifecycle)) return "blocked";
        if ("training".equals(lifecycle) || "proposed".equals(lifecycle)
                || "stale".equals(syncStatus)) return "pending";
        return "active";
    }
}
