package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

/** Versioned, fail-closed IR between conversational intent and capability resolution. */
final class SemanticProgramContract {
    static final String FORMAT = "amin-semantic-program";
    static final int VERSION = 1;
    static final String CAPABILITY_INVENTORY_READ = "capability.inventory.read";
    static final String ROUTE_CAPABILITY_RESOLVER = "capability_resolver";

    private SemanticProgramContract() { }

    static JSONObject compileCapabilityQuery(String rawQuery) throws Exception {
        if (!CapabilityResolver.isCapabilityQuestion(rawQuery)) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_UNSUPPORTED_INTENT");
        }
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) throw new IllegalArgumentException("SEMANTIC_PROGRAM_QUERY_REQUIRED");
        return new JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("status", "generated")
                .put("intent", CapabilityResolver.isListRequest(rawQuery)
                        ? "list_capabilities" : "find_capability")
                .put("input", new JSONObject().put("query", query))
                .put("capability_requirements", new JSONArray()
                        .put(CAPABILITY_INVENTORY_READ))
                .put("route", new JSONObject()
                        .put("resolver", ROUTE_CAPABILITY_RESOLVER))
                .put("boundary", ConversationCapabilityContract.readOnlyBoundary())
                .put("evidence_contract", new JSONArray()
                        .put("source_records")
                        .put("unresolved_gaps"));
    }

    static void validate(JSONObject program) throws Exception {
        if (program == null) throw new IllegalArgumentException("SEMANTIC_PROGRAM_REQUIRED");
        if (!FORMAT.equals(program.optString("format", ""))) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_FORMAT_INVALID");
        }
        if (program.optInt("version", -1) != VERSION) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_VERSION_UNSUPPORTED");
        }
        String intent = program.optString("intent", "");
        if (!"list_capabilities".equals(intent) && !"find_capability".equals(intent)) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_INTENT_UNSUPPORTED");
        }
        JSONObject input = program.optJSONObject("input");
        if (input == null || input.optString("query", "").trim().isEmpty()) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_QUERY_REQUIRED");
        }
        String query = input.getString("query");
        if (!CapabilityResolver.isCapabilityQuestion(query)) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_UNSUPPORTED_INTENT");
        }
        String canonicalIntent = CapabilityResolver.isListRequest(query)
                ? "list_capabilities" : "find_capability";
        if (!canonicalIntent.equals(intent)) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_INTENT_QUERY_MISMATCH");
        }

        JSONArray requirements = program.optJSONArray("capability_requirements");
        if (requirements == null || requirements.length() != 1
                || !CAPABILITY_INVENTORY_READ.equals(requirements.optString(0, ""))) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_CAPABILITY_UNREGISTERED");
        }

        JSONObject route = program.optJSONObject("route");
        if (route == null || !ROUTE_CAPABILITY_RESOLVER.equals(route.optString("resolver", ""))) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_ROUTE_UNSUPPORTED");
        }

        JSONObject boundary = program.optJSONObject("boundary");
        if (boundary == null
                || !"read_only".equals(boundary.optString("mode", ""))
                || boundary.optBoolean("execution_allowed", true)
                || boundary.optBoolean("graph_mutation_allowed", true)
                || boundary.optBoolean("github_write_allowed", true)
                || boundary.optBoolean("self_extension_allowed", true)
                || !"none".equals(boundary.optString("autonomy_level", ""))) {
            throw new IllegalArgumentException("SEMANTIC_PROGRAM_BOUNDARY_VIOLATION");
        }
    }
}
