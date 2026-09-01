package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class AgentExecutionStatusContractTest {
    @Test public void acceptsStrictReadOnlyGitHubEvidenceMarker() {
        JSONObject marker = AgentExecutionStatusContract.parsePullRequestBody("before\n"
                + "<!-- amin-agent-execution {\"agent_id\":\"agent:codex\","
                + "\"agent_type\":\"codex\",\"role\":\"Backend / Platform Engineer\","
                + "\"issue_number\":122,\"current_task\":\"Step 12 dashboard\","
                + "\"status\":\"working\",\"owner_attention_required\":false} -->\nafter");

        assertTrue(marker.toString(), marker.optBoolean("valid", false));
        assertEquals(AgentExecutionStatusContract.SCHEMA_VERSION,
                marker.optString("schema_version", ""));
        assertEquals(AgentExecutionStatusContract.CODEX, marker.optString("agent_id", ""));
        assertEquals("working", marker.optString("status", ""));
        assertTrue(marker.optBoolean("read_only", false));
        assertFalse(marker.has("ci_status"));
    }

    @Test public void rejectsUnverifiableOrIncompleteMarkers() {
        JSONObject unknown = AgentExecutionStatusContract.parsePullRequestBody("no marker");
        assertEquals(0, unknown.length());

        JSONObject invalid = AgentExecutionStatusContract.parsePullRequestBody(
                "<!-- amin-agent-execution {\"agent_id\":\"Codex\","
                        + "\"agent_type\":\"codex\",\"role\":\"Backend\","
                        + "\"status\":\"blocked\"} -->");
        assertFalse(invalid.optBoolean("valid", true));
        assertEquals("INVALID_AGENT_ID", invalid.optString("error", ""));
    }

    @Test public void ownerAttentionRequiresExplicitReason() throws Exception {
        JSONObject input = new JSONObject()
                .put("agent_id", "agent:chatgpt")
                .put("agent_type", "chatgpt")
                .put("role", "Orchestrator")
                .put("current_task", "Review roadmap")
                .put("status", "waiting")
                .put("owner_attention_required", true);
        try {
            AgentExecutionStatusContract.normalize(input);
            throw new AssertionError("Expected owner attention reason gate");
        } catch (IllegalArgumentException expected) {
            assertEquals("OWNER_ATTENTION_REASON_REQUIRED", expected.getMessage());
        }
    }

    @Test public void rejectsAmbiguousOrMismatchedPermanentIdentity() {
        String marker = "<!-- amin-agent-execution {\"agent_id\":\"agent:codex\","
                + "\"agent_type\":\"codex\",\"role\":\"Backend\",\"current_task\":\"One\","
                + "\"status\":\"working\"} -->";
        JSONObject ambiguous = AgentExecutionStatusContract.parsePullRequestBody(marker + marker);
        assertFalse(ambiguous.optBoolean("valid", true));
        assertEquals("AGENT_MARKER_AMBIGUOUS", ambiguous.optString("error", ""));

        JSONObject mismatch = AgentExecutionStatusContract.parsePullRequestBody(
                "<!-- amin-agent-execution {\"agent_id\":\"agent:codex\","
                        + "\"agent_type\":\"chatgpt\",\"role\":\"Backend\","
                        + "\"current_task\":\"One\",\"status\":\"working\"} -->");
        assertFalse(mismatch.optBoolean("valid", true));
        assertEquals("AGENT_ID_TYPE_MISMATCH", mismatch.optString("error", ""));
    }
}
