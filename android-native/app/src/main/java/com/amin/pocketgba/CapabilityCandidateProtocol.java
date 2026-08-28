package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical proposal envelope for capabilities discovered from code, UI actions, commands or connects.
 *
 * Rule: classification is deterministic first. Semantic/LLM review may propose a better classification,
 * but it must never bypass validation or human Registry approval.
 */
final class CapabilityCandidateProtocol {
    static final int VERSION = 1;

    static final String TYPE_NODE = "node";
    static final String TYPE_ACTION = "action";
    static final String TYPE_COMMAND = "command";
    static final String TYPE_CONNECT = "connect";
    static final String TYPE_TOOL = "tool";
    static final String TYPE_CONNECTOR = "connector";
    static final String TYPE_REFERENCE = "reference";
    static final String TYPE_UNKNOWN = "unknown";

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            TYPE_NODE, TYPE_ACTION, TYPE_COMMAND, TYPE_CONNECT,
            TYPE_TOOL, TYPE_CONNECTOR, TYPE_REFERENCE
    ));

    private CapabilityCandidateProtocol() {}

    static JSONObject createCandidate(JSONObject proposal, String source) {
        JSONObject input = proposal == null ? new JSONObject() : proposal;
        String type = classify(input);
        try {
            JSONObject candidate = new JSONObject(input.toString());
            candidate.put("candidate_id", "candidate:" + UUID.randomUUID().toString().substring(0, 8));
            candidate.put("candidate_protocol", "amin-capability-candidate");
            candidate.put("candidate_protocol_version", VERSION);
            candidate.put("entity_type", type);
            candidate.put("source", clean(source).isEmpty() ? "unknown" : clean(source));
            candidate.put("review_status", "generated");
            candidate.put("semantic_review_required", requiresSemanticReview(candidate));
            candidate.put("created_at", System.currentTimeMillis());
            return candidate;
        } catch (Exception error) {
            return null;
        }
    }

    static String classify(JSONObject proposal) {
        if (proposal == null) return TYPE_UNKNOWN;

        String explicit = normalizeType(proposal.optString("entity_type", proposal.optString("candidate_type", "")));
        if (!TYPE_UNKNOWN.equals(explicit)) return explicit;

        if (hasAny(proposal, "relationship_type", "relationshipType")
                || (hasAny(proposal, "source_node_id", "source") && hasAny(proposal, "target_node_id", "target"))) {
            return TYPE_CONNECT;
        }
        if (hasAny(proposal, "command_id") || proposal.optJSONArray("phrases") != null) return TYPE_COMMAND;
        if (hasAny(proposal, "tool_id")) return TYPE_TOOL;
        if (hasAny(proposal, "connector_id", "connector_type", "endpoint", "base_url")) return TYPE_CONNECTOR;
        if (hasAny(proposal, "reference_id", "content_ref", "ref")) return TYPE_REFERENCE;
        if (hasAny(proposal, "action_id", "action") && hasAny(proposal, "owner_node_id", "ownerNodeId", "capability_id")) {
            return TYPE_ACTION;
        }
        if (hasAny(proposal, "node_id", "capability_id", "route", "activity", "node_type")) return TYPE_NODE;
        return TYPE_UNKNOWN;
    }

    static boolean requiresSemanticReview(JSONObject candidate) {
        if (candidate == null) return true;
        String type = normalizeType(candidate.optString("entity_type", ""));
        if (TYPE_UNKNOWN.equals(type)) return true;

        // New UI action names are structurally valid but may duplicate an existing command/action.
        if (TYPE_ACTION.equals(type)) {
            String action = actionName(candidate);
            return action.isEmpty() || !AminActionValidator.getSupportedActions().contains(action.toUpperCase(Locale.ROOT));
        }

        // New commands should be semantically checked for duplicate meaning before registration.
        if (TYPE_COMMAND.equals(type)) return true;

        // A connect can be validated structurally; semantic review is only required when no known relationship type is supplied.
        if (TYPE_CONNECT.equals(type)) {
            String relationship = candidate.optString("relationship_type", candidate.optString("relationshipType", ""));
            return !GraphContract.isRelationshipTypeAllowed(relationship);
        }
        return false;
    }

    static String normalizeType(String value) {
        String type = clean(value).toLowerCase(Locale.ROOT);
        if ("page".equals(type) || "feature".equals(type) || "capability".equals(type)) return TYPE_NODE;
        if ("edge".equals(type) || "relationship".equals(type)) return TYPE_CONNECT;
        return TYPES.contains(type) ? type : TYPE_UNKNOWN;
    }

    static String actionName(JSONObject object) {
        if (object == null) return "";
        String value = object.optString("action_id", object.optString("action", ""));
        return clean(value);
    }

    private static boolean hasAny(JSONObject object, String... keys) {
        if (object == null || keys == null) return false;
        for (String key : keys) {
            if (!object.has(key)) continue;
            Object value = object.opt(key);
            if (value instanceof JSONArray) {
                if (((JSONArray) value).length() > 0) return true;
            } else if (value != null && !clean(String.valueOf(value)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static String clean(String value) { return value == null ? "" : value.trim(); }
}
