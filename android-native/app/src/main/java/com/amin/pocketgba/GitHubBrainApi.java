package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

final class GitHubBrainApi {
    static final String OWNER = "ken12121122-dotcom";
    static final String CONTROL_REPOSITORY = "ken12121122-dotcom/amin-agent-control";
    private static final String API = "https://api.github.com";
    private static final String REPOSITORY_PATH = "/repos/" + CONTROL_REPOSITORY;
    private final GitHubHttpTransport transport;
    private final String token;

    GitHubBrainApi(GitHubHttpTransport transport, String token) {
        this.transport = transport;
        this.token = token == null ? "" : token.trim();
        if (transport == null || this.token.isEmpty()) throw new IllegalArgumentException("GitHub 登入權杖不可為空。 ");
    }

    void verifySession() throws Exception {
        JSONObject user = object(success(request("GET", "/user", "", "application/vnd.github+json"), 200,
                "無法驗證 GitHub 使用者"));
        if (!OWNER.equals(user.optString("login", ""))) throw new SecurityException("GitHub 登入帳號不是指定擁有者。 ");
        JSONObject repository = object(success(request("GET", REPOSITORY_PATH, "",
                "application/vnd.github+json"), 200, "無法讀取私人控制 Repo"));
        if (!CONTROL_REPOSITORY.equals(repository.optString("full_name", ""))
                || !repository.optBoolean("private", false)) {
            throw new SecurityException("控制 Repo 身分或可見性不符合要求。 ");
        }
    }

    Issue createIssue(BrainTaskEnvelope envelope) throws Exception {
        if (envelope == null) throw new IllegalArgumentException("任務不可為空。 ");
        JSONObject body = new JSONObject().put("title", envelope.title()).put("body", envelope.issueBody());
        JSONObject response = object(success(request("POST", REPOSITORY_PATH + "/issues", body.toString(),
                "application/vnd.github+json"), 201, "私人任務發布失敗"));
        int number = response.optInt("number", 0);
        String url = response.optString("html_url", "");
        if (number <= 0 || url.trim().isEmpty()) throw new IllegalStateException("GitHub 未回傳有效 Issue。 ");
        return new Issue(number, url);
    }

    void approveIssue(int issueNumber) throws Exception {
        replaceActionLabel(issueNumber, "agent-approved", "私人任務批准失敗");
    }

    void cancelIssue(int issueNumber) throws Exception {
        replaceActionLabel(issueNumber, "agent-cancel", "私人任務取消失敗");
    }

    private void replaceActionLabel(int issueNumber, String label, String message) throws Exception {
        if (issueNumber <= 0) throw new IllegalArgumentException("Issue 編號無效。 ");
        GitHubHttpResponse removed = request("DELETE", REPOSITORY_PATH + "/issues/" + issueNumber
                + "/labels/" + URLEncoder.encode(label, "UTF-8"), "", "application/vnd.github+json");
        if (!(removed.statusCode() == 200 || removed.statusCode() == 204 || removed.statusCode() == 404)) {
            throw new IllegalStateException(message + "（HTTP " + removed.statusCode() + "）");
        }
        JSONObject body = new JSONObject().put("labels", new JSONArray().put(label));
        success(request("POST", REPOSITORY_PATH + "/issues/" + issueNumber + "/labels",
                body.toString(), "application/vnd.github+json"), 200, message);
    }

    FeedResponse fetchMobileFeed(String etag) throws Exception {
        String path = REPOSITORY_PATH + "/contents/"
                + URLEncoder.encode("work-graph/v1/mobile-feed.json", "UTF-8").replace("%2F", "/")
                + "?ref=main";
        Map<String, String> additional = new LinkedHashMap<>();
        if (etag != null && !etag.trim().isEmpty()) additional.put("If-None-Match", etag);
        GitHubHttpResponse response = request("GET", path, "", "application/vnd.github.raw+json", additional);
        if (response.statusCode() == 304) return new FeedResponse(false, etag == null ? "" : etag, null);
        success(response, 200, "無法讀取 Brain 狀態");
        JSONObject feed = object(response);
        BrainFeedState.diff(feed, java.util.Collections.emptySet());
        return new FeedResponse(true, response.header("ETag"), feed);
    }

    private GitHubHttpResponse request(String method, String path, String body, String accept) throws Exception {
        return request(method, path, body, accept, Collections.emptyMap());
    }

    private GitHubHttpResponse request(String method, String path, String body, String accept,
                                       Map<String, String> additional) throws Exception {
        if (!(path.equals("/user") || path.equals(REPOSITORY_PATH) || path.startsWith(REPOSITORY_PATH + "/"))) {
            throw new SecurityException("GitHub API 路徑超出控制 Repo。 ");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", accept);
        headers.put("Authorization", "Bearer " + token);
        headers.put("X-GitHub-Api-Version", "2022-11-28");
        headers.put("User-Agent", "Amin-Pocket-Brain/1");
        if (!body.isEmpty()) headers.put("Content-Type", "application/json; charset=utf-8");
        headers.putAll(additional);
        return transport.execute(new GitHubHttpRequest(method, API + path, headers, body, 2 * 1024 * 1024));
    }

    private static GitHubHttpResponse success(GitHubHttpResponse response, int expected, String message) {
        if (response.statusCode() != expected) {
            throw new IllegalStateException(message + "（HTTP " + response.statusCode() + "）");
        }
        return response;
    }

    private static JSONObject object(GitHubHttpResponse response) {
        try { return new JSONObject(response.body()); }
        catch (Exception error) { throw new IllegalStateException("GitHub 回應格式不正確。 "); }
    }

    static final class Issue {
        private final int number;
        private final String url;
        Issue(int number, String url) { this.number = number; this.url = url; }
        int number() { return number; }
        String url() { return url; }
    }

    static final class FeedResponse {
        private final boolean changed;
        private final String etag;
        private final JSONObject feed;
        FeedResponse(boolean changed, String etag, JSONObject feed) {
            this.changed = changed; this.etag = etag == null ? "" : etag;
            this.feed = BrainJson.copy(feed);
        }
        boolean changed() { return changed; }
        String etag() { return etag; }
        JSONObject feed() { return BrainJson.copy(feed); }
    }
}
