package com.amin.pocketgba;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class AminAction {
    private final String action;
    private final JSONObject parameters;
    private final String source;
    private final double confidence;
    private final String requestId;
    private final String createdAt;

    public AminAction(String action, JSONObject parameters, String source, double confidence) {
        this(action, parameters, source, confidence, UUID.randomUUID().toString(), Instant.now().toString());
    }

    AminAction(String action, JSONObject parameters, String source, double confidence, String requestId, String createdAt) {
        this.action = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        this.parameters = parameters == null ? new JSONObject() : parameters;
        this.source = source == null ? "unknown" : source;
        this.confidence = Math.max(0d, Math.min(1d, confidence));
        this.requestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        this.createdAt = createdAt == null || createdAt.isBlank() ? Instant.now().toString() : createdAt;
    }

    public String getAction() { return action; }
    public JSONObject getParameters() { return parameters; }
    public String getSource() { return source; }
    public double getConfidence() { return confidence; }
    public String getRequestId() { return requestId; }
    public String getCreatedAt() { return createdAt; }

    public JSONObject toJsonObject() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("action", action);
            payload.put("parameters", parameters);
            payload.put("source", source);
            payload.put("confidence", confidence);
            payload.put("requestId", requestId);
            payload.put("createdAt", createdAt);
        } catch (JSONException error) {
            throw new IllegalStateException("Unable to serialize Amin action", error);
        }
        return payload;
    }

    public String toJson() {
        return toJsonObject().toString();
    }
}
