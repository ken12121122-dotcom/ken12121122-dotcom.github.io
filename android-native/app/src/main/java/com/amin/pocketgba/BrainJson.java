package com.amin.pocketgba;

import org.json.JSONObject;

final class BrainJson {
    private BrainJson() {}

    static JSONObject copy(JSONObject source) {
        if (source == null) return null;
        try {
            return new JSONObject(source.toString());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to copy Brain JSON", error);
        }
    }

    static JSONObject object(Object... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Brain JSON needs key/value pairs");
        }
        JSONObject value = new JSONObject();
        try {
            for (int index = 0; index < keyValues.length; index += 2) {
                if (!(keyValues[index] instanceof String)) {
                    throw new IllegalArgumentException("Brain JSON key must be text");
                }
                value.put((String) keyValues[index], keyValues[index + 1]);
            }
            return value;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to create Brain JSON", error);
        }
    }

    static String requiredString(JSONObject source, String key) {
        String value = source == null ? "" : source.optString(key, "");
        if (value.isEmpty()) throw new IllegalStateException("Brain JSON is missing " + key);
        return value;
    }

    static String pretty(JSONObject source) {
        try {
            return source.toString(2);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to format Brain JSON", error);
        }
    }
}
