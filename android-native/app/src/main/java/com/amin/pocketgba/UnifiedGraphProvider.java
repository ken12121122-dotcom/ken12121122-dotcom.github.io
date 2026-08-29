package com.amin.pocketgba;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Single visual read model for Amin Graph.
 *
 * The canvas owns layout. Scanner/indexers may only contribute canonical evidence-backed entities
 * and relations; they must never create another renderer or choose coordinates.
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
            JSONObject canonical = pending.optJSONObject("canonicalEvidence");
            if (canonical == null) return out.toString();
            if (!CanonicalEvidenceAdapter.FORMAT.equals(canonical.optString("format", ""))) return out.toString();

            JSONArray nodes = out.getJSONArray("nodes");
            JSONArray relations = out.getJSONArray("relations");

            JSONArray entities = canonical.optJSONArray("entities");
            if (entities != null) {
                for (int i = 0; i < entities.length(); i++) {
                    JSONObject entity = entities.optJSONObject(i);
                    if (entity == null) continue;
                    String id = clean(entity.optString("entityId", entity.optString("id", "")));
                    if (id.isEmpty()) continue;
                    String verification = clean(entity.optString("verification", ""));
                    JSONObject evidence = entity.optJSONObject("evidence");
                    nodes.put(new JSONObject()
                            .put("id", id)
                            .put("title", entity.optString("title", id))
                            .put("summary", summary(entity, evidence))
                            .put("detail", evidence == null ? "" : evidence.toString())
                            .put("entityType", "node")
                            .put("nodeType", entity.optString("kind", "evidence"))
                            .put("parentId", entity.optString("parentId", ""))
                            .put("status", "gap".equals(verification) ? "blocked" : "active")
                            .put("verification", verification)
                            .put("sourceKey", entity.optString("sourceKey", ""))
                            .put("evidenceRevision", entity.optString("evidenceRevision", ""))
                            .put("evidence", evidence == null ? JSONObject.NULL : new JSONObject(evidence.toString())));
                }
            }

            JSONArray canonicalRelations = canonical.optJSONArray("relations");
            if (canonicalRelations != null) {
                for (int i = 0; i < canonicalRelations.length(); i++) {
                    JSONObject relation = canonicalRelations.optJSONObject(i);
                    if (relation == null) continue;
                    String from = clean(relation.optString("from", ""));
                    String to = clean(relation.optString("to", ""));
                    if (from.isEmpty() || to.isEmpty()) continue;
                    String verification = clean(relation.optString("verification", ""));
                    JSONObject evidence = relation.optJSONObject("evidence");
                    relations.put(new JSONObject()
                            .put("id", relation.optString("relationId", relation.optString("id", "relation:" + i)))
                            .put("from", from)
                            .put("to", to)
                            .put("type", relation.optString("type", "related_to"))
                            .put("status", "gap".equals(verification) ? "blocked" : "active")
                            .put("verification", verification)
                            .put("evidenceRevision", relation.optString("evidenceRevision", ""))
                            .put("gate", new JSONObject().put("enabled", !"gap".equals(verification)))
                            .put("commandChain", new JSONArray())
                            .put("evidence", evidence == null ? JSONObject.NULL : new JSONObject(evidence.toString())));
                }
            }

            out.put("scannerRevision", canonical.optString("revision", pending.optString("revision", "")));
            out.put("scannerStatus", canonical.optString("status", ""));
            out.put("canonicalEvidence", new JSONObject(canonical.toString()));
            return out.toString();
        } catch (Exception error) {
            return emptyGraph().toString();
        }
    }

    private static JSONObject emptyGraph() {
        try {
            return new JSONObject()
                    .put("format", "amin-unified-graph")
                    .put("version", 6)
                    .put("layoutAuthority", "amin-dynamic-canvas-only")
                    .put("domains", new JSONArray())
                    .put("groups", new JSONArray())
                    .put("nodes", new JSONArray())
                    .put("commands", new JSONArray())
                    .put("relations", new JSONArray())
                    .put("canonicalEvidence", CanonicalEvidenceAdapter.empty())
                    .put("runtimeEdges", GraphRuntimeEdgeTrace.snapshotJson())
                    .put("runtimeFlows", GraphRuntimeFlowTrace.snapshotJson());
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static String summary(JSONObject entity, JSONObject evidence) {
        String kind = clean(entity == null ? "" : entity.optString("kind", ""));
        String verification = clean(entity == null ? "" : entity.optString("verification", ""));
        if ("gap".equals(kind) || "gap".equals(verification)) return "Evidence gap · scanner stopped here";
        String path = evidence == null ? "" : clean(evidence.optString("path", ""));
        if (!path.isEmpty()) return verification + " · " + path;
        return verification.isEmpty() ? "Evidence-backed node" : verification;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
