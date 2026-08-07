package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class DataContract {
    static final List<String> FIELD_TYPES = Arrays.asList("text", "number", "date", "select", "boolean");

    static final class Validation {
        final boolean valid;
        final JSONArray errors;
        Validation(boolean valid, JSONArray errors) { this.valid = valid; this.errors = errors; }
    }

    private final JSONObject source;
    DataContract(String json) throws Exception { this(new JSONObject(json)); }
    DataContract(JSONObject source) { this.source = source == null ? new JSONObject() : source; }

    String id() { return source.optString("contract_id", source.optString("contractId", "")); }
    JSONArray fields() { JSONArray f = source.optJSONArray("fields"); return f == null ? new JSONArray() : f; }
    JSONObject json() { try { return new JSONObject(source.toString()); } catch (Exception e) { return new JSONObject(); } }

    Validation validateSchema() {
        JSONArray errors = new JSONArray();
        if (id().trim().isEmpty()) errors.put("missing contract_id");
        JSONArray fields = fields();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < fields.length(); i++) {
            JSONObject f = fields.optJSONObject(i);
            if (f == null) { errors.put("field[" + i + "] must be object"); continue; }
            String key = f.optString("key", "").trim();
            String type = f.optString("type", "").trim();
            if (key.isEmpty()) errors.put("field[" + i + "] missing key");
            else if (keys.contains(key)) errors.put("duplicate field key: " + key);
            else keys.add(key);
            if (!FIELD_TYPES.contains(type)) errors.put("unsupported field type: " + type);
            if ("select".equals(type)) {
                JSONArray options = f.optJSONArray("options");
                if (options == null || options.length() == 0) errors.put("select field missing options: " + key);
            }
        }
        return new Validation(errors.length() == 0, errors);
    }

    Validation validateRecord(JSONObject record) {
        JSONArray errors = new JSONArray();
        Validation schema = validateSchema();
        for (int i = 0; i < schema.errors.length(); i++) errors.put(schema.errors.optString(i));
        if (record == null) { errors.put("record is null"); return new Validation(false, errors); }
        JSONArray fields = fields();
        for (int i = 0; i < fields.length(); i++) {
            JSONObject f = fields.optJSONObject(i);
            if (f == null) continue;
            String key = f.optString("key", "");
            String type = f.optString("type", "");
            boolean required = f.optBoolean("required", false);
            Object value = record.opt(key);
            boolean missing = value == null || value == JSONObject.NULL || (value instanceof String && ((String)value).trim().isEmpty());
            if (required && missing) { errors.put("required field missing: " + key); continue; }
            if (missing) continue;
            if ("number".equals(type) && !(value instanceof Number)) errors.put("field must be number: " + key);
            if ("boolean".equals(type) && !(value instanceof Boolean)) errors.put("field must be boolean: " + key);
            if (("text".equals(type) || "date".equals(type) || "select".equals(type)) && !(value instanceof String)) errors.put("field must be string: " + key);
            if ("select".equals(type)) {
                JSONArray options = f.optJSONArray("options");
                boolean found = false;
                if (options != null) for (int j = 0; j < options.length(); j++) if (String.valueOf(value).equals(options.optString(j))) found = true;
                if (!found) errors.put("invalid option for " + key + ": " + value);
            }
        }
        return new Validation(errors.length() == 0, errors);
    }

    JSONObject applyContext(JSONObject context, JSONObject record) {
        JSONObject out = new JSONObject();
        try {
            if (context != null) {
                JSONArray names = context.names();
                if (names != null) for (int i = 0; i < names.length(); i++) { String k = names.optString(i); out.put(k, context.opt(k)); }
            }
            if (record != null) {
                JSONArray names = record.names();
                if (names != null) for (int i = 0; i < names.length(); i++) { String k = names.optString(i); out.put(k, record.opt(k)); }
            }
        } catch (Exception ignored) {}
        return out;
    }
}
