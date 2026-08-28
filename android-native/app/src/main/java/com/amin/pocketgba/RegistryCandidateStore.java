package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Pending registration candidates. Nothing becomes registered until approve(...) is called.
 * Action and Connect approval re-run deterministic validation immediately before persistence.
 */
final class RegistryCandidateStore {
    private static final String PREFS = "amin_registry_candidates";
    private static final String KEY_PENDING = "pending";
    private final Context context;
    private final SharedPreferences prefs;

    RegistryCandidateStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONObject createNodeCandidate(String requestedName, String sourceText) {
        try {
            String title = clean(requestedName);
            if (title.isEmpty()) title = "新節點";
            String candidateId = "candidate:" + UUID.randomUUID().toString().substring(0, 8);
            JSONObject candidate = new JSONObject()
                    .put("candidate_id", candidateId)
                    .put("entity_type", "node")
                    .put("title", title)
                    .put("source_text", clean(sourceText))
                    .put("review_status", "generated")
                    .put("created_at", System.currentTimeMillis());
            save(candidate);
            GraphRuntimeFlowTrace.startNodeCreation(candidateId, title);
            GraphRuntimeFlowTrace.step(candidateId, "candidate", "建立 Candidate", "success");
            GraphRuntimeFlowTrace.step(candidateId, "approval", "等待人工 Approval", "waiting");
            UnifiedGraphProvider.notifyChanged(context);
            return candidate;
        } catch (Exception error) { return null; }
    }

    JSONObject createCommandCandidate(String id, String title, String description, String action, JSONArray phrases) {
        try {
            JSONObject candidate = new JSONObject()
                    .put("candidate_id", "candidate:" + UUID.randomUUID().toString().substring(0, 8))
                    .put("entity_type", "command")
                    .put("command_id", clean(id))
                    .put("title", clean(title))
                    .put("description", clean(description))
                    .put("action", clean(action))
                    .put("phrases", phrases == null ? new JSONArray() : phrases)
                    .put("review_status", "generated")
                    .put("created_at", System.currentTimeMillis());
            save(candidate);
            return candidate;
        } catch (Exception error) { return null; }
    }

    JSONObject createActionCandidate(String ownerNodeId, String action, String sourceText) {
        try {
            JSONObject proposal = new JSONObject()
                    .put("entity_type", CapabilityCandidateProtocol.TYPE_ACTION)
                    .put("owner_node_id", clean(ownerNodeId))
                    .put("action", clean(action))
                    .put("source_text", clean(sourceText));
            JSONObject candidate = CapabilityCandidateProtocol.createCandidate(proposal, "registry-action");
            if (candidate == null) return null;
            save(candidate);
            UnifiedGraphProvider.notifyChanged(context);
            return candidate;
        } catch (Exception error) { return null; }
    }

    JSONObject createConnectCandidate(JSONObject proposedEdge, String sourceText) {
        try {
            JSONObject edge = proposedEdge == null ? new JSONObject() : new JSONObject(proposedEdge.toString());
            String source = first(edge, "source_node_id", "source", "from");
            String target = first(edge, "target_node_id", "target", "to");
            String relationship = CapabilityCandidateProtocol.relationshipName(edge);
            if (relationship.isEmpty()) relationship = "related_to";
            String edgeId = first(edge, "edge_id", "edgeId");
            if (edgeId.isEmpty()) edgeId = "edge:" + UUID.randomUUID().toString().substring(0, 8);

            edge.put("entity_type", CapabilityCandidateProtocol.TYPE_CONNECT)
                    .put("edge_id", edgeId)
                    .put("edgeId", edgeId)
                    .put("source_node_id", source).put("source", source).put("from", source)
                    .put("target_node_id", target).put("target", target).put("to", target)
                    .put("relationship_type", relationship).put("relationshipType", relationship).put("relation", relationship)
                    .put("source_text", clean(sourceText));
            if (edge.optJSONObject("gate") == null) edge.put("gate", new JSONObject().put("enabled", true));
            if (edge.optJSONArray("command_chain") == null) edge.put("command_chain", new JSONArray());

            JSONObject candidate = CapabilityCandidateProtocol.createCandidate(edge, "registry-connect");
            if (candidate == null) return null;
            save(candidate);
            UnifiedGraphProvider.notifyChanged(context);
            return candidate;
        } catch (Exception error) { return null; }
    }

