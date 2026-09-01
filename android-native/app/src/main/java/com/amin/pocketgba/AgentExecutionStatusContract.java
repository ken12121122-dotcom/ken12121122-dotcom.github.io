package com.amin.pocketgba;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executable, read-only contract for GitHub-evidenced Agent work status.
 *
 * The marker identifies ownership and the Agent's declared workflow state. CI is deliberately
 * excluded: AgentStatusProjector derives it from authored GitHub Work relations instead.
 */
final class AgentExecutionStatusContract {
    static final String SCHEMA_VERSION = "agent-execution-status-v1";
    static final String CODEX = "agent:codex";
    static final String CLAUDE_CODE = "agent:claude-code";
    static final String CHATGPT = "agent:chatgpt";

    private static final int MAX_MARKER_BYTES = 4096;
    private static final Pattern MARKER = Pattern.compile(
            "<!--\\s*amin-agent-execution\\s*(\\{.*?\\})\\s*-->", Pattern.DOTALL);
    private static final Pattern AGENT_ID = Pattern.compile("agent:[a-z0-9][a-z0-9._-]{1,48}");
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            "claude_code", "codex", "chatgpt", "other"));
    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList(
            "working", "waiting", "blocked", "idle", "review"));

    private AgentExecutionStatusContract() { }

    static JSONObject parsePullRequestBody(String body) {
        String source = body == null ? "" : body;
        Matcher matcher = MARKER.matcher(source);
        if (!matcher.find()) return new JSONObject();
        String raw = matcher.group(1);
        if (matcher.find()) return invalid("AGENT_MARKER_AMBIGUOUS");
        if (raw == null || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_MARKER_BYTES) {
            return invalid("AGENT_MARKER_TOO_LARGE");
        }
        try {
            return normalize(new JSONObject(raw));
        } catch (IllegalArgumentException error) {
            return invalid(error.getMessage() == null ? "AGENT_MARKER_INVALID" : error.getMessage());
        } catch (Exception error) {
            // Parser messages can contain excerpts from the PR body. Never persist them.
            return invalid("AGENT_MARKER_INVALID");
        }
    }

    static JSONObject normalize(JSONObject input) throws Exception {
        if (input == null) throw new IllegalArgumentException("AGENT_MARKER_REQUIRED");
        String agentId = clean(input.optString("agent_id", ""));
        String agentType = clean(input.optString("agent_type", "")).toLowerCase();
        String status = clean(input.optString("status", "")).toLowerCase();
        String role = bounded(input.optString("role", ""), 120, "AGENT_ROLE_TOO_LONG");
        String currentTask = bounded(input.optString("current_task", ""), 240,
                "AGENT_TASK_TOO_LONG");
        String blockingReason = bounded(input.optString("blocking_reason", ""), 320,
                "AGENT_BLOCKER_TOO_LONG");
        String attentionReason = bounded(input.optString("owner_attention_reason", ""), 320,
                "OWNER_ATTENTION_REASON_TOO_LONG");
        int issueNumber = input.optInt("issue_number", 0);
        boolean ownerAttention = input.optBoolean("owner_attention_required", false);

        if (!AGENT_ID.matcher(agentId).matches()) throw new IllegalArgumentException("INVALID_AGENT_ID");
        if (!TYPES.contains(agentType)) throw new IllegalArgumentException("INVALID_AGENT_TYPE");
        if ((CODEX.equals(agentId) && !"codex".equals(agentType))
                || (CLAUDE_CODE.equals(agentId) && !"claude_code".equals(agentType))
                || (CHATGPT.equals(agentId) && !"chatgpt".equals(agentType))) {
            throw new IllegalArgumentException("AGENT_ID_TYPE_MISMATCH");
        }
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("INVALID_AGENT_STATUS");
        if (role.isEmpty()) throw new IllegalArgumentException("AGENT_ROLE_REQUIRED");
        if (currentTask.isEmpty() && !"idle".equals(status)) {
            throw new IllegalArgumentException("CURRENT_TASK_REQUIRED");
        }
        if (issueNumber < 0) throw new IllegalArgumentException("INVALID_ISSUE_NUMBER");
        if (ownerAttention && attentionReason.isEmpty()) {
            throw new IllegalArgumentException("OWNER_ATTENTION_REASON_REQUIRED");
        }
        if ("blocked".equals(status) && blockingReason.isEmpty()) {
            throw new IllegalArgumentException("BLOCKING_REASON_REQUIRED");
        }

        return new JSONObject()
                .put("schema_version", SCHEMA_VERSION)
                .put("valid", true)
                .put("agent_id", agentId)
                .put("agent_type", agentType)
                .put("role", role)
                .put("issue_number", issueNumber)
                .put("current_task", currentTask)
                .put("status", status)
                .put("blocking_reason", blockingReason)
                .put("owner_attention_required", ownerAttention)
                .put("owner_attention_reason", attentionReason)
                .put("started_at", bounded(input.optString("started_at", ""), 64,
                        "STARTED_AT_TOO_LONG"))
                .put("read_only", true);
    }

    private static JSONObject invalid(String error) {
        try {
            return new JSONObject().put("schema_version", SCHEMA_VERSION)
                    .put("valid", false).put("error", clean(error)).put("read_only", true);
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static String bounded(String value, int max, String error) {
        String clean = clean(value);
        if (clean.length() > max) throw new IllegalArgumentException(error);
        return clean;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
