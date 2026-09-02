package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

/** P0 read-only contract shared by the existing chat surfaces. */
final class ConversationCapabilityContract {
    static final String CONTEXT_FORMAT = "amin-conversation-capability-context";
    static final String RESOLUTION_FORMAT = "amin-capability-resolution";
    static final int VERSION = 1;

    private ConversationCapabilityContract() { }

    static JSONArray resolutionOrder() {
        return new JSONArray()
                .put("existing_capability")
                .put("repository_implementation")
                .put("github_reusable_implementation")
                .put("small_internal_implementation")
                .put("api_mcp_connector");
    }

    static JSONObject readOnlyBoundary() throws Exception {
        return new JSONObject()
                .put("mode", "read_only")
                .put("execution_allowed", false)
                .put("graph_mutation_allowed", false)
                .put("github_write_allowed", false)
                .put("self_extension_allowed", false)
                .put("autonomy_level", "none");
    }
}
