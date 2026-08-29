package com.amin.pocketgba;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Single visual read model for Amin Graph.
 *
 * The canvas owns layout. Scanner/indexers may only contribute synchronized evidence-backed
 * entities and relations; they must never create another renderer or choose coordinates.
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

            JSONObject persisted = new GraphEvidenceSyncStore(context).current();
            boolean growthPlayback = GraphGrowthPlaybackStore.isActive();
            JSONObject synced = GraphGrowthPlaybackStore.currentOr(persisted);
            if (!GraphSyncEngine.FORMAT.equals(synced.optString("format", ""))) return out.toString();
            JSONArray syncedEntities = synced.optJSONArray("entities");
            if (syncedEntities == null || syncedEntities.length() == 0) return out.toString();

            JSONArray nodes = out.getJSONArray("nodes");
            JSONArray relations = out.getJSONArray("relations");

            for (int i = 0; i < syncedEntities.length(); i++) {
                JSONObject entity = syncedEntities.optJSONObject(i);
                if (entity == null) continue;
                String id = clean(entity.optString("entityId", entity.optString("id", "")));
                if (id.isEmpty()) continue;
                String verification = clean(entity.optString("verification", ""));
                String syncStatus = clean(entity.optString("status", "active"));
                JSONObject evidence = entity.optJSONObject("evidence");
                nodes.put(new JSONObject()
                        .put("id", id)
                        .put("title", entity.optString("title", id))
                        .put("summary", summary(entity, evidence))
                        .put("detail", evidence == null ? "" : evidence.toString())
                        .put("entityType", "node")
                        .put("nodeType", entity.optString("kind", "evidence"))
                        .put("parentId", entity.optString("parentId", ""))
                        .put("status", visualStatus(verification, syncStatus))
                        .put("verification", verification)
                        .put("syncStatus", syncStatus)
                        .put("sourceKey", entity.optString("sourceKey", ""))
                        .put("sourceHash", entity.optString("sourceHash", ""))
                        .put("firstSeen", entity.optLong("firstSeen", 0L))
                        .put("lastSeen", entity.optLong("lastSeen", 0L))
                        .put("evidenceRevision", entity.optString("evidenceRevision", ""))
                        .put("evidence", evidence == null ? JSONObject.NULL : new JSONObject(evidence.toString())));
            }

            JSONArray syncedRelations = synced.optJSONArray("relations");
            if (syncedRelations != null) {
                for (int i = 0; i < syncedRelations.length(); i++) {
                    JSONObject relation = syncedRelations.optJSONObject(i);
                    if (relation == null) continue;
                    String from = clean(relation.optString("from", ""));
                    String to = clean(relation.optString("to", ""));
                    if (from.isEmpty() || to.isEmpty()) continue;
                    String verification = clean(relation.optString("verification", ""));
                    String syncStatus = clean(relation.optString("status", "active"));
                    JSONObject evidence = relation.optJSONObject("evidence");
                    boolean enabled = !"gap".equals(verification) && !"stale".equals(syncStatus);
                    relations.put(new JSONObject()
                            .put("id", relation.optString("relationId", relation.optString("id", "relation:" + i)))
                            .put("from", from)
                            .put("to", to)
                            .put("type", relation.optString("type", "related_to"))
                            .put("status", visualStatus(verification, syncStatus))
                            .put("verification", verification)
                            .put("syncStatus", syncStatus)
                            .put("sourceHash", relation.optString("sourceHash", ""))
                            .put("firstSeen", relation.optLong("firstSeen", 0L))
                            .put("lastSeen", relation.optLong("lastSeen", 0L))
                            .put("evidenceRevision", relation.optString("evidenceRevision", ""))
                            .put("gate", new JSONObject().put("enabled", enabled))
                            .put("commandChain", new JSONArray())
                            .put("evidence", evidence == null ? JSONObject.NULL : new JSONObject(evidence.toString())));
                }
            }

            out.put("scannerRevision", synced.optString("revision", ""));
            out.put("scannerStatus", synced.optString("sourceStatus", ""));
            out.put("syncedEvidence", new JSONObject(synced.toString()));
            out.put("growthPlayback", growthPlayback);
            return out.toString();
        } catch (Exception error) {
            return emptyGraph().toString();
        }
    }

    private static JSONObject emptyGraph() {
        try {
            return new JSONObject()
                    .put("format", "amin-unified-graph")
                    .put("version", 8)
                    .put("layoutAuthority", "amin-dynamic-canvas-only")
                    .put("domains", new JSONArray())
                    .put("groups", new JSONArray())
                    .put("nodes", new JSONArray())
                    .put("commands", new JSONArray())
                    .put("relations", new JSONArray())
                    .put("syncedEvidence", GraphSyncEngine.empty())
                    .put("growthPlayback", false)
                    .put("runtimeEdges", GraphRuntimeEdgeTrace.snapshotJson())
                    .put("runtimeFlows", GraphRuntimeFlowTrace.snapshotJson());
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static String visualStatus(String verification, String syncStatus) {
        if ("gap".equals(verification)) return "blocked";
        if ("stale".equals(syncStatus)) return "pending";
        return "active";
    }

    private static String summary(JSONObject entity, JSONObject evidence) {
        String kind = clean(entity == null ? "" : entity.optString("kind", ""));
        String verification = clean(entity == null ? "" : entity.optString("verification", ""));
        String syncStatus = clean(entity == null ? "" : entity.optString("status", ""));
        if ("gap".equals(kind) || "gap".equals(verification)) return "Evidence gap · scanner stopped here";
        if ("stale".equals(syncStatus)) return "Evidence missing · stale / needs review";
        String path = evidence == null ? "" : clean(evidence.optString("path", ""));
        if (!path.isEmpty()) return verification + " · " + path;
        return verification.isEmpty() ? "Evidence-backed node" : verification;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
