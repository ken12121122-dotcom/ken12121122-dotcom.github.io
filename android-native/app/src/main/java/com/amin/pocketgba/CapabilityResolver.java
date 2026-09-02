package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Deterministic P0 resolver. It inspects inventory and never dispatches a capability. */
final class CapabilityResolver {
    private CapabilityResolver() { }

    static boolean isCapabilityQuestion(String raw) {
        String query = VoiceCommandParser.normalize(raw);
        if (query.isEmpty()) return false;
        return query.contains("有哪些能力") || query.contains("有什麼能力")
                || query.contains("能力清單") || query.contains("能力盤點")
                || query.contains("你會什麼") || query.contains("你能做什麼")
                || query.contains("你可以做什麼") || query.equals("有什麼功能")
                || query.contains("你有什麼功能")
                || query.contains("capability") || query.startsWith("查詢能力")
                || query.startsWith("尋找能力")
                || (query.contains("有沒有") && query.contains("能力"));
    }

    static JSONObject resolve(String rawQuery, JSONObject context) {
        try {
            JSONObject safe = context == null ? new JSONObject() : context;
            JSONArray capabilities = safe.optJSONArray("capabilities");
            if (capabilities == null) capabilities = new JSONArray();
            JSONArray matches = findMatches(rawQuery, capabilities);
            boolean listRequest = isListRequest(rawQuery);
            boolean found = listRequest || matches.length() > 0;
            String status = found ? "resolved_existing_capability" : "not_found";
            String answer = listRequest
                    ? inventoryAnswer(safe, capabilities)
                    : matchAnswer(rawQuery, matches);
            return new JSONObject()
                    .put("format", ConversationCapabilityContract.RESOLUTION_FORMAT)
                    .put("version", ConversationCapabilityContract.VERSION)
                    .put("query", rawQuery == null ? "" : rawQuery.trim())
                    .put("intent", listRequest ? "list_capabilities" : "find_capability")
                    .put("resolution_status", status)
                    .put("resolution_stage", found ? "existing_capability" : "repository_implementation")
                    .put("boundary", ConversationCapabilityContract.readOnlyBoundary())
                    .put("matches", listRequest ? capabilities : matches)
                    .put("evidence", safe.optJSONArray("source_records") == null
                            ? new JSONArray() : safe.optJSONArray("source_records"))
                    .put("unresolved_gaps", safe.optJSONArray("unresolved_gaps") == null
                            ? new JSONArray() : safe.optJSONArray("unresolved_gaps"))
                    .put("owner_attention_required", false)
                    .put("answer", answer)
                    .put("spoken_answer", listRequest ? inventorySpokenAnswer(safe) : answer);
        } catch (Exception error) {
            try {
                return new JSONObject()
                        .put("format", ConversationCapabilityContract.RESOLUTION_FORMAT)
                        .put("version", ConversationCapabilityContract.VERSION)
                        .put("resolution_status", "failed")
                        .put("resolution_stage", "existing_capability")
                        .put("boundary", ConversationCapabilityContract.readOnlyBoundary())
                        .put("matches", new JSONArray())
                        .put("evidence", new JSONArray())
                        .put("unresolved_gaps", new JSONArray()
                                .put("capability_resolution_failed:" + error.getClass().getSimpleName()))
                        .put("owner_attention_required", false)
                        .put("answer", "能力盤點暫時無法讀取，沒有執行任何動作。")
                        .put("spoken_answer", "能力盤點暫時無法讀取，沒有執行任何動作。");
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }

    private static boolean isListRequest(String raw) {
        String query = VoiceCommandParser.normalize(raw);
        return query.contains("有哪些") || query.contains("有什麼")
                || query.contains("清單") || query.contains("盤點")
                || query.contains("你會什麼") || query.contains("你能做什麼")
                || query.contains("你可以做什麼");
    }

    private static JSONArray findMatches(String rawQuery, JSONArray capabilities) throws Exception {
        JSONArray out = new JSONArray();
        String query = searchTerm(rawQuery);
        if (query.isEmpty()) return out;
        for (int i = 0; i < capabilities.length(); i++) {
            JSONObject record = capabilities.optJSONObject(i);
            JSONObject payload = record == null ? null : record.optJSONObject("payload");
            if (payload == null) continue;
            String haystack = VoiceCommandParser.normalize(record.optString("stable_id", "")
                    + payload.optString("title", "") + payload.optString("description", "")
                    + payload.optString("command_id", "") + payload.optString("action", "")
                    + payload.optJSONObject("voice"));
            String title = VoiceCommandParser.normalize(payload.optString("title", ""));
            if (haystack.contains(query) || !title.isEmpty() && query.contains(title)) {
                out.put(new JSONObject(record.toString()));
            }
        }
        return out;
    }

    private static String searchTerm(String raw) {
        String value = VoiceCommandParser.normalize(raw);
        String[] noise = {"請幫我", "幫我", "查詢能力", "尋找能力", "有沒有", "這個", "能力", "功能", "目前"};
        for (String item : noise) value = value.replace(item, "");
        return value;
    }

    private static String inventoryAnswer(JSONObject context, JSONArray capabilities) {
        JSONObject summary = context.optJSONObject("summary");
        int total = summary == null ? capabilities.length() : summary.optInt("total", capabilities.length());
        int direct = summary == null ? 0 : summary.optInt("chat_addressable", 0);
        int bridge = summary == null ? 0 : summary.optInt("bridge_required", 0);
        List<String> nodes = new ArrayList<>(), commands = new ArrayList<>(), missing = new ArrayList<>();
        for (int i = 0; i < capabilities.length(); i++) {
            JSONObject record = capabilities.optJSONObject(i);
            JSONObject payload = record == null ? null : record.optJSONObject("payload");
            if (payload == null) continue;
            String title = payload.optString("title", record.optString("stable_id", "?"));
            if (CapabilityGraphContract.COMMAND.equals(payload.optString("entity_type", ""))) commands.add(title);
            else if (ReadOnlyCapabilityContextBuilder.isChatAddressable(payload)) nodes.add(title);
            else missing.add(title);
        }
        return "目前共有 " + total + " 項能力：" + direct + " 項可由聊天辨識或路由，"
                + bridge + " 項缺少聊天 Bridge。\n"
                + "可供狐狸查詢或既有語音路由的節點：" + join(nodes) + "。\n"
                + "固定指令（" + commands.size() + "）：" + join(commands) + "。\n"
                + "缺 Bridge：" + join(missing) + "。\n"
                + "這是唯讀盤點，沒有執行指令、修改 Graph 或寫入 GitHub。";
    }

    private static String matchAnswer(String rawQuery, JSONArray matches) {
        if (matches.length() == 0) {
            return "目前 Registry 找不到與「" + clean(rawQuery) + "」相符的能力。"
                    + "下一步應先檢查 repo 既有實作，再依序評估 GitHub 可重用方案、小型內部實作與 API／MCP。"
                    + "本次沒有建立新能力，也沒有執行任何動作。";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < matches.length(); i++) {
            JSONObject record = matches.optJSONObject(i);
            JSONObject payload = record == null ? null : record.optJSONObject("payload");
            if (payload == null) continue;
            String entry = payload.optString("activity", "");
            if (entry.isEmpty()) entry = payload.optString("route", "");
            if (entry.isEmpty()) entry = payload.optString("action", "read_only");
            lines.add(payload.optString("title", record.optString("stable_id", "?"))
                    + "［" + record.optString("stable_id", "?") + "］· 入口 " + entry
                    + " · " + (ReadOnlyCapabilityContextBuilder.isChatAddressable(payload)
                    ? "可由聊天辨識" : "缺聊天 Bridge"));
        }
        return "找到 " + lines.size() + " 項相符能力：\n" + joinLines(lines)
                + "\n本次只回報 Registry 狀態，沒有執行能力。";
    }

    private static String inventorySpokenAnswer(JSONObject context) {
        JSONObject summary = context.optJSONObject("summary");
        int total = summary == null ? 0 : summary.optInt("total", 0);
        int direct = summary == null ? 0 : summary.optInt("chat_addressable", 0);
        int bridge = summary == null ? 0 : summary.optInt("bridge_required", 0);
        return "目前共有 " + total + " 項能力，其中 " + direct
                + " 項可由聊天查詢或辨識，" + bridge
                + " 項還缺少聊天橋接。完整清單已顯示在畫面上。";
    }

    private static String join(List<String> values) {
        if (values.isEmpty()) return "無";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append("、");
            out.append(value);
        }
        return out.toString();
    }

    private static String joinLines(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) out.append("- ").append(value).append('\n');
        return out.toString().trim();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
