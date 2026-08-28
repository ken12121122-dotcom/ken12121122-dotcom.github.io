package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Deterministic validation layer before semantic review and human approval. */
final class CapabilityCandidateValidator {
    static final class Result {
        final boolean valid;
        final String code;
        final String message;
        final JSONArray warnings;

        private Result(boolean valid, String code, String message, JSONArray warnings) {
            this.valid = valid;
            this.code = code;
            this.message = message;
            this.warnings = warnings == null ? new JSONArray() : warnings;
        }

        static Result ok(JSONArray warnings) { return new Result(true, "OK", "Candidate accepted", warnings); }
        static Result fail(String code, String message) { return new Result(false, code, message, new JSONArray()); }
    }

    Result validate(Context context, NodeMetadataStore nodeStore, JSONObject candidate) {
        if (candidate == null) return Result.fail("CANDIDATE_REQUIRED", "Missing capability candidate");
        if (!"amin-capability-candidate".equals(candidate.optString("candidate_protocol", ""))) {
            return Result.fail("PROTOCOL_INVALID", "Unsupported candidate protocol");
        }
        if (candidate.optInt("candidate_protocol_version", 0) != CapabilityCandidateProtocol.VERSION) {
            return Result.fail("PROTOCOL_VERSION_INVALID", "Unsupported candidate protocol version");
        }
        if (clean(candidate.optString("candidate_id", "")).isEmpty()) {
            return Result.fail("CANDIDATE_ID_REQUIRED", "candidate_id is required");
        }

        String type = CapabilityCandidateProtocol.normalizeType(candidate.optString("entity_type", ""));
        switch (type) {
            case CapabilityCandidateProtocol.TYPE_NODE:
                return validateNode(context, nodeStore, candidate);
            case CapabilityCandidateProtocol.TYPE_ACTION:
                return validateAction(context, nodeStore, candidate);
            case CapabilityCandidateProtocol.TYPE_COMMAND:
                return validateCommand(candidate);
            case CapabilityCandidateProtocol.TYPE_CONNECT:
                return validateConnect(context, nodeStore, candidate);
            case CapabilityCandidateProtocol.TYPE_TOOL:
                return requireId(candidate, "tool_id", "TOOL_ID_REQUIRED");
            case CapabilityCandidateProtocol.TYPE_CONNECTOR:
                return requireId(candidate, "connector_id", "CONNECTOR_ID_REQUIRED");
            case CapabilityCandidateProtocol.TYPE_REFERENCE:
                return requireId(candidate, "reference_id", "REFERENCE_ID_REQUIRED");
            default:
                return Result.fail("TYPE_UNKNOWN", "Candidate type requires semantic classification");
        }
    }

    private Result validateNode(Context context, NodeMetadataStore nodeStore, JSONObject candidate) {
        String capabilityId = first(candidate, "capability_id", "capabilityId", "node_id", "nodeId");
        String title = first(candidate, "title", "name");
        if (capabilityId.isEmpty()) return Result.fail("CAPABILITY_ID_REQUIRED", "Node capability_id is required");
        if (title.isEmpty()) return Result.fail("TITLE_REQUIRED", "Node title is required");
        JSONArray warnings = new JSONArray();
        if (context != null && NodeRegistry.findNode(context, nodeStore, capabilityId) != null) {
            warnings.put("POSSIBLE_DUPLICATE_NODE:" + capabilityId);
        }
        return Result.ok(warnings);
    }

    private Result validateAction(Context context, NodeMetadataStore nodeStore, JSONObject candidate) {
        String owner = first(candidate, "owner_node_id", "ownerNodeId", "capability_id", "capabilityId");
        String action = CapabilityCandidateProtocol.actionName(candidate);
        if (owner.isEmpty()) return Result.fail("OWNER_NODE_REQUIRED", "Action owner_node_id is required");
        if (action.isEmpty()) return Result.fail("ACTION_ID_REQUIRED", "Action action_id/action is required");
        if (!action.matches("[A-Za-z0-9_.:-]{1,96}")) {
            return Result.fail("ACTION_ID_INVALID", "Action id contains unsupported characters");
        }
        if (context != null && NodeRegistry.findNode(context, nodeStore, owner) == null) {
            return Result.fail("OWNER_NODE_NOT_FOUND", "Action owner node is not registered: " + owner);
        }
        JSONArray warnings = new JSONArray();
        if (!AminActionValidator.getSupportedActions().contains(action.toUpperCase(Locale.ROOT))) {
            warnings.put("NEW_ACTION_REQUIRES_SEMANTIC_REVIEW:" + action);
        }
        return Result.ok(warnings);
    }

    private Result validateCommand(JSONObject candidate) {
        String commandId = first(candidate, "command_id", "id");
        String action = first(candidate, "action", "action_id");
        if (commandId.isEmpty()) return Result.fail("COMMAND_ID_REQUIRED", "command_id is required");
        if (action.isEmpty()) return Result.fail("COMMAND_ACTION_REQUIRED", "Command action is required");
        JSONArray warnings = new JSONArray();
        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            if (commandId.equalsIgnoreCase(command.getId())) warnings.put("DUPLICATE_COMMAND_ID:" + commandId);
            if (action.equalsIgnoreCase(command.getAction())) warnings.put("ACTION_ALREADY_HAS_COMMAND:" + action);
        }
        warnings.put("COMMAND_SEMANTIC_REVIEW_REQUIRED");
        return Result.ok(warnings);
    }

    private Result validateConnect(Context context, NodeMetadataStore nodeStore, JSONObject candidate) {
        String source = first(candidate, "source_node_id", "source", "from");
        String target = first(candidate, "target_node_id", "target", "to");
        String relationship = CapabilityCandidateProtocol.relationshipName(candidate);
        if (source.isEmpty()) return Result.fail("SOURCE_REQUIRED", "Connect source node is required");
        if (target.isEmpty()) return Result.fail("TARGET_REQUIRED", "Connect target node is required");
        if (source.equals(target)) return Result.fail("SELF_CONNECT_NOT_ALLOWED", "Connect source and target must differ");
        if ("related_to".equals(relationship) || relationship.isEmpty()) {
            return Result.fail("RELATIONSHIP_REQUIRES_CLASSIFICATION", "Draft connect must be classified before registration");
        }
        if (!GraphContract.isRelationshipTypeAllowed(relationship)) {
            return Result.fail("RELATIONSHIP_INVALID", "Unsupported relationship type: " + relationship);
        }
        if (context != null) {
            if (NodeRegistry.findNode(context, nodeStore, source) == null) {
                return Result.fail("SOURCE_NOT_REGISTERED", "Connect source is not registered: " + source);
            }
            if (NodeRegistry.findNode(context, nodeStore, target) == null) {
                return Result.fail("TARGET_NOT_REGISTERED", "Connect target is not registered: " + target);
            }
        }
        return Result.ok(new JSONArray());
    }

    private Result requireId(JSONObject candidate, String key, String code) {
        return clean(candidate.optString(key, "")).isEmpty()
                ? Result.fail(code, key + " is required")
                : Result.ok(new JSONArray());
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
