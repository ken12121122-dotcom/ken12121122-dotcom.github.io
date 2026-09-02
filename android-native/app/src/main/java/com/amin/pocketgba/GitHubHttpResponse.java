package com.amin.pocketgba;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class GitHubHttpResponse {
    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;

    GitHubHttpResponse(int statusCode, String body, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    int statusCode() { return statusCode; }
    String body() { return body; }
    String header(String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return "";
    }
}
