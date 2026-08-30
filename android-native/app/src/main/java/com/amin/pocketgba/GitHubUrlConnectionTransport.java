package com.amin.pocketgba;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** HTTPS-only GitHub transport shared with the future authenticated control layer. */
final class GitHubUrlConnectionTransport implements GitHubHttpTransport {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList("github.com", "api.github.com"));

    @Override public GitHubHttpResponse execute(GitHubHttpRequest request) throws Exception {
        URL url = new URL(request.url());
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !HOSTS.contains(host)) {
            throw new SecurityException("GITHUB_HOST_NOT_ALLOWED");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setUseCaches(false);
        connection.setRequestMethod(request.method());
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        byte[] body = request.body().getBytes(StandardCharsets.UTF_8);
        if (body.length > 0) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
        }
        int status = connection.getResponseCode();
        if (status >= 300 && status < 400) {
            connection.disconnect();
            throw new SecurityException("GITHUB_REDIRECT_NOT_ALLOWED");
        }
        InputStream raw = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = raw == null ? "" : read(raw, request.maximumResponseBytes());
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() != null && header.getValue() != null && !header.getValue().isEmpty()) {
                responseHeaders.put(header.getKey(), header.getValue().get(0));
            }
        }
        connection.disconnect();
        return new GitHubHttpResponse(status, responseBody, responseHeaders);
    }

    private static String read(InputStream raw, int maximumBytes) throws Exception {
        try (InputStream input = new BufferedInputStream(raw);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maximumBytes) throw new SecurityException("GITHUB_RESPONSE_TOO_LARGE");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
