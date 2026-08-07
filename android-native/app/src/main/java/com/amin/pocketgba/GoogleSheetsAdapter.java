package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Google Sheets adapter with header-based mapping; credentials/network are injected through Transport. */
final class GoogleSheetsAdapter implements StorageAdapter {
    interface Transport {
        JSONArray readHeaders(String sourceId, String table) throws Exception;
        void appendRow(String sourceId, String table, JSONArray orderedValues) throws Exception;
        JSONArray readRows(String sourceId, String table) throws Exception;
    }

    private final String sourceId;
    private final String table;
    private final Transport transport;

    GoogleSheetsAdapter(String sourceId, String table, Transport transport) {
        this.sourceId = clean(sourceId); this.table = clean(table); this.transport = transport;
    }

    @Override public JSONArray headers() throws Exception {
        requireConfigured();
        JSONArray h = transport.readHeaders(sourceId, table);
        return h == null ? new JSONArray() : h;
    }

    @Override public Result append(DataContract contract, JSONObject record) throws Exception {
        requireConfigured();
        DataContract.Validation validation = contract.validateRecord(record);
        if (!validation.valid) return new Result(false, validation.errors.toString(), new JSONObject());
        JSONArray headers = headers();
        JSONArray row = mapRecordToHeaders(headers, record);
        transport.appendRow(sourceId, table, row);
        return new Result(true, "appended", new JSONObject().put("table", table).put("values", row));
    }

    @Override public Result list(DataContract contract, JSONObject filter) throws Exception {
        requireConfigured();
        JSONArray headers = headers();
        JSONArray rows = transport.readRows(sourceId, table);
        JSONArray records = rowsToRecords(headers, rows == null ? new JSONArray() : rows, filter);
        return new Result(true, "loaded", new JSONObject().put("records", records));
    }

    @Override public Result get(DataContract contract, String recordId) throws Exception {
        JSONObject filter = new JSONObject().put("transaction_id", recordId);
        Result list = list(contract, filter);
        JSONArray records = list.payload.optJSONArray("records");
        JSONObject found = records != null && records.length() > 0 ? records.optJSONObject(0) : null;
        return new Result(found != null, found == null ? "not_found" : "loaded", found);
    }

    static JSONArray mapRecordToHeaders(JSONArray headers, JSONObject record) {
        JSONArray values = new JSONArray();
        for (int i = 0; i < headers.length(); i++) {
            String key = headers.optString(i, "").trim();
            Object value = record == null ? null : record.opt(key);
            values.put(value == null ? JSONObject.NULL : value);
        }
        return values;
    }

    static JSONArray rowsToRecords(JSONArray headers, JSONArray rows, JSONObject filter) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i); if (row == null) continue;
            JSONObject record = new JSONObject();
            try {
                for (int j = 0; j < headers.length(); j++) record.put(headers.optString(j), j < row.length() ? row.opt(j) : JSONObject.NULL);
                if (matches(record, filter)) out.put(record);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static boolean matches(JSONObject record, JSONObject filter) {
        if (filter == null || filter.length() == 0) return true;
        JSONArray names = filter.names(); if (names == null) return true;
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            if (!String.valueOf(filter.opt(key)).equals(String.valueOf(record.opt(key)))) return false;
        }
        return true;
    }

    private void requireConfigured() {
        if (sourceId.isEmpty() || table.isEmpty() || transport == null) throw new IllegalStateException("google_sheets adapter is not configured");
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
