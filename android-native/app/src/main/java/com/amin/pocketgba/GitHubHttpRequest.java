package com.amin.pocketgba;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class GitHubHttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;
    private final int maximumResponseBytes;

    GitHubHttpRequest(String method, String url, Map<String, String> headers,
                      String body, int maximumResponseBytes) {
        this.method = method;
        this.url = url;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.body = body == null ? "" : body;
        this.maximumResponseBytes = maximumResponseBytes;
    }

    String method() { return method; }
    String url() { return url; }
    Map<String, String> headers() { return headers; }
    String body() { return body; }
    int maximumResponseBytes() { return maximumResponseBytes; }
}
