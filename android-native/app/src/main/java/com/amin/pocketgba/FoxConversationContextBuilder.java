package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Selects relevant registered Nodes and composes only their managed Markdown for Fox chat. */
final class FoxConversationContextBuilder {
    private static final int MAX_SELECTED_NODES = 3;

    private static final class Match {
        final JSONObject node;
        final int score;
        Match(JSONObject node, int score) { this.node = node; this.score = score; }
    }

    private FoxConversationContextBuilder() { }

    static boolean shouldAnswerWithNodeContext(Context context, NodeMetadataStore store, String query) {
        return looksLikeQuestion(query) && !matches(context, store, query).isEmpty();
    }

    static JSONObject build(Context context, NodeMetadataStore store, String query) {
        JSONArray selected = new JSONArray();
        JSONArray sources = new JSONArray();
        JSONArray gaps = new JSONArray();
        StringBuilder content = new StringBuilder();
        appendContext(context, store, NodeMdContextBuilder.FOX_NODE_ID,
                "狐狸", selected, sources, gaps, content);
        List<Match> matches = matches(context, store, query);
        for (int i = 0; i < matches.size() && i < MAX_SELECTED_NODES; i++) {
            JSONObject node = matches.get(i).node;
            String id = node.optString("node_id", node.optString("nodeId", ""));
            if (NodeMdContextBuilder.FOX_NODE_ID.equals(id)) continue;
            appendContext(context, store, id,
                    node.optString("title", node.optString("name", id)),
                    selected, sources, gaps, content);
        }
        try {
            return new JSONObject().put("format", "amin-fox-conversation-context").put("version", 1)
                    .put("selected_nodes", selected).put("content", content.toString())
                    .put("source_records", sources).put("unresolved_gaps", gaps)
                    .put("read_only", true);
        } catch (Exception ignored) { return new JSONObject(); }
    }

    static String systemContext(JSONObject context) {
        if (context == null || context.optString("content", "").trim().isEmpty()) return "";
        return "你是狐狸。只依照下列已註冊 Node 的唯讀 Markdown 與對話內容回答。"
                + "若 MD 只有功能描述而沒有即時資料，必須明確說明目前無法回報實際數值或狀態。"
                + "不得聲稱已執行工具、讀取未提供的外部資料、修改 Node／MD 或寫入 GitHub。\n\n"
                + context.optString("content", "");
    }

    private static void appendContext(Context context, NodeMetadataStore store, String nodeId,
                                      String title, JSONArray selected, JSONArray sources,
                                      JSONArray gaps, StringBuilder content) {
        JSONObject built = NodeMdContextBuilder.build(context, store, nodeId);
        String markdown = built.optString("content", "").trim();
        if (markdown.isEmpty()) {
            gaps.put(nodeId + ":md_unavailable");
            return;
        }
        selected.put(nodeId);
        if (content.length() > 0) content.append("\n\n");
        content.append("## Selected Node: ").append(title).append(" [").append(nodeId).append("]\n")
                .append(markdown);
        copyInto(built.optJSONArray("source_records"), sources);
        copyInto(built.optJSONArray("unresolved_gaps"), gaps);
    }

    private static List<Match> matches(Context context, NodeMetadataStore store, String rawQuery) {
        List<Match> out = new ArrayList<>();
        String query = VoiceCommandParser.normalize(rawQuery);
        if (query.isEmpty()) return out;
        try {
            JSONArray nodes = new JSONObject(NodeRegistry.registryJson(context, store)).optJSONArray("nodes");
            if (nodes == null) return out;
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.optJSONObject(i);
                if (node == null || "reference".equalsIgnoreCase(node.optString("node_type", ""))
                        || node.optJSONObject("input_context") == null) continue;
                int score = score(node, query);
                if (score > 0) out.add(new Match(node, score));
            }
        } catch (Exception ignored) { }
        out.sort(Comparator.comparingInt((Match match) -> match.score).reversed()
                .thenComparing(match -> match.node.optString("node_id", "")));
        return out;
    }

    private static int score(JSONObject node, String query) {
        int best = 0;
        String title = VoiceCommandParser.normalize(node.optString("title", node.optString("name", "")));
        if (!title.isEmpty() && query.contains(title)) best = 1000 + title.length();
        JSONObject voice = node.optJSONObject("voice");
        JSONArray aliases = voice == null ? null : voice.optJSONArray("aliases");
        if (aliases != null) for (int i = 0; i < aliases.length(); i++) {
            String alias = VoiceCommandParser.normalize(aliases.optString(i, ""));
            if (alias.length() >= 2 && query.contains(alias)) best = Math.max(best, 800 + alias.length());
        }
        return best;
    }

    private static boolean looksLikeQuestion(String raw) {
        String value = VoiceCommandParser.normalize(raw);
        return value.contains("什麼") || value.contains("為什麼") || value.contains("如何")
                || value.contains("怎麼") || value.contains("哪些") || value.contains("說明")
                || value.contains("介紹") || value.contains("回報") || value.contains("狀況")
                || value.contains("進度") || value.contains("內容") || value.contains("用途")
                || value.endsWith("嗎") || value.contains("?") || value.contains("？");
    }

    private static void copyInto(JSONArray source, JSONArray target) {
        if (source == null || target == null) return;
        for (int i = 0; i < source.length(); i++) target.put(source.opt(i));
    }
}
