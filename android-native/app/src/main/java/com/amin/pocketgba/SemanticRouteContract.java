package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase 12 Semantic Router v0.1 — structured output contract.
 *
 * The model may only classify and select; it never dispatches, writes, approves,
 * or publishes anything. {@link #isUsable()} is the single gate every caller must
 * check before trusting a parsed result instead of the existing deterministic
 * (keyword) router — a low-confidence or execution-requesting result must fall
 * back to the deterministic path unchanged.
 */
final class SemanticRouteContract {
    static final double MIN_CONFIDENCE = 0.6;
    static final String INTENT_CAPABILITY_QUERY = "capability_query";
    static final String INTENT_NODE_CONTEXT = "node_context";
    static final String INTENT_OTHER = "other";

    final String intent;
    final List<String> selectedNodes;
    final List<String> selectedCapabilities;
    final List<String> requiredContext;
    final double confidence;
    final List<String> unresolvedGaps;
    final boolean requiresExecution;

    private SemanticRouteContract(String intent, List<String> selectedNodes, List<String> selectedCapabilities,
            List<String> requiredContext, double confidence, List<String> unresolvedGaps,
            boolean requiresExecution) {
        this.intent = intent;
        this.selectedNodes = selectedNodes;
        this.selectedCapabilities = selectedCapabilities;
        this.requiredContext = requiredContext;
        this.confidence = confidence;
        this.unresolvedGaps = unresolvedGaps;
        this.requiresExecution = requiresExecution;
    }

    /** Only a result that never asks for execution and clears the confidence bar may replace the fallback. */
    boolean isUsable() {
        return !requiresExecution && confidence >= MIN_CONFIDENCE;
    }

    boolean isCapabilityQuery() {
        return INTENT_CAPABILITY_QUERY.equals(intent);
    }

    /**
     * @throws IllegalArgumentException if {@code rawModelOutput} contains no parseable JSON object.
     *         Callers must treat this the same as a low-confidence result: fall back.
     */
    static SemanticRouteContract parse(String rawModelOutput) {
        JSONObject json;
        try {
            json = new JSONObject(extractJsonObject(rawModelOutput));
        } catch (org.json.JSONException error) {
            throw new IllegalArgumentException("malformed semantic router response", error);
        }
        boolean requiresExecution = json.optBoolean("requires_execution", true);
        double confidence = json.optDouble("confidence", 0.0);
        return new SemanticRouteContract(
                json.optString("intent", INTENT_OTHER),
                stringList(json.optJSONArray("selected_nodes")),
                stringList(json.optJSONArray("selected_capabilities")),
                stringList(json.optJSONArray("required_context")),
                confidence,
                stringList(json.optJSONArray("unresolved_gaps")),
                requiresExecution);
    }

    /** Model output occasionally wraps JSON in prose or a markdown code fence; extract the first {@code {...}} block. */
    private static String extractJsonObject(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty semantic router response");
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalArgumentException("no JSON object in semantic router response");
        return raw.substring(start, end + 1);
    }

    private static List<String> stringList(JSONArray array) {
        if (array == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) out.add(value);
        }
        return out;
    }

    /** System prompt enforcing the contract's output shape and its read-only, no-execution boundary. */
    static String systemPrompt() {
        return "你是語意路由器（Semantic Router）。只輸出一個 JSON 物件，不要有任何其他文字、"
                + "不要解釋、不要使用 markdown code fence。"
                + "JSON 格式：{\"intent\":\"" + INTENT_CAPABILITY_QUERY + " 或 " + INTENT_NODE_CONTEXT
                + " 或 " + INTENT_OTHER + "\",\"selected_nodes\":[],\"selected_capabilities\":[],"
                + "\"required_context\":[],\"confidence\":0到1之間的數字,\"unresolved_gaps\":[],"
                + "\"requires_execution\":false}。"
                + "requires_execution 永遠回傳 false——你只負責分類與選擇，不能執行、寫入、批准或發布任何東西，"
                + "那些邊界仍由既有程式碼把關。"
                + "如果不確定，confidence 給低分，並在 unresolved_gaps 說明原因，讓呼叫端改用既有的規則式判斷。";
    }
}
