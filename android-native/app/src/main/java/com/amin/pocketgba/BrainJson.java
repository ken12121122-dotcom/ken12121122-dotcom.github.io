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
}
