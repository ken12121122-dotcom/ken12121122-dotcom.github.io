package com.amin.pocketgba;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;

public final class ExecutionResult {
    private final String requestId;
    private final boolean success;
    private final String code;
    private final String message;
    private final String action;
    private final String source;
    private final String startedAt;
    private final String finishedAt;
    private final JSONObject data;

    private ExecutionResult(
            String requestId,
            boolean success,
            String code,
            String message,
            String action,
            String source,
            String startedAt,
            String finishedAt,
            JSONObject data
    ) {
        this.requestId = requestId;
        this.success = success;
        this.code = code;
        this.message = message;
        this.action = action;
        this.source = source;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.data = data == null ? new JSONObject() : data;
    }

    public static ExecutionResult success(AminAction action, String startedAt, String message) {
        return new ExecutionResult(
                action.getRequestId(), true, "OK", message, action.getAction(), action.getSource(),
                startedAt, Instant.now().toString(), new JSONObject()
        );
    }

    public static ExecutionResult failure(AminAction action, String startedAt, String code, String message) {
        return new ExecutionResult(
                action == null ? "" : action.getRequestId(), false, code, message,
                action == null ? "" : action.getAction(),
                action == null ? "unknown" : action.getSource(),
                startedAt, Instant.now().toString(), new JSONObject()
        );
    }

    public static ExecutionResult timeout(AminAction action, String startedAt) {
        return failure(action, startedAt, "EXECUTION_TIMEOUT", "Action execution timed out");
    }

    public String getRequestId() { return requestId; }
    public boolean isSuccess() { return success; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getAction() { return action; }
    public String getSource() { return source; }
    public String getStartedAt() { return startedAt; }
    public String getFinishedAt() { return finishedAt; }
    public JSONObject getData() { return data; }

    public JSONObject toJsonObject() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("requestId", requestId);
            payload.put("success", success);
            payload.put("code", code);
            payload.put("message", message);
            payload.put("action", action);
            payload.put("source", source);
            payload.put("startedAt", startedAt);
            payload.put("finishedAt", finishedAt);
            payload.put("data", data);
        } catch (JSONException error) {
            throw new IllegalStateException("Unable to serialize execution result", error);
        }
        return payload;
    }

    public String toJson() { return toJsonObject().toString(); }
}
