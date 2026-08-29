package com.amin.pocketgba;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Single visual read model for Amin Graph.
 *
 * The canvas owns layout. Scanner/indexers may only contribute evidence-backed nodes and relations;
 * they must never create a second renderer or choose coordinates.
 */
final class UnifiedGraphProvider {
    static final String ACTION_CHANGED = "com.amin.pocketgba.UNIFIED_GRAPH_CHANGED";

    private UnifiedGraphProvider() { }

    static void notifyChanged(Context context) {
        if (context == null) return;
        Intent intent = new Intent(ACTION_CHANGED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    static String graphJson(Context context, NodeMetadataStore ignoredNodeStore) {
        try {
            JSONObject out = emptyGraph();
            if (context == null) return out.toString();

            JSONObject pending = SourceGraphProvider.pending(context);
            if (pending == null) return out.toString();

            JSONObject reverse = pending.optJSONObject("reverseDiscovery");
            if (reverse == null || !reverse.optBoolean("anchorVerified", false)) return out.toString();

            JSONArray nodes = out.getJSONArray("nodes");
            JSONArray relations = out.getJSONArray("relations");

            String anchorId = reverse.optString("anchorId", "").trim();
            String anchorLabel = reverse.optString("anchorLabel", anchorId).trim();
            if (!anchorId.isEmpty()) {
                nodes.put(node(
                        anchorId,
                        anchorLabel.isEmpty() ? anchorId : anchorLabel,
                        "scanner",
                        "",
                        "Evidence verified Scanner anchor",
                        reverse.optJSONObject("anchorEvidence"),
                        "static_verified"
                ));
            }

            JSONArray steps = reverse.optJSONArray("steps");
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.optJSONObject(i);
                    if (step == null) continue;
                    String from = step.optString("from", "").trim();
                    String to = step.optString("to", "").trim();
                    if (from.isEmpty() || to.isEmpty()) continue;
                    String toLabel = step.optString("toLabel", to).trim();
                    nodes.put(node(
                            to,
                            toLabel.isEmpty() ? to : toLabel,
                            "source",
                            from,
                            "Discovered from verified reverse evidence",
                            step.optJSONObject("evidence"),
                            step.optString("verification", "static_verified")
                    ));
                    relations.put(relation(
                            "scan:" + i + ":" + from + ">" + to,
                            from,
                            to,
                            step.optString("relation", "related_to"),
                            step.optJSONObject("evidence"),
                            step.optString("verification", "static_verified")
                    ));
                }
            }

            JSONObject gap = reverse.optJSONObject("gap");
            if (gap != null) {
                String after = gap.optString("after", "").trim();
                String code = gap.optString("code", "EVIDENCE_GAP").trim();
                String gapId = "gap:" + (code.isEmpty() ? "evidence" : code);
                nodes.put(node(
                        gapId,
                        gap.optString("expectedLayer", "GAP").toUpperCase() + " GAP",
                        "gap",
                        after,
                        gap.optString("reason", "Evidence missing"),
                        null,
                        "gap"
                ));
                if (!after.isEmpty()) {
                    relations.put(relation(
                            "scan:gap:" + after + ">" + gapId,
                            after,
                            gapId,
                            "evidence_gap",
                            null,
                            "gap"
                    ));
                }
            }

            out.put("scannerRevision", reverse.optString("revision", pending.optString("revision", "")));
            out.put("scannerStatus", reverse.optString("status", ""));
            out.put("sourceGraph", pending);
            return out.toString();
        } catch (Exception error) {
            return emptyGraph().toString();
        }
    }

    private static JSONObject emptyGraph() {
        try {
            return new JSONObject()
                    .put("format", "amin-unified-graph")
                    .put("version", 5)
                    .put("layoutAuthority", "amin-dynamic-canvas-only")
                    .put("domains", new JSONArray())
                    .put("groups", new JSONArray())
                    .put("nodes", new JSONArray())
                    .put("commands", new JSONArray())
                    .put("relations", new JSONArray())
                    .put("sourceGraph", new JSONObject()
                            .put("format", "amin-source-graph")
                            .put("version", 1)
                            .put("entities", new JSONArray())
                            .put("relations", new JSONArray()))
                    .put("capabilitySource", new JSONObject()
                            .put("format", "amin-capability-source-map")
                            .put("version", 1)
                            .put("mappings", new JSONArray())
                            .put("findings", new JSONArray()))
                    .put("architectureFindings", new JSONArray())
                    .put("runtimeEdges", GraphRuntimeEdgeTrace.snapshotJson())
                    .put("runtimeFlows", GraphRuntimeFlowTrace.snapshotJson());
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static JSONObject node(String id, String title, String nodeType, String parentId,
                                   String summary, JSONObject evidence, String verification) throws Exception {
        JSONObject out = new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("summary", summary == null ? "" : summary)
                .put("detail", evidence == null ? "" : evidence.toString())
                .put("entityType", "node")
                .put("nodeType", nodeType == null ? "" : nodeType)
                .put("parentId", parentId == null ? "" : parentId)
                .put("status", "gap".equals(verification) ? "blocked" : "active")
                .put("verification", verification == null ? "" : verification);
        if (evidence != null) out.put("evidence", new JSONObject(evidence.toString()));
        return out;
    }

    private static JSONObject relation(String id, String from, String to, String type,
                                       JSONObject evidence, String verification) throws Exception {
        JSONObject out = new JSONObject()
                .put("id", id)
                .put("from", from)
                .put("to", to)
                .put("type", type == null || type.trim().isEmpty() ? "related_to" : type.trim())
                .put("status", "gap".equals(verification) ? "blocked" : "active")
                .put("verification", verification == null ? "" : verification)
                .put("gate", new JSONObject().put("enabled", !"gap".equals(verification)))
                .put("commandChain", new JSONArray());
        if (evidence != null) out.put("evidence", new JSONObject(evidence.toString()));
        return out;
    }
}