    JSONObject approve(JSONObject candidate, NodeMetadataStore nodeStore) {
        if (candidate == null || nodeStore == null) return null;
        String type = candidate.optString("entity_type", "");
        String candidateId = candidate.optString("candidate_id", "");
        JSONObject registered = null;
        if ("node".equals(type)) {
            GraphRuntimeFlowTrace.step(candidateId, "approval", "人工 Approval", "success");
            GraphRuntimeFlowTrace.step(candidateId, "registry", "寫入 Node Registry", "running");
            registered = nodeStore.registerApprovedNode(
                    candidate.optString("title", ""),
                    candidate.optString("source_text", "")
            );
        } else if ("command".equals(type)) {
            try {
                JSONObject command = new JSONObject()
                        .put("id", candidate.optString("command_id", ""))
                        .put("title", candidate.optString("title", ""))
                        .put("description", candidate.optString("description", ""))
                        .put("action", candidate.optString("action", ""))
                        .put("phrases", candidate.optJSONArray("phrases") == null ? new JSONArray() : candidate.optJSONArray("phrases"));
                registered = new DynamicCommandStore(context).register(command);
            } catch (Exception ignored) { }
        } else if (CapabilityCandidateProtocol.TYPE_ACTION.equals(type)) {
            CapabilityCandidateValidator.Result validation = new CapabilityCandidateValidator().validate(context, nodeStore, candidate);
            if (!validation.valid) return null;
            registered = nodeStore.registerApprovedAction(
                    first(candidate, "owner_node_id", "ownerNodeId", "capability_id", "capabilityId"),
                    CapabilityCandidateProtocol.actionName(candidate)
            );
        } else if (CapabilityCandidateProtocol.TYPE_CONNECT.equals(type)) {
            CapabilityCandidateValidator.Result validation = new CapabilityCandidateValidator().validate(context, nodeStore, candidate);
            if (!validation.valid) return null;
            registered = nodeStore.registerApprovedConnect(candidate);
        }
        if (registered != null) {
            remove(candidateId);
            if ("node".equals(type)) {
                String nodeId = registered.optString("node_id", registered.optString("nodeId", ""));
                GraphRuntimeFlowTrace.step(candidateId, "registry", "寫入 Node Registry", "success");
                GraphRuntimeFlowTrace.step(candidateId, "refresh", "Unified Graph Refresh", "success");
                GraphRuntimeFlowTrace.finish(candidateId, "completed", nodeId);
            }
            UnifiedGraphProvider.notifyChanged(context);
        } else if ("node".equals(type)) {
            GraphRuntimeFlowTrace.step(candidateId, "registry", "寫入 Node Registry", "failed");
            GraphRuntimeFlowTrace.finish(candidateId, "failed", "");
            UnifiedGraphProvider.notifyChanged(context);
        }
        return registered;
    }

    void reject(JSONObject candidate) {
        if (candidate == null) return;
        String candidateId = candidate.optString("candidate_id", "");
        if ("node".equals(candidate.optString("entity_type", ""))) {
            GraphRuntimeFlowTrace.step(candidateId, "approval", "人工 Approval", "failed");
            GraphRuntimeFlowTrace.finish(candidateId, "rejected", "");
            UnifiedGraphProvider.notifyChanged(context);
        }
        remove(candidateId);
        UnifiedGraphProvider.notifyChanged(context);
    }

    JSONArray pending() {
        try { return new JSONArray(prefs.getString(KEY_PENDING, "[]")); }
        catch (Exception error) { return new JSONArray(); }
    }

    private void save(JSONObject candidate) {
        if (candidate == null) return;
        JSONArray source = pending();
        source.put(candidate);
        prefs.edit().putString(KEY_PENDING, source.toString()).apply();
    }

    private void remove(String candidateId) {
        String wanted = clean(candidateId);
        JSONArray source = pending();
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject current = source.optJSONObject(i);
            if (current != null && !wanted.equals(current.optString("candidate_id", ""))) out.put(current);
        }
        prefs.edit().putString(KEY_PENDING, out.toString()).apply();
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
