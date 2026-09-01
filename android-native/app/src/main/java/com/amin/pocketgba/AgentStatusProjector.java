package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects strict GitHub evidence into the existing Unified Graph and single Canvas. */
final class AgentStatusProjector {
    private static final String GROUP_ID = "group:agents";

    private AgentStatusProjector() { }

    static void append(JSONObject out, JSONArray nodes, JSONArray relations,
                       Set<String> nodeIds, Set<String> relationIds) throws Exception {
        if (out == null || nodes == null || relations == null) return;
        Map<String, JSONObject> byId = indexNodes(nodes);
        Map<String, JSONObject> latestByAgent = latestAssignments(nodes);
        JSONArray statuses = new JSONArray();
        int activeCount = 0;
        int attentionCount = 0;

        for (JSONObject roster : roster()) {
            String agentId = roster.getString("agent_id");
            JSONObject assignment = latestByAgent.get(agentId);
            JSONObject status = buildStatus(roster, assignment, byId, relations);
            statuses.put(status);
            if (!"idle".equals(status.getString("status"))) activeCount++;
            if (status.optBoolean("owner_attention_required", false)) attentionCount++;

            if (nodeIds.add(agentId)) {
                nodes.put(agentNode(status));
                byId.put(agentId, nodes.getJSONObject(nodes.length() - 1));
            }
            JSONArray evidence = status.optJSONArray("evidence");
            if (evidence == null) continue;
            for (int i = 0; i < evidence.length(); i++) {
                JSONObject item = evidence.optJSONObject(i);
                String target = item == null ? "" : item.optString("source_ref", "");
                if (target.isEmpty() || !nodeIds.contains(target)) continue;
                String edgeId = "agent-status:relation:" + slug(agentId) + ":" + slug(target);
                if (!relationIds.add(edgeId)) continue;
                relations.put(new JSONObject()
                        .put("id", edgeId)
                        .put("from", agentId)
                        .put("to", target)
                        .put("type", "assigned_to")
                        .put("status", "active")
                        .put("derived", true)
                        .put("readOnly", true)
                        .put("gate", new JSONObject().put("enabled", true))
                        .put("commandChain", new JSONArray()));
            }
        }

        ensureGroup(out);
        out.put("agentControl", new JSONObject()
                .put("format", AgentExecutionStatusContract.SCHEMA_VERSION)
                .put("readOnly", true)
                .put("source", "github-work-evidence")
                .put("rosterCount", statuses.length())
                .put("activeCount", activeCount)
                .put("ownerAttentionCount", attentionCount)
                .put("statuses", statuses));
    }

    private static JSONObject buildStatus(JSONObject roster, JSONObject assignment,
                                          Map<String, JSONObject> byId,
                                          JSONArray relations) throws Exception {
        String agentId = roster.getString("agent_id");
        JSONObject assignmentAttributes = assignment == null ? null
                : assignment.optJSONObject("attributes");
        JSONObject marker = assignmentAttributes == null ? null
                : assignmentAttributes.optJSONObject("agent_execution");
        JSONObject status = new JSONObject(roster.toString())
                .put("schema_version", AgentExecutionStatusContract.SCHEMA_VERSION)
                .put("status", "idle")
                .put("ci_status", "unknown")
                .put("issue_number", 0)
                .put("branch", "")
                .put("pr_number", 0)
                .put("pr_node_id", "")
                .put("current_task", "")
                .put("blocking_reason", "")
                .put("owner_attention_required", false)
                .put("owner_attention_reason", "")
                .put("started_at", "")
                .put("last_activity_at", "")
                .put("evidence", new JSONArray())
                .put("read_only", true);
        if (assignment == null || marker == null || !marker.optBoolean("valid", false)) return status;

        String prId = assignment.optString("id", "");
        JSONObject attributes = assignment.optJSONObject("attributes");
        if (attributes == null) return status;
        status.put("status", marker.optString("status", "idle"))
                .put("role", marker.optString("role", roster.optString("role", "")))
                .put("issue_number", marker.optInt("issue_number", 0))
                .put("branch", attributes.optString("head_ref", ""))
                .put("pr_number", attributes.optInt("number", 0))
                .put("pr_node_id", prId)
                .put("current_task", marker.optString("current_task", ""))
                .put("blocking_reason", marker.optString("blocking_reason", ""))
                .put("owner_attention_required", marker.optBoolean("owner_attention_required", false))
                .put("owner_attention_reason", marker.optString("owner_attention_reason", ""))
                .put("started_at", marker.optString("started_at", ""))
                .put("last_activity_at", attributes.optString("updated_at", ""));
        addEvidence(status, "pr", prId, assignment.optString("url", ""));

        int issueNumber = marker.optInt("issue_number", 0);
        if (issueNumber > 0) {
            String repositoryId = repositoryId(prId);
            String issueId = repositoryId.isEmpty() ? "" : "github:issue:" + repositoryId + ":" + issueNumber;
            JSONObject issue = byId.get(issueId);
            if (issue != null) addEvidence(status, "issue", issueId, issue.optString("url", ""));
        }

        JSONObject latestRun = latestRelatedRun(prId, byId, relations);
        if (latestRun != null) {
            String runId = latestRun.optString("id", "");
            JSONObject runAttributes = latestRun.optJSONObject("attributes");
            String conclusion = runAttributes.optString("conclusion", "");
            String runStatus = runAttributes.optString("status", "");
            String ci = ciStatus(conclusion, runStatus);
            status.put("ci_status", ci);
            addEvidence(status, "workflow_run", runId, latestRun.optString("url", ""));
            if ("failing".equals(ci)) {
                status.put("status", "blocked");
                if (status.optString("blocking_reason", "").isEmpty()) {
                    status.put("blocking_reason", "CI failure · " + latestRun.optString("title", runId));
                }
            }
        }
        if (!agentId.equals(marker.optString("agent_id", ""))) {
            throw new IllegalStateException("AGENT_ID_PROJECTION_MISMATCH");
        }
        return status;
    }

