package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class ContractRegistry {
    private static final String PREFS = "amin_data_contracts";
    private final SharedPreferences prefs;

    ContractRegistry(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    JSONObject get(String id) {
        String key = id == null ? "" : id.trim();
        if (key.isEmpty()) return null;
        try {
            String saved = prefs.getString(key, null);
            if (saved != null) return new JSONObject(saved);
        } catch (Exception ignored) {}
        if ("transaction_v1".equals(key)) return transactionV1();
        return null;
    }

    boolean put(JSONObject contract) {
        if (contract == null) return false;
        DataContract parsed = new DataContract(contract);
        DataContract.Validation validation = parsed.validateSchema();
        if (!validation.valid || parsed.id().isEmpty()) return false;
        prefs.edit().putString(parsed.id(), contract.toString()).apply();
        return true;
    }

    static JSONObject transactionV1() {
        try {
            JSONArray fields = new JSONArray();
            fields.put(field("date", "日期", "date", true, null));
            fields.put(field("type", "收支類型", "select", true,
                    new JSONArray().put("expense").put("income").put("transfer")));
            fields.put(field("category", "分類", "select", true,
                    new JSONArray().put("food").put("transport").put("subscription").put("entertainment").put("salary").put("transfer")));
            fields.put(field("item", "品項", "text", true, null));
            fields.put(field("amount", "金額", "number", true, null));
            fields.put(field("account", "帳戶", "text", false, null));
            fields.put(field("merchant", "商家／來源", "text", false, null));
            fields.put(field("note", "備註", "text", false, null));
            return new JSONObject()
                    .put("contract_id", "transaction_v1")
                    .put("version", 2)
                    .put("storage_headers", new JSONArray()
                            .put("transaction_id").put("date").put("type").put("category").put("item")
                            .put("amount").put("account").put("merchant").put("note").put("created_at"))
                    .put("fields", fields);
        } catch (Exception e) { return new JSONObject(); }
    }

    private static JSONObject field(String key, String label, String type, boolean required, JSONArray options) throws Exception {
        JSONObject f = new JSONObject().put("key", key).put("label", label).put("type", type).put("required", required);
        if (options != null) f.put("options", options);
        return f;
    }
}
