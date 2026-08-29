package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Central registry for the first approved Step 9 bridge relation directions. */
final class Step9BridgeGraphContract {
    private static final Set<String> REGISTERED = new HashSet<>(Arrays.asList(
            key("TASK", "uses_knowledge", "KNOWLEDGE"),
            key("RUN", "learned_from", "KNOWLEDGE"),
            key("RUN", "generated", "MEMORY_CANDIDATE"),
            key("MEMORY_CANDIDATE", "promoted_to", "KNOWLEDGE"),
            key("PR", "provides_evidence_for", "KNOWLEDGE"),
            key("GOAL", "requires_capability", "CAPABILITY")
    ));

    private Step9BridgeGraphContract() { }

    static boolean isRegistered(String sourceType, String semantic, String targetType) {
        return REGISTERED.contains(key(sourceType, semantic, targetType));
    }

    /** Returns an empty string only when every bridge relation uses a registered direction. */
    static String validationError(JSONArray relations) {
        if (relations == null) return "INVALID_BRIDGE_RELATIONS";
        for (int i = 0; i < relations.length(); i++) {
            JSONObject relation = relations.optJSONObject(i);
            JSONObject payload = relation == null ? null : relation.optJSONObject("payload");
            if (payload == null) return "INVALID_BRIDGE_RELATION";
            if (!isRegistered(
                    payload.optString("source_type", ""),
                    payload.optString("relation_semantic", ""),
                    payload.optString("target_type", ""))) {
                return "UNREGISTERED_BRIDGE_RELATION";
            }
        }
        return "";
    }

    private static String key(String sourceType, String semantic, String targetType) {
        return clean(sourceType).toUpperCase() + "|" + clean(semantic).toLowerCase()
                + "|" + clean(targetType).toUpperCase();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
