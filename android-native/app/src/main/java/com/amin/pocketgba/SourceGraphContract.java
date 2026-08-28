package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Canonical contract for AMIN's source-code layer. This layer describes code structure only. */
final class SourceGraphContract {
    static final String FORMAT = "amin-source-graph";
    static final int VERSION = 1;

    static final String TYPE_REPOSITORY = "repository";
    static final String TYPE_BRANCH = "branch";
    static final String TYPE_DIRECTORY = "directory";
    static final String TYPE_FILE = "file";
    static final String TYPE_CLASS = "class";
    static final String TYPE_FUNCTION = "function";

    private static final Set<String> ENTITY_TYPES = new HashSet<>();
    private static final Set<String> RELATION_TYPES = new HashSet<>();

    static {
        ENTITY_TYPES.add(TYPE_REPOSITORY);
        ENTITY_TYPES.add(TYPE_BRANCH);
        ENTITY_TYPES.add(TYPE_DIRECTORY);
        ENTITY_TYPES.add(TYPE_FILE);
        ENTITY_TYPES.add(TYPE_CLASS);
        ENTITY_TYPES.add(TYPE_FUNCTION);
        RELATION_TYPES.add("contains");
        RELATION_TYPES.add("declares");
        RELATION_TYPES.add("imports");
        RELATION_TYPES.add("calls");
    }

    private SourceGraphContract() { }

    static JSONObject normalize(JSONObject input) {
        try {
            JSONObject source = input == null ? new JSONObject() : input;
            JSONObject out = new JSONObject(source.toString());
            out.put("format", FORMAT);
            out.put("version", VERSION);
            if (!out.has("authority")) out.put("authority", "repository-baseline");
            if (out.optJSONArray("entities") == null) out.put("entities", new JSONArray());
            if (out.optJSONArray("relations") == null) out.put("relations", new JSONArray());
            Validation validation = validate(out);
            out.put("valid", validation.valid);
            out.put("errors", validation.errors);
            return out;
        } catch (Exception error) {
            return empty("NORMALIZE_ERROR:" + error.getClass().getSimpleName());
        }
    }

    static Validation validate(JSONObject graph) {
        JSONArray errors = new JSONArray();
        if (graph == null) {
            errors.put("graph required");
            return new Validation(false, errors);
        }
        if (!FORMAT.equals(graph.optString("format", FORMAT))) errors.put("format invalid");
        if (graph.optInt("version", VERSION) != VERSION) errors.put("version invalid");

        JSONArray entities = graph.optJSONArray("entities");
        JSONArray relations = graph.optJSONArray("relations");
        if (entities == null) entities = new JSONArray();
        if (relations == null) relations = new JSONArray();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                errors.put("entity[" + i + "] invalid");
                continue;
            }
            String id = clean(entity.optString("id", ""));
            String type = clean(entity.optString("entityType", entity.optString("type", "")));
            if (id.isEmpty()) errors.put("entity[" + i + "] id required");
            else if (!ids.add(id)) errors.put("duplicate entity id:" + id);
            if (!ENTITY_TYPES.contains(type)) errors.put("entity type invalid:" + type);
        }
        for (int i = 0; i < relations.length(); i++) {
            JSONObject relation = relations.optJSONObject(i);
            if (relation == null) {
                errors.put("relation[" + i + "] invalid");
                continue;
            }
            String from = clean(relation.optString("from", ""));
            String to = clean(relation.optString("to", ""));
            String type = clean(relation.optString("type", ""));
            if (!RELATION_TYPES.contains(type)) errors.put("relation type invalid:" + type);
            if (!ids.contains(from)) errors.put("relation source missing:" + from);
            if (!ids.contains(to)) errors.put("relation target missing:" + to);
        }
        return new Validation(errors.length() == 0, errors);
    }

    static JSONObject empty(String error) {
        try {
            JSONArray errors = new JSONArray();
            if (!clean(error).isEmpty()) errors.put(error);
            return new JSONObject()
                    .put("format", FORMAT)
                    .put("version", VERSION)
                    .put("authority", "repository-baseline")
                    .put("entities", new JSONArray())
                    .put("relations", new JSONArray())
                    .put("valid", errors.length() == 0)
                    .put("errors", errors);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    static final class Validation {
        final boolean valid;
        final JSONArray errors;
        Validation(boolean valid, JSONArray errors) {
            this.valid = valid;
            this.errors = errors == null ? new JSONArray() : errors;
        }
    }
}
