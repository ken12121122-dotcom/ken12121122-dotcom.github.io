package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

interface StorageAdapter {
    final class Result {
        final boolean success;
        final String message;
        final JSONObject payload;
        Result(boolean success, String message, JSONObject payload) {
            this.success = success; this.message = message; this.payload = payload == null ? new JSONObject() : payload;
        }
    }

    Result append(DataContract contract, JSONObject record) throws Exception;
    Result list(DataContract contract, JSONObject filter) throws Exception;
    Result get(DataContract contract, String recordId) throws Exception;
    JSONArray headers() throws Exception;
}
