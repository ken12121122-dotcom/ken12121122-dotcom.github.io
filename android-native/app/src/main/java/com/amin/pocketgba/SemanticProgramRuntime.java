package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

/** Validates Semantic Program IR before delegating to the existing read-only resolver. */
final class SemanticProgramRuntime {
    private SemanticProgramRuntime() { }

    static JSONObject resolve(JSONObject program, JSONObject context) {
        try {
            SemanticProgramContract.validate(program);
            String query = program.getJSONObject("input").getString("query");
            JSONObject resolution = CapabilityResolver.resolve(query, context);
            resolution.put("semantic_program_validation", "passed");
            resolution.put("semantic_program", new JSONObject(program.toString()));
            return resolution;
        } catch (Exception error) {
            try {
                return new JSONObject()
                        .put("format", ConversationCapabilityContract.RESOLUTION_FORMAT)
                        .put("version", ConversationCapabilityContract.VERSION)
                        .put("resolution_status", "failed")
                        .put("resolution_stage", "semantic_program_validation")
                        .put("boundary", ConversationCapabilityContract.readOnlyBoundary())
                        .put("matches", new JSONArray())
                        .put("evidence", new JSONArray())
                        .put("unresolved_gaps", new JSONArray()
                                .put("semantic_program_invalid:" + error.getMessage()))
                        .put("owner_attention_required", false)
                        .put("answer", "語意程式未通過唯讀能力邊界驗證，沒有執行任何動作。")
                        .put("spoken_answer", "語意程式未通過安全驗證，沒有執行任何動作。");
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }
}
