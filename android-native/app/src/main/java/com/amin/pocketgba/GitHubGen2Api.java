package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub access for GEN2 runs. Limited to the OWNER account and the private
 * ken12121122-dotcom/gen2-knowledge repository: list open run Issues and post
 * a comment on one of them. Nothing else can be reached through this class.
 */
final class GitHubGen2Api {
    static final String REPOSITORY = "ken12121122-dotcom/gen2-knowledge";
    private static final String API = "https://api.github.com";
    private static final String REPOSITORY_PATH = "/repos/" + REPOSITORY;
    private final GitHubHttpTransport transport;
    private final String token;

    GitHubGen2Api(GitHubHttpTransport transport, String token) {
        this.transport = transport;
        this.token = token == null ? "" : token.trim();
        if (transport == null || this.token.isEmpty()) throw new IllegalArgumentException("GitHub 登入權杖不可為空。 ");
    }

    void verifySession() throws Exception {
        JSONObject user = object(success(request("GET", "/user", ""), 200, "無法驗證 GitHub 使用者"));
        if (!GitHubBrainApi.OWNER.equals(user.optString("login", ""))) {
            throw new SecurityException("GitHub 登入帳號不是指定擁有者。 ");
        }
        GitHubHttpResponse repo = request("GET", REPOSITORY_PATH, "");
        if (repo.statusCode() == 404) {
            throw new SecurityException("GitHub App 尚未授權 gen2-knowledge：請在 GitHub App 安裝設定中加入這個 repo。 ");
        }
        JSONObject repository = object(success(repo, 200, "無法讀取 gen2-knowledge"));
        if (!REPOSITORY.equals(repository.optString("full_name", "")) || !repository.optBoolean("private", false)) {
            throw new SecurityException("gen2-knowledge 身分或可見性不符合要求。 ");
        }
    }

    List<Gen2RunView> openRuns() throws Exception {
        GitHubHttpResponse response = request("GET",
                REPOSITORY_PATH + "/issues?labels=gen2-run&state=open&per_page=50&sort=updated", "");
        success(response, 200, "無法讀取 GEN2 執行清單");
        try {
            return Gen2RunView.fromIssues(new JSONArray(response.body()));
        } catch (Exception error) {
            throw new IllegalStateException("GitHub 回應格式不正確。 ");
        }
    }

    void postCommand(int issueNumber, String command) throws Exception {
        if (issueNumber <= 0) throw new IllegalArgumentException("Issue 編號無效。 ");
        if (command == null || !command.startsWith("/gen2 ")) throw new IllegalArgumentException("指令格式不正確。 ");
        JSONObject body = new JSONObject().put("body", command);
        success(request("POST", REPOSITORY_PATH + "/issues/" + issueNumber + "/comments", body.toString()),
                201, "指令送出失敗");
    }

    private GitHubHttpResponse request(String method, String path, String body) throws Exception {
        if (!(path.equals("/user") || path.equals(REPOSITORY_PATH) || path.startsWith(REPOSITORY_PATH + "/"))) {
            throw new SecurityException("GitHub API 路徑超出 gen2-knowledge。 ");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        headers.put("Authorization", "Bearer " + token);
        headers.put("X-GitHub-Api-Version", "2022-11-28");
        headers.put("User-Agent", "Amin-Pocket-Gen2/1");
        if (!body.isEmpty()) headers.put("Content-Type", "application/json; charset=utf-8");
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
}