    private static JSONObject latestRelatedRun(String prId, Map<String, JSONObject> byId,
                                               JSONArray relations) {
        JSONObject latest = null;
        String latestAt = "";
        for (int i = 0; i < relations.length(); i++) {
            JSONObject edge = relations.optJSONObject(i);
            if (edge == null || !prId.equals(edge.optString("from", ""))
                    || !"has_run".equals(edge.optString("type", ""))) continue;
            JSONObject run = byId.get(edge.optString("to", ""));
            if (run == null || !GitHubWorkContract.WORKFLOW_RUN.equals(run.optString("workType", ""))) continue;
            JSONObject attributes = run.optJSONObject("attributes");
            String at = attributes == null ? "" : attributes.optString("updated_at", "");
            if (latest == null || at.compareTo(latestAt) > 0) { latest = run; latestAt = at; }
        }
        return latest;
    }

    private static Map<String, JSONObject> latestAssignments(JSONArray nodes) {
        Map<String, JSONObject> out = new HashMap<>();
        Map<String, String> updated = new HashMap<>();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node == null || !GitHubWorkContract.PULL_REQUEST.equals(node.optString("workType", ""))) continue;
            JSONObject attributes = node.optJSONObject("attributes");
            JSONObject marker = attributes == null ? null : attributes.optJSONObject("agent_execution");
            if (marker == null || !marker.optBoolean("valid", false)) continue;
            String agentId = marker.optString("agent_id", "");
            String at = attributes.optString("updated_at", "");
            if (!out.containsKey(agentId) || at.compareTo(updated.get(agentId)) > 0) {
                out.put(agentId, node); updated.put(agentId, at);
            }
        }
        return out;
    }

    private static JSONObject agentNode(JSONObject status) throws Exception {
        String id = status.getString("agent_id");
        String state = status.getString("status");
        String detail = "role: " + status.optString("role", "")
                + "\nstatus: " + state
                + "\ncurrent_task: " + status.optString("current_task", "")
                + "\nissue: " + status.optInt("issue_number", 0)
                + "\npr: " + status.optInt("pr_number", 0)
                + "\nci: " + status.optString("ci_status", "unknown")
                + "\nblocker: " + status.optString("blocking_reason", "")
                + "\nowner_attention: " + status.optBoolean("owner_attention_required", false);
        return new JSONObject()
                .put("id", id)
                .put("title", status.optString("display_name", id))
                .put("summary", state + " · " + status.optString("current_task", ""))
                .put("detail", detail)
                .put("groupId", GROUP_ID)
                .put("domainId", "system:github")
                .put("entityType", "agent")
                .put("nodeType", "agent")
                .put("agentStatus", new JSONObject(status.toString()))
                .put("status", "blocked".equals(state) ? "blocked"
                        : ("working".equals(state) ? "active" : "pending"))
                .put("readOnly", true);
    }

    private static List<JSONObject> roster() throws Exception {
        List<JSONObject> out = new ArrayList<>();
        out.add(roster(AgentExecutionStatusContract.CLAUDE_CODE, "claude_code",
                "Claude Code", "UI / Product Engineer"));
        out.add(roster(AgentExecutionStatusContract.CODEX, "codex",
                "Codex", "Backend / Platform Engineer"));
        out.add(roster(AgentExecutionStatusContract.CHATGPT, "chatgpt",
                "ChatGPT", "Product Architect / Engineering Orchestrator"));
        return out;
    }

    private static JSONObject roster(String id, String type, String name, String role) throws Exception {
        return new JSONObject().put("agent_id", id).put("agent_type", type)
                .put("display_name", name).put("role", role);
    }

    private static Map<String, JSONObject> indexNodes(JSONArray nodes) {
        Map<String, JSONObject> out = new HashMap<>();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null) out.put(node.optString("id", ""), node);
        }
        return out;
    }

    private static void addEvidence(JSONObject status, String type, String ref, String url) throws Exception {
        if (ref == null || ref.isEmpty()) return;
        status.getJSONArray("evidence").put(new JSONObject()
                .put("source_type", type).put("source_ref", ref).put("url", url == null ? "" : url));
    }

    private static String ciStatus(String conclusion, String status) {
        String c = conclusion == null ? "" : conclusion.trim().toLowerCase();
        String s = status == null ? "" : status.trim().toLowerCase();
        if ("success".equals(c) || "neutral".equals(c) || "skipped".equals(c)) return "passing";
        if ("failure".equals(c) || "timed_out".equals(c) || "cancelled".equals(c)
                || "action_required".equals(c)) return "failing";
        if (c.isEmpty() && ("queued".equals(s) || "in_progress".equals(s) || "waiting".equals(s))) {
            return "pending";
        }
        return "unknown";
    }

    private static void ensureGroup(JSONObject out) throws Exception {
        JSONArray groups = out.optJSONArray("groups");
        if (groups == null) { groups = new JSONArray(); out.put("groups", groups); }
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group != null && GROUP_ID.equals(group.optString("id", ""))) return;
        }
        groups.put(new JSONObject().put("id", GROUP_ID).put("title", "AGENT")
                .put("domainId", "system:github")
                .put("summary", "GitHub-evidenced Agent work status"));
    }

    private static String repositoryId(String prId) {
        String[] parts = prId == null ? new String[0] : prId.split(":");
        return parts.length >= 4 && "github".equals(parts[0]) && "pr".equals(parts[1]) ? parts[2] : "";
    }

    private static String slug(String value) {
        return value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
