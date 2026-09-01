package com.amin.pocketgba;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * The single visual read model for Amin Graph.
 *
 * Registries, synchronized evidence, and provider-backed Work Graph records are projected into
 * the existing canvas. Producers never own layout and never create a parallel graph renderer.
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

    static String graphJson(Context context, NodeMetadataStore nodeStore) {
        try {
            JSONObject out = emptyGraph();
            if (context == null) return out.toString();

            JSONArray nodes = out.getJSONArray("nodes");
            JSONArray commands = out.getJSONArray("commands");
            JSONArray relations = out.getJSONArray("relations");
            Set<String> nodeIds = new HashSet<>();
            Set<String> relationIds = new HashSet<>();

            JSONArray registryNodes = new JSONArray();
            JSONArray registryRelations = new JSONArray();
            addRegistry(context, nodeStore, nodes, commands, relations, registryNodes,
                    registryRelations, nodeIds, relationIds);
            JSONObject capabilityState = new CapabilityInventoryStore(context).refresh(nodeStore);
            CapabilityInventoryProjector.append(out, capabilityState);
            addEvidence(context, out, nodes, relations, nodeIds, relationIds);
            addGitHubWork(context, out, nodes, relations, nodeIds, relationIds);
            AgentStatusProjector.append(out, nodes, relations, nodeIds, relationIds);

            JSONObject sourceGraph = SourceGraphProvider.graph(context);
            out.put("sourceGraph", sourceGraph);
            JSONObject capabilitySource = CapabilitySourceProvider.evaluate(
                    context, registryNodes, sourceGraph, registryRelations);
            out.put("capabilitySource", capabilitySource);
            out.put("architectureFindings", capabilitySource.optJSONArray("findings") == null
                    ? new JSONArray() : capabilitySource.optJSONArray("findings"));
            return out.toString();
        } catch (Exception error) {
            return emptyGraph().toString();
        }
    }

    private static void addRegistry(Context context, NodeMetadataStore nodeStore,
                                    JSONArray nodes, JSONArray commands, JSONArray relations,
                                    JSONArray registryNodes, JSONArray registryRelations,
                                    Set<String> nodeIds, Set<String> relationIds) throws Exception {
        JSONObject root = new JSONObject(NodeRegistry.registryJson(context, nodeStore));
        JSONArray sourceNodes = root.optJSONArray("nodes");
        if (sourceNodes != null) {
            for (int i = 0; i < sourceNodes.length(); i++) {
                JSONObject source = sourceNodes.optJSONObject(i);
                if (source == null) continue;
                String id = first(source, "node_id", "nodeId", "capability_id", "capabilityId", "id");
                if (!nodeIds.add(id)) continue;
                String title = first(source, "name", "title");
                JSONObject entity = new JSONObject()
                        .put("id", id)
                        .put("title", title.isEmpty() ? id : title)
                        .put("summary", source.optString("description", ""))
                        .put("detail", nodeDetail(source))
                        .put("groupId", "group:nodes")
                        .put("domainId", "system:amin")
                        .put("entityType", "node")
                        .put("registryId", id)
                        .put("route", source.optString("route", ""))
                        .put("parentId", first(source, "parent_id", "parentNodeId", "parent"))
                        .put("nodeType", first(source, "node_type", "nodeType"))
                        .put("status", source.optString("status", "active"))
                        .put("version", first(source, "version", "nodeVersion"))
                        .put("direction", source.optString("direction", ""))
                        .put("slot", source.optInt("slot", 0))
                        .put("voice", source.optJSONObject("voice") == null
                                ? new JSONObject() : source.optJSONObject("voice"))
                        .put("storage", source.optJSONObject("storage") == null
                                ? new JSONObject() : source.optJSONObject("storage"));
                nodes.put(entity);
                registryNodes.put(new JSONObject(entity.toString()));
            }
        }

        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            String id = "command:" + command.getId();
            if (!nodeIds.add(id)) continue;
            JSONObject entity = new JSONObject()
                    .put("id", id)
                    .put("title", command.getTitle())
                    .put("summary", command.getDescription())
                    .put("detail", "action: " + command.getAction() + "\nphrases: " + join(command.getPhrases()))
                    .put("groupId", "group:commands")
                    .put("domainId", "system:amin")
                    .put("entityType", "command")
                    .put("registryId", id)
                    .put("action", command.getAction())
                    .put("category", command.getCategory())
                    .put("mode", command.getMode() == null ? "" : command.getMode())
                    .put("requiresAccessibility", command.requiresAccessibility())
                    .put("phrases", new JSONArray(command.getPhrases()));
            nodes.put(entity);
            commands.put(new JSONObject(entity.toString()));
            registryNodes.put(new JSONObject(entity.toString()));
        }

        JSONArray dynamic = new DynamicCommandStore(context).registered();
        for (int i = 0; i < dynamic.length(); i++) {
            JSONObject source = dynamic.optJSONObject(i);
            if (source == null) continue;
            String rawId = clean(source.optString("id", ""));
            if (rawId.isEmpty()) continue;
            String id = rawId.startsWith("command:") ? rawId : "command:" + rawId;
            if (!nodeIds.add(id)) continue;
            JSONObject entity = new JSONObject()
                    .put("id", id)
                    .put("title", source.optString("title", rawId))
                    .put("summary", source.optString("description", "Dynamic command"))
                    .put("detail", "action: " + source.optString("action", "")
                            + "\nphrases: " + jsonList(source.optJSONArray("phrases")))
                    .put("groupId", "group:commands")
                    .put("domainId", "system:amin")
                    .put("entityType", "command")
                    .put("registryId", id)
                    .put("action", source.optString("action", ""))
                    .put("category", source.optString("category", "dynamic"))
                    .put("mode", source.optString("mode", ""))
                    .put("requiresAccessibility", source.optBoolean("requires_accessibility", false))
                    .put("phrases", source.optJSONArray("phrases") == null
                            ? new JSONArray() : source.optJSONArray("phrases"));
            nodes.put(entity);
            commands.put(new JSONObject(entity.toString()));
            registryNodes.put(new JSONObject(entity.toString()));
        }

        JSONObject edgeRoot = new JSONObject(NodeRegistry.typedEdgesJson(context, nodeStore));
        JSONArray edges = edgeRoot.optJSONArray("edges");
        if (edges == null) edges = new JSONArray();
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            if (edge == null) continue;
            String from = first(edge, "from", "source", "source_node_id", "sourceNodeId");
            String to = first(edge, "to", "target", "target_node_id", "targetNodeId");
            if (from.isEmpty() || to.isEmpty()) continue;
            String type = first(edge, "relationship_type", "relationshipType", "relation", "type", "edge_type");
            if (type.isEmpty()) type = "related_to";
            String id = first(edge, "edge_id", "edgeId");
            if (id.isEmpty()) id = "edge:" + i;
            if (!relationIds.add(id)) continue;
            JSONObject relation = new JSONObject()
                    .put("id", id)
                    .put("from", from)
                    .put("to", to)
                    .put("type", type)
                    .put("status", edge.optString("status", "active"))
                    .put("commandChain", edge.has("command_chain")
                            ? edge.optJSONArray("command_chain")
                            : edge.has("commands") ? edge.optJSONArray("commands") : new JSONArray())
                    .put("gate", edge.has("gate") ? edge.optJSONObject("gate")
                            : new JSONObject().put("enabled", true))
                    .put("authority", edge.optString("authority", ""));
            relations.put(relation);
            registryRelations.put(new JSONObject(relation.toString()));
        }
    }

    private static void addEvidence(Context context, JSONObject out, JSONArray nodes,
                                    JSONArray relations, Set<String> nodeIds,
                                    Set<String> relationIds) throws Exception {
        JSONObject persisted = new GraphEvidenceSyncStore(context).current();
        boolean playback = GraphGrowthPlaybackStore.isActive();
        JSONObject synced = GraphGrowthPlaybackStore.currentOr(persisted);
        if (!GraphSyncEngine.FORMAT.equals(synced.optString("format", ""))) return;

        JSONArray entities = synced.optJSONArray("entities");
        if (entities != null) {
            for (int i = 0; i < entities.length(); i++) {
                JSONObject entity = entities.optJSONObject(i);
                if (entity == null) continue;
                String id = clean(entity.optString("entityId", entity.optString("id", "")));
                if (!nodeIds.add(id)) continue;
                String verification = clean(entity.optString("verification", ""));
                String syncStatus = clean(entity.optString("status", "active"));
                JSONObject evidence = entity.optJSONObject("evidence");
                nodes.put(new JSONObject()
                        .put("id", id)
                        .put("title", entity.optString("title", id))
                        .put("summary", evidenceSummary(entity, evidence))
                        .put("detail", evidence == null ? "" : evidence.toString())
                        .put("groupId", "group:evidence")
                        .put("domainId", "system:evidence")
                        .put("entityType", "node")
                        .put("nodeType", entity.optString("kind", "evidence"))
                        .put("parentId", entity.optString("parentId", ""))
                        .put("status", evidenceStatus(verification, syncStatus))
                        .put("verification", verification)
                        .put("syncStatus", syncStatus)
                        .put("sourceKey", entity.optString("sourceKey", ""))
                        .put("sourceHash", entity.optString("sourceHash", ""))
                        .put("firstSeen", entity.optLong("firstSeen", 0L))
                        .put("lastSeen", entity.optLong("lastSeen", 0L))
                        .put("evidenceRevision", entity.optString("evidenceRevision", ""))
                        .put("evidence", evidence == null ? JSONObject.NULL
                                : new JSONObject(evidence.toString())));
            }
        }

        JSONArray edges = synced.optJSONArray("relations");
        if (edges != null) {
            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.optJSONObject(i);
                if (edge == null) continue;
                String from = clean(edge.optString("from", ""));
                String to = clean(edge.optString("to", ""));
                String id = clean(edge.optString("relationId", edge.optString("id", "relation:evidence:" + i)));
                if (!nodeIds.contains(from) || !nodeIds.contains(to) || !relationIds.add(id)) continue;
                String verification = clean(edge.optString("verification", ""));
                String syncStatus = clean(edge.optString("status", "active"));
                JSONObject evidence = edge.optJSONObject("evidence");
                relations.put(new JSONObject()
                        .put("id", id)
                        .put("from", from)
                        .put("to", to)
                        .put("type", edge.optString("type", "related_to"))
                        .put("status", evidenceStatus(verification, syncStatus))
                        .put("verification", verification)
                        .put("syncStatus", syncStatus)
                        .put("gate", new JSONObject().put("enabled",
                                !"gap".equals(verification) && !"stale".equals(syncStatus)))
                        .put("commandChain", new JSONArray())
                        .put("evidence", evidence == null ? JSONObject.NULL
                                : new JSONObject(evidence.toString())));
            }
        }
        out.put("scannerRevision", synced.optString("revision", ""));
        out.put("scannerStatus", synced.optString("sourceStatus", ""));
        out.put("syncedEvidence", new JSONObject(synced.toString()));
        out.put("growthPlayback", playback);
    }

    private static void addGitHubWork(Context context, JSONObject out, JSONArray nodes,
                                      JSONArray relations, Set<String> nodeIds,
                                      Set<String> relationIds) throws Exception {
        JSONObject state = new GitHubWorkSyncStore(context).current();
        JSONArray entities = state.optJSONArray("entities");
        if (entities == null) entities = new JSONArray();
        int projectedEntities = 0;
        for (int i = 0; i < entities.length(); i++) {
            JSONObject record = entities.optJSONObject(i);
            JSONObject payload = record == null ? null : record.optJSONObject("payload");
            String id = record == null ? "" : clean(record.optString("stable_id", ""));
            if (payload == null || !GitHubWorkContract.PROVIDER.equals(payload.optString("provider", ""))
                    || !nodeIds.add(id)) continue;
            JSONObject attributes = payload.optJSONObject("attributes");
            if (attributes == null) attributes = new JSONObject();
            String workType = clean(payload.optString("work_type", ""));
            String lifecycle = clean(payload.optString("lifecycle_status", "observed"));
            String failedStep = clean(attributes.optString("failed_step_name", ""));
            String summary = GitHubWorkContract.JOB.equals(workType) && !failedStep.isEmpty()
                    ? "failed step · " + failedStep : workType + " · " + lifecycle;
            nodes.put(new JSONObject()
                    .put("id", id)
                    .put("title", payload.optString("title", id))
                    .put("summary", summary)
                    .put("detail", workDetail(attributes, payload.optString("url", "")))
                    .put("groupId", "group:github-work")
                    .put("domainId", "system:github")
                    .put("entityType", "node")
                    .put("nodeType", workType.toLowerCase())
                    .put("workType", workType)
                    .put("parentId", payload.optString("parent_id", ""))
                    .put("url", payload.optString("url", ""))
                    .put("status", workStatus(lifecycle, record.optString("sync_status", "active")))
                    .put("syncStatus", record.optString("sync_status", "active"))
                    .put("firstSeen", record.optLong("firstSeen", 0L))
                    .put("lastSeen", record.optLong("lastSeen", 0L))
                    .put("attributes", new JSONObject(attributes.toString())));
            projectedEntities++;
        }

        JSONArray edges = state.optJSONArray("relations");
        if (edges == null) edges = new JSONArray();
        int projectedRelations = 0;
        for (int i = 0; i < edges.length(); i++) {
            JSONObject record = edges.optJSONObject(i);
            JSONObject payload = record == null ? null : record.optJSONObject("payload");
            String id = record == null ? "" : clean(record.optString("stable_id", ""));
            String from = payload == null ? "" : clean(payload.optString("from", ""));
            String to = payload == null ? "" : clean(payload.optString("to", ""));
            if (payload == null || !GitHubWorkContract.PROVIDER.equals(payload.optString("provider", ""))
                    || !nodeIds.contains(from) || !nodeIds.contains(to) || !relationIds.add(id)) continue;
            String syncStatus = record.optString("sync_status", "active");
            relations.put(new JSONObject()
                    .put("id", id)
                    .put("from", from)
                    .put("to", to)
                    .put("type", payload.optString("relation_semantic", "related_to"))
                    .put("status", "stale".equals(syncStatus) ? "pending" : "active")
                    .put("syncStatus", syncStatus)
                    .put("gate", new JSONObject().put("enabled", !"stale".equals(syncStatus)))
                    .put("commandChain", new JSONArray()));
            projectedRelations++;
        }

        JSONObject sync = GitHubWorkSyncState.snapshot(context);
        out.put("githubWork", new JSONObject()
                .put("format", GitHubWorkContract.SCHEMA_VERSION)
                .put("readOnly", true)
                .put("rawLogsEnabled", false)
                .put("entityCount", projectedEntities)
                .put("relationCount", projectedRelations)
                .put("sync", sync));
    }

    private static JSONObject emptyGraph() {
        try {
            return new JSONObject()
                    .put("format", "amin-unified-graph")
                    .put("version", 9)
                    .put("layoutAuthority", "amin-dynamic-canvas-only")
                    .put("semanticZoom", new JSONObject().put("hysteresis", 1.08)
                            .put("sourceDepths", new JSONArray().put("repository").put("branch")
                                    .put("directory").put("file").put("class").put("function")))
                    .put("layers", new JSONObject()
                            .put("capability", new JSONObject().put("available", true).put("authoritative", true))
                            .put("source", new JSONObject().put("available", true).put("authoritative", false))
                            .put("runtime", new JSONObject().put("available", true).put("authoritative", false))
                            .put("work", new JSONObject().put("available", true).put("authoritative", false)))
                    .put("domains", new JSONArray()
                            .put(domain("system:amin", "AMIN", "Unified registry graph"))
                            .put(domain("system:evidence", "SOURCE EVIDENCE", "Synchronized evidence graph"))
                            .put(domain("system:github", "GITHUB WORK", "Read-only engineering state")))
                    .put("groups", new JSONArray()
                            .put(group("group:nodes", "NODE", "system:amin", "Knowledge / memory nodes"))
                            .put(group("group:commands", "COMMAND", "system:amin", "Executable commands"))
                            .put(group("group:evidence", "EVIDENCE", "system:evidence", "Verified source evidence"))
                            .put(group("group:github-work", "WORK", "system:github", "Repository delivery state")))
                    .put("nodes", new JSONArray())
                    .put("commands", new JSONArray())
                    .put("relations", new JSONArray())
                    .put("sourceGraph", new JSONObject().put("format", "amin-source-graph")
                            .put("version", 1).put("entities", new JSONArray()).put("relations", new JSONArray()))
                    .put("capabilitySource", new JSONObject().put("format", "amin-capability-source-map")
                            .put("version", 1).put("mappings", new JSONArray()).put("findings", new JSONArray()))
                    .put("architectureFindings", new JSONArray())
                    .put("syncedEvidence", GraphSyncEngine.empty())
                    .put("githubWork", new JSONObject().put("readOnly", true).put("rawLogsEnabled", false)
                            .put("entityCount", 0).put("relationCount", 0))
                    .put("agentControl", new JSONObject()
                            .put("format", AgentExecutionStatusContract.SCHEMA_VERSION)
                            .put("readOnly", true).put("source", "github-work-evidence")
                            .put("rosterCount", 0).put("activeCount", 0)
                            .put("ownerAttentionCount", 0).put("statuses", new JSONArray()))
                    .put("growthPlayback", false)
                    .put("runtimeEdges", GraphRuntimeEdgeTrace.snapshotJson())
                    .put("runtimeFlows", GraphRuntimeFlowTrace.snapshotJson());
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static JSONObject domain(String id, String title, String summary) throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("summary", summary);
    }

    private static JSONObject group(String id, String title, String domainId, String summary) throws Exception {
        return new JSONObject().put("id", id).put("title", title)
                .put("domainId", domainId).put("summary", summary);
    }

    private static String workStatus(String lifecycle, String syncStatus) {
        if ("stale".equals(syncStatus) || "queued".equals(lifecycle) || "in_progress".equals(lifecycle)
                || "draft".equals(lifecycle)) {
            return "pending";
        }
        if ("failure".equals(lifecycle) || "failed".equals(lifecycle)
                || "cancelled".equals(lifecycle) || "timed_out".equals(lifecycle)
                || "changes_requested".equals(lifecycle) || "expired".equals(lifecycle)) return "blocked";
        return "active";
    }

    private static String workDetail(JSONObject attributes, String url) {
        StringBuilder out = new StringBuilder();
        java.util.Iterator<String> keys = attributes.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = attributes.optString(key, "").trim();
            if (value.isEmpty() || "0".equals(value)) continue;
            if (out.length() > 0) out.append('\n');
            out.append(key).append(": ").append(value);
        }
        if (!clean(url).isEmpty()) {
            if (out.length() > 0) out.append('\n');
            out.append("url: ").append(url);
        }
        return out.toString();
    }

    private static String evidenceStatus(String verification, String syncStatus) {
        if ("gap".equals(verification)) return "blocked";
        if ("stale".equals(syncStatus)) return "pending";
        return "active";
    }

    private static String evidenceSummary(JSONObject entity, JSONObject evidence) {
        String kind = clean(entity == null ? "" : entity.optString("kind", ""));
        String verification = clean(entity == null ? "" : entity.optString("verification", ""));
        String syncStatus = clean(entity == null ? "" : entity.optString("status", ""));
        if ("gap".equals(kind) || "gap".equals(verification)) return "Evidence gap · scanner stopped here";
        if ("stale".equals(syncStatus)) return "Evidence missing · stale / needs review";
        String path = evidence == null ? "" : clean(evidence.optString("path", ""));
        if (!path.isEmpty()) return verification + " · " + path;
        return verification.isEmpty() ? "Evidence-backed node" : verification;
    }

    private static String nodeDetail(JSONObject node) {
        StringBuilder out = new StringBuilder();
        String route = clean(node.optString("route", ""));
        if (!route.isEmpty()) out.append("route: ").append(route);
        JSONObject voice = node.optJSONObject("voice");
        JSONArray aliases = voice == null ? null : voice.optJSONArray("aliases");
        if (aliases != null && aliases.length() > 0) {
            if (out.length() > 0) out.append('\n');
            out.append("aliases: ").append(jsonList(aliases));
        }
        if (out.length() == 0) out.append(node.optString("description", ""));
        return out.toString();
    }

    private static String join(java.util.List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append("、");
            out.append(value);
        }
        return out.toString();
    }

    private static String jsonList(JSONArray values) {
        if (values == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            String value = clean(values.optString(i, ""));
            if (value.isEmpty()) continue;
            if (out.length() > 0) out.append("、");
            out.append(value);
        }
        return out.toString();
    }

    private static String first(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            String value = clean(object.optString(key, ""));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
