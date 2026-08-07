package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

final class MarkdownPreviewGenerator {
    private MarkdownPreviewGenerator() {}

    static String generate(JSONObject node, JSONArray edges) {
        if (node == null) return "# Unknown Node\n";
        StringBuilder md = new StringBuilder();
        md.append("# ").append(value(node, "name", "title", "Node")).append("\n\n");
        md.append("## 目的\n\n").append(node.optString("description", "待補說明")).append("\n\n");
        md.append("## 功能 ID\n\n`").append(value(node, "capability_id", "capabilityId", "")).append("`\n\n");
        md.append("## 節點\n\n- Node ID: `").append(value(node, "node_id", "nodeId", "")).append("`\n");
        md.append("- Type: `").append(node.optString("node_type", "capability")).append("`\n");
        md.append("- Status: `").append(node.optString("status", "active")).append("`\n");
        md.append("- Version: `").append(node.optString("version", "1")).append("`\n\n");
        md.append("## 輸入\n\n").append(blank(node.optString("input_contract", ""))).append("\n\n");
        md.append("## 輸出\n\n").append(blank(node.optString("output_contract", ""))).append("\n\n");
        JSONObject storage = node.optJSONObject("storage");
        md.append("## 資料來源\n\n");
        if (storage == null || storage.optString("adapter", "").isEmpty()) md.append("未設定\n\n");
        else {
            md.append("- Adapter: `").append(storage.optString("adapter")).append("`\n");
            md.append("- Source: `").append(storage.optString("source_id")).append("`\n");
            md.append("- Table: `").append(storage.optString("table")).append("`\n\n");
        }
        md.append("## 關聯\n\n");
        String nodeId = value(node, "node_id", "nodeId", "");
        int relationCount = 0;
        if (edges != null) for (int i = 0; i < edges.length(); i++) {
            JSONObject e = edges.optJSONObject(i); if (e == null) continue;
            String source = value(e, "source_node_id", "source", "");
            String target = value(e, "target_node_id", "target", "");
            if (!nodeId.equals(source) && !nodeId.equals(target)) continue;
            String type = value(e, "relationship_type", "relationshipType", "relation");
            md.append("- ").append(nodeId.equals(source) ? "outgoing" : "incoming").append(" `").append(type).append("`: `")
                    .append(nodeId.equals(source) ? target : source).append("`\n");
            relationCount++;
        }
        if (relationCount == 0) md.append("- 無\n");
        md.append("\n## 操作方式\n\n");
        JSONArray actions = node.optJSONArray("actions");
        if (actions != null) for (int i = 0; i < actions.length(); i++) md.append("- ").append(actions.optString(i)).append("\n");
        JSONObject voice = node.optJSONObject("voice");
        if (voice != null && voice.optBoolean("enabled", false)) {
            md.append("- Voice");
            JSONArray aliases = voice.optJSONArray("aliases");
            if (aliases != null && aliases.length() > 0) md.append(": ").append(aliases.toString());
            md.append("\n");
        }
        return md.toString();
    }

    private static String value(JSONObject o, String a, String b, String fallback) {
        String v = o.optString(a, ""); if (v.isEmpty()) v = o.optString(b, ""); return v.isEmpty() ? fallback : v;
    }
    private static String blank(String v) { return v == null || v.trim().isEmpty() ? "未設定" : "`" + v.trim() + "`"; }
}
