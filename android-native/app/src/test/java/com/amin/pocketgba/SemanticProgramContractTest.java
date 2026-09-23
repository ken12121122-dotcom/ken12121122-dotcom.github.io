package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class SemanticProgramContractTest {
    @Test public void compilesCapabilityQuestionIntoVersionedReadOnlyIr() throws Exception {
        JSONObject program = SemanticProgramContract.compileCapabilityQuery("你目前有哪些能力？");

        SemanticProgramContract.validate(program);
        assertEquals("amin-semantic-program", program.getString("format"));
        assertEquals(1, program.getInt("version"));
        assertEquals("list_capabilities", program.getString("intent"));
        assertEquals("capability.inventory.read",
                program.getJSONArray("capability_requirements").getString(0));
        assertFalse(program.getJSONObject("boundary").getBoolean("execution_allowed"));
        assertFalse(program.getJSONObject("boundary").getBoolean("github_write_allowed"));
    }

    @Test public void equivalentLanguageCompilesToStableCanonicalProgram() throws Exception {
        JSONObject listA = SemanticProgramContract.compileCapabilityQuery("你目前有哪些能力？");
        JSONObject listB = SemanticProgramContract.compileCapabilityQuery("能力清單");
        JSONObject findA = SemanticProgramContract.compileCapabilityQuery("有沒有記帳能力");
        JSONObject findB = SemanticProgramContract.compileCapabilityQuery("尋找能力 記帳");

        assertEquals("list_capabilities", listA.getString("intent"));
        assertEquals(listA.getString("intent"), listB.getString("intent"));
        assertEquals(listA.getJSONArray("capability_requirements").toString(),
                listB.getJSONArray("capability_requirements").toString());
        assertEquals(listA.getJSONObject("route").getString("resolver"),
                listB.getJSONObject("route").getString("resolver"));
        assertEquals(listA.getJSONObject("boundary").toString(),
                listB.getJSONObject("boundary").toString());

        assertEquals("find_capability", findA.getString("intent"));
        assertEquals(findA.getString("intent"), findB.getString("intent"));
        assertEquals(findA.getJSONArray("capability_requirements").toString(),
                findB.getJSONArray("capability_requirements").toString());
        assertEquals(findA.getJSONObject("route").getString("resolver"),
                findB.getJSONObject("route").getString("resolver"));
    }

    @Test public void ordinaryConversationDoesNotBecomeSemanticCapabilityProgram() throws Exception {
        try {
            SemanticProgramContract.compileCapabilityQuery("今天天氣如何");
            throw new AssertionError("Expected unsupported intent rejection");
        } catch (IllegalArgumentException expected) {
            assertEquals("SEMANTIC_PROGRAM_UNSUPPORTED_INTENT", expected.getMessage());
        }
    }

    @Test public void rejectsUnknownCapabilityRequirementBeforeResolution() throws Exception {
        JSONObject program = SemanticProgramContract.compileCapabilityQuery("有沒有記帳能力");
        program.put("capability_requirements", new JSONArray().put("unknown.delete_everything"));

        JSONObject result = SemanticProgramRuntime.resolve(program, context());

        assertEquals("failed", result.getString("resolution_status"));
        assertEquals("semantic_program_validation", result.getString("resolution_stage"));
        assertEquals(0, result.getJSONArray("matches").length());
        assertTrue(result.getJSONArray("unresolved_gaps").getString(0)
                .contains("SEMANTIC_PROGRAM_CAPABILITY_UNREGISTERED"));
    }

    @Test public void rejectsAttemptToEscalateExecutionBoundary() throws Exception {
        JSONObject program = SemanticProgramContract.compileCapabilityQuery("有沒有記帳能力");
        program.getJSONObject("boundary").put("execution_allowed", true);

        JSONObject result = SemanticProgramRuntime.resolve(program, context());

        assertEquals("failed", result.getString("resolution_status"));
        assertTrue(result.getJSONArray("unresolved_gaps").getString(0)
                .contains("SEMANTIC_PROGRAM_BOUNDARY_VIOLATION"));
    }

    @Test public void validatedProgramReusesExistingCapabilityResolver() throws Exception {
        JSONObject program = SemanticProgramContract.compileCapabilityQuery("有沒有記帳能力");
        JSONObject result = SemanticProgramRuntime.resolve(program, context());

        assertEquals("resolved_existing_capability", result.getString("resolution_status"));
        assertEquals("existing_capability", result.getString("resolution_stage"));
        assertEquals("passed", result.getString("semantic_program_validation"));
        assertEquals("find_capability", result.getJSONObject("semantic_program").getString("intent"));
        assertEquals(1, result.getJSONArray("matches").length());
        assertFalse(result.getJSONObject("boundary").getBoolean("execution_allowed"));
    }

    private static JSONObject context() throws Exception {
        JSONObject finance = new JSONObject()
                .put("stable_id", "finance.transaction.create")
                .put("payload", new JSONObject()
                        .put("entity_type", "CAPABILITY")
                        .put("title", "新增收支")
                        .put("description", "記帳")
                        .put("input_context", new JSONObject().put("mode", "managed_md")
                                .put("context_node_id", "app:finance-transaction-create-md"))
                        .put("voice", new JSONObject().put("enabled", true)));
        return new JSONObject()
                .put("summary", new JSONObject().put("total", 1)
                        .put("chat_addressable", 1).put("bridge_required", 0))
                .put("capabilities", new JSONArray().put(finance))
                .put("source_records", new JSONArray().put(new JSONObject()
                        .put("source_id", "node_registry").put("authority", "android_manifest")))
                .put("unresolved_gaps", new JSONArray());
    }
}
