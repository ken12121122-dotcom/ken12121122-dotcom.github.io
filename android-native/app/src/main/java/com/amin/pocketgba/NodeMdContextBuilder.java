package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Resolves a registered Node --reads_from--> managed Markdown context without mutating either. */
final class NodeMdContextBuilder {
    static final String FOX_NODE_ID = "app:fox-chat";
    private static final int MAX_CONTEXT_BYTES = 64 * 1024;

    private NodeMdContextBuilder() { }

    static JSONObject build(Context context, NodeMetadataStore nodeStore, String nodeId) {
        JSONArray sources = new JSONArray();
        JSONArray gaps = new JSONArray();
        try {
            JSONObject node = NodeRegistry.findNode(context, nodeStore, nodeId);
            if (node == null) return result(nodeId, "", sources, gaps.put("node_not_registered"));
            JSONObject input = node.optJSONObject("input_context");
            String contextNodeId = input == null ? "" : input.optString("context_node_id", "").trim();
            if (contextNodeId.isEmpty()) return result(nodeId, "", sources, gaps.put("context_node_not_declared"));
            if (!hasReadRelation(context, nodeStore, nodeId, contextNodeId)) {
                return result(nodeId, "", sources, gaps.put("reads_from_relation_missing"));
            }
            JSONObject contextNode = NodeRegistry.findNode(context, nodeStore, contextNodeId);
            JSONObject storage = contextNode == null ? null : contextNode.optJSONObject("storage");
            if (storage == null) {
                return result(nodeId, "", sources, gaps.put("managed_md_source_missing"));
            }
            String adapter = storage.optString("adapter", "");
            String sourceId = storage.optString("source_id", "").trim();
            String markdown;
            String authority;
            if ("asset_md".equals(adapter) && sourceId.startsWith("asset:")) {
                markdown = readStream(context.getAssets().open(sourceId.substring("asset:".length())));
                authority = "bundled_asset";
            } else if ("internal_md".equals(adapter) && sourceId.startsWith("file:")) {
                markdown = readInternal(context, sourceId.substring("file:".length()));
                authority = "app_private_storage";
            } else {
                return result(nodeId, "", sources, gaps.put("unsupported_md_source"));
            }
            sources.put(new JSONObject().put("source_id", sourceId)
                    .put("node_id", contextNodeId).put("media_type", "text/markdown")
                    .put("authority", authority).put("read_only", true));
            return result(nodeId, markdown, sources, gaps);
        } catch (Exception error) {
            return result(nodeId, "", sources,
                    gaps.put("context_build_failed:" + error.getClass().getSimpleName()));
        }
    }

    static String systemContext(JSONObject context) {
        if (context == null || context.optString("content", "").trim().isEmpty()) return "";
        return "你是狐狸，使用下列已註冊 Node 的唯讀 Markdown context 回答。"
                + "不得聲稱已執行工具、修改 Node、修改 MD 或寫入 GitHub；資料不足時明確回報缺口。\n\n"
                + context.optString("content", "");
    }

    private static boolean hasReadRelation(Context context, NodeMetadataStore store,
                                           String source, String target) throws Exception {
        JSONArray edges = new JSONObject(NodeRegistry.typedEdgesJson(context, store)).optJSONArray("edges");
        if (edges == null) return false;
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            if (edge == null) continue;
            String from = edge.optString("source_node_id", edge.optString("source", ""));
            String to = edge.optString("target_node_id", edge.optString("target", ""));
            String relation = edge.optString("relationship_type", edge.optString("relationshipType", ""));
            if (source.equals(from) && target.equals(to) && "reads_from".equals(relation)) return true;
        }
        return false;
    }

    private static String readInternal(Context context, String relativePath) throws Exception {
        File root = new File(context.getFilesDir(), "node-context").getCanonicalFile();
        File target = new File(context.getFilesDir(), relativePath).getCanonicalFile();
        if (!target.getPath().startsWith(root.getPath() + File.separator) || !target.isFile()) {
            throw new SecurityException("INVALID_INTERNAL_MD_PATH");
        }
        try (InputStream input = new FileInputStream(target)) { return readStream(input); }
    }

    private static String readStream(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            int total = 0;
            while ((read = source.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_CONTEXT_BYTES) throw new IllegalArgumentException("MD_CONTEXT_TOO_LARGE");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static JSONObject result(String nodeId, String content, JSONArray sources, JSONArray gaps) {
        try {
            return new JSONObject().put("format", "amin-node-md-context").put("version", 1)
                    .put("node_id", nodeId == null ? "" : nodeId)
                    .put("content", content == null ? "" : content)
                    .put("source_records", sources == null ? new JSONArray() : sources)
                    .put("unresolved_gaps", gaps == null ? new JSONArray() : gaps)
                    .put("read_only", true);
        } catch (Exception ignored) { return new JSONObject(); }
    }
}
