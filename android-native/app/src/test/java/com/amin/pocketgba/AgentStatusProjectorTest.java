package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public final class AgentStatusProjectorTest {
    @Test public void projectsRosterAndDerivesCiFromExistingWorkRelations() throws Exception {
        JSONObject marker = AgentExecutionStatusContract.normalize(new JSONObject()
                .put("agent_id", AgentExecutionStatusContract.CODEX)
                .put("agent_type", "codex")
                .put("role", "Backend / Platform Engineer")
                .put("issue_number", 122)
                .put("current_task", "Step 12 dashboard")
                .put("status", "review")
                .put("owner_attention_required", false));
        String prId = "github:pr:1095700954:129";
        String issueId = "github:issue:1095700954:122";
        String runId = "github:run:1095700954:9001";
        JSONArray nodes = new JSONArray()
                .put(node(prId, GitHubWorkContract.PULL_REQUEST,
                        new JSONObject().put("number", 129).put("head_ref", "feat/step12")
                                .put("updated_at", "2026-09-01T01:00:00Z")
                                .put("agent_execution", marker)))
                .put(node(issueId, GitHubWorkContract.ISSUE,
                        new JSONObject().put("number", 122)))
                .put(node(runId, GitHubWorkContract.WORKFLOW_RUN,
                        new JSONObject().put("status", "completed").put("conclusion", "success")
                                .put("updated_at", "2026-09-01T02:00:00Z")));
        JSONArray relations = new JSONArray().put(new JSONObject()
                .put("id", "github:relation:pr-run:129:9001")
                .put("from", prId).put("to", runId).put("type", "has_run"));
        Set<String> nodeIds = ids(nodes);
        Set<String> relationIds = new HashSet<>();
        relationIds.add("github:relation:pr-run:129:9001");
        JSONObject graph = new JSONObject().put("groups", new JSONArray());

        AgentStatusProjector.append(graph, nodes, relations, nodeIds, relationIds);

        JSONObject control = graph.getJSONObject("agentControl");
        assertTrue(control.getBoolean("readOnly"));
        assertEquals(3, control.getInt("rosterCount"));
        assertEquals(1, control.getInt("activeCount"));
        JSONObject codex = findStatus(control.getJSONArray("statuses"), AgentExecutionStatusContract.CODEX);
        assertEquals("review", codex.getString("status"));
        assertEquals("passing", codex.getString("ci_status"));
        assertEquals(3, codex.getJSONArray("evidence").length());
        assertEquals(3, countAgents(nodes));
        assertTrue(hasRelation(relations, AgentExecutionStatusContract.CODEX, prId));
        assertTrue(hasRelation(relations, AgentExecutionStatusContract.CODEX, issueId));
        assertFalse(codex.getBoolean("owner_attention_required"));
    }

    @Test public void noMarkerMeansIdleAndCannotInventAssignment() throws Exception {
        JSONArray nodes = new JSONArray().put(node("github:pr:1:1", GitHubWorkContract.PULL_REQUEST,
                new JSONObject().put("number", 1).put("updated_at", "now")));
        JSONArray relations = new JSONArray();
        JSONObject graph = new JSONObject().put("groups", new JSONArray());
        Set<String> ids = ids(nodes);

        AgentStatusProjector.append(graph, nodes, relations, ids, new HashSet<>());

        JSONArray statuses = graph.getJSONObject("agentControl").getJSONArray("statuses");
        for (int i = 0; i < statuses.length(); i++) {
            JSONObject status = statuses.getJSONObject(i);
            assertEquals("idle", status.getString("status"));
            assertEquals(0, status.getJSONArray("evidence").length());
        }
        assertEquals(0, graph.getJSONObject("agentControl").getInt("activeCount"));
    }

    private static JSONObject node(String id, String type, JSONObject attributes) throws Exception {
        return new JSONObject().put("id", id).put("title", id).put("url", "https://example/" + id)
                .put("workType", type).put("attributes", attributes);
    }

    private static Set<String> ids(JSONArray nodes) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < nodes.length(); i++) out.add(nodes.optJSONObject(i).optString("id", ""));
        return out;
    }

    private static JSONObject findStatus(JSONArray statuses, String id) throws Exception {
        for (int i = 0; i < statuses.length(); i++) {
            JSONObject status = statuses.getJSONObject(i);
            if (id.equals(status.optString("agent_id", ""))) return status;
        }
        throw new AssertionError("Missing " + id);
    }

    private static int countAgents(JSONArray nodes) {
        int count = 0;
        for (int i = 0; i < nodes.length(); i++) {
            if ("agent".equals(nodes.optJSONObject(i).optString("entityType", ""))) count++;
        }
        return count;
    }

    private static boolean hasRelation(JSONArray relations, String from, String to) {
        for (int i = 0; i < relations.length(); i++) {
            JSONObject relation = relations.optJSONObject(i);
            if (from.equals(relation.optString("from", "")) && to.equals(relation.optString("to", ""))) {
                return true;
            }
        }
        return false;
    }
}
