package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class BrainTaskEnvelope {
    static final String CONTROL_REPOSITORY = "ken12121122-dotcom/amin-agent-control";
    static final String TARGET_REPOSITORY = "ken12121122-dotcom/ken12121122-dotcom.github.io";
    static final String TARGET_BASE_BRANCH = "release/android";
    static final String ALLOWED_PATH_PREFIX = "android-native/";

    private static final Pattern CAPABILITY = Pattern.compile(
            "[a-z][a-z0-9._-]{0,39}:[a-z0-9][a-z0-9._-]{0,39}");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(OPENAI_API_KEY|ANTHROPIC_API_KEY|(?:api[_-]?key|access[_-]?token|client[_-]?secret)\\s*[:=]|"
                    + "Bearer\\s+[A-Za-z0-9._~-]{8,}|gh[pousr]_[A-Za-z0-9_]{8,}|github_pat_|"
                    + "sk-(?:ant|proj)-|-----BEGIN [A-Z ]*PRIVATE KEY-----)");

    private final JSONObject json;

    private BrainTaskEnvelope(JSONObject json) {
        this.json = new JSONObject(json.toString());
    }

    static BrainTaskEnvelope create(UUID goalId, UUID taskId, UUID clientNonce,
                                    String title, String specification, String requestedAgent,
                                    List<String> requiredCapabilities, int maximumAttempts,
                                    Instant createdAt) {
        if (goalId == null || taskId == null || clientNonce == null || createdAt == null) {
            throw new IllegalArgumentException("任務識別與建立時間不可為空。 ");
        }
        String safeTitle = required(title, "任務標題", 160);
        String safeSpecification = required(specification, "任務內容", 20_000);
        if (SECRET.matcher(safeTitle + "\n" + safeSpecification).find()) {
            throw new IllegalArgumentException("任務內容疑似包含金鑰或權杖，已阻止發布。 ");
        }
        String agent = requestedAgent == null ? "" : requestedAgent.trim().toLowerCase();
        if (!("auto".equals(agent) || "codex".equals(agent) || "claude".equals(agent))) {
            throw new IllegalArgumentException("不支援的 Agent 選項。 ");
        }
        if (maximumAttempts < 1 || maximumAttempts > 2) {
            throw new IllegalArgumentException("最多只能執行兩次。 ");
        }
        if (requiredCapabilities == null || requiredCapabilities.size() > 32) {
            throw new IllegalArgumentException("能力需求數量不正確。 ");
        }
        JSONArray capabilities = new JSONArray();
        Set<String> unique = new HashSet<>();
        for (String raw : requiredCapabilities) {
            String capability = raw == null ? "" : raw.trim();
            if (!CAPABILITY.matcher(capability).matches() || !unique.add(capability)) {
                throw new IllegalArgumentException("能力識別格式不正確或重複。 ");
            }
            capabilities.put(capability);
        }
        JSONObject target = new JSONObject()
                .put("repository", TARGET_REPOSITORY)
                .put("base_branch", TARGET_BASE_BRANCH)
                .put("allowed_path_prefixes", new JSONArray().put(ALLOWED_PATH_PREFIX));
        JSONObject value = new JSONObject()
                .put("format", "amin-brain-task-request")
                .put("version", 1)
                .put("goal_id", goalId.toString())
                .put("task_id", taskId.toString())
                .put("title", safeTitle)
                .put("specification", safeSpecification)
                .put("target", target)
                .put("requested_agent", agent)
                .put("required_capabilities", capabilities)
                .put("max_attempts", maximumAttempts)
                .put("created_at", createdAt.toString())
                .put("client_nonce", clientNonce.toString());
        return new BrainTaskEnvelope(value);
    }

    JSONObject json() { return new JSONObject(json.toString()); }
    String title() { return json.getString("title"); }
    String taskUuid() { return json.getString("task_id"); }
    String goalUuid() { return json.getString("goal_id"); }

    String issueBody() {
        return "<!-- amin-brain-task:v1 -->\n```json\n" + json.toString(2) + "\n```";
    }

    private static String required(String value, String label, int maximum) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > maximum) {
            throw new IllegalArgumentException(label + "不可為空或超過長度。 ");
        }
        return clean;
    }
}
