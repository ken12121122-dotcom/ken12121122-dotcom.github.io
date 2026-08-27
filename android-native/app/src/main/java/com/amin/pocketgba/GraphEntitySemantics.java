package com.amin.pocketgba;

import org.json.JSONObject;

/** Canonical graph entity semantics. Type is the source of truth for level and visual size. */
final class GraphEntitySemantics {
    static final String LEVEL_LARGE = "large";
    static final String LEVEL_MEDIUM = "medium";
    static final String LEVEL_SMALL = "small";

    private GraphEntitySemantics() { }

    static String canonicalType(JSONObject source, String fallback) {
        String raw = first(source, "canonical_type", "canonicalType", "entity_type", "entityType", "node_type", "nodeType", "type");
        if (raw.isEmpty()) raw = clean(fallback);
        String key = raw.toLowerCase().replace('-', '_').replace(' ', '_');
        if (key.equals("system") || key.equals("root") || key.equals("app_root")) return "SYSTEM";
        if (key.equals("agent") || key.equals("assistant")) return "AGENT";
        if (key.equals("stream") || key.equals("workflow")) return "STREAM";
        if (key.equals("skill")) return "SKILL";
        if (key.equals("group") || key.equals("domain")) return "GROUP";
        if (key.equals("command")) return "COMMAND";
        if (key.equals("tool")) return "TOOL";
        return "NODE";
    }

    static String levelForType(String canonicalType) {
        String type = clean(canonicalType).toUpperCase();
        if (type.equals("SYSTEM") || type.equals("AGENT")) return LEVEL_LARGE;
        if (type.equals("STREAM") || type.equals("SKILL") || type.equals("GROUP")) return LEVEL_MEDIUM;
        return LEVEL_SMALL;
    }

    static JSONObject apply(JSONObject entity, JSONObject source, String fallbackType) throws Exception {
        String type = canonicalType(source, fallbackType);
        String level = levelForType(type);
        entity.put("canonicalType", type);
        entity.put("level", level);
        entity.put("visualSize", level);
        return entity;
    }

    static String legacyParentForLevel(String existingParent, String level) {
        String parent = clean(existingParent);
        if (!parent.isEmpty()) return parent;
        // Current renderer treats a non-root parent as the small tier. This keeps legacy UI
        // compatible while canonical level becomes the authoritative data contract.
        if (LEVEL_SMALL.equals(level)) return "group:nodes";
        return "";
    }

    private static String first(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            String value = clean(object.optString(key, ""));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
