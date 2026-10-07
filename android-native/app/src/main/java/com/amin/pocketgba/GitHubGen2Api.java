package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub access for GEN2 runs. Limited to the OWNER account and the private
 * ken12121122-dotcom/gen2-knowledge repository: list open run Issues and post
 * a comment on one of them, start an approved run, read Workflow files on
 * main, and talk in the fox chat Issue. Nothing else can be reached through
 * this class.
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

    /**
     * Bridge 105: the fox chat Issue (label gen2-chat). The oldest open one is
     * the chat; the first time, the app opens it. Needs Issues: write.
     */
    int chatIssue() throws Exception {
        GitHubHttpResponse response = request("GET", REPOSITORY_PATH + "/issues?labels=" + Gen2FoxChat.LABEL + "&state=open&per_page=10&sort=created&direction=asc", "");
        JSONArray list = array(success(response, 200, "無法讀取狐狸聊天"));
        for (int i = 0; i < list.length(); i++) {
            JSONObject issue = list.optJSONObject(i);
            if (issue != null && !issue.has("pull_request") && issue.optInt("number", 0) > 0) return issue.optInt("number");
        }
        JSONObject body = new JSONObject().put("title", "🦊 狐狸聊天")
                .put("body", "OWNER 和狐狸的聊天室。狐狸由 Actions › GEN2 Fox 用 Claude 訂閱回覆，長期記憶在 fox-memory 分支。")
                .put("labels", new JSONArray().put(Gen2FoxChat.LABEL));
        return object(success(request("POST", REPOSITORY_PATH + "/issues", body.toString()), 201, "無法建立狐狸聊天")).optInt("number");
    }

    /** The latest chat comments (up to the last 200), oldest first. */
    JSONArray chatComments(int issue) throws Exception {
        if (issue <= 0) throw new IllegalArgumentException("Issue 編號無效。 ");
        int count = object(success(request("GET", REPOSITORY_PATH + "/issues/" + issue, ""), 200, "無法讀取狐狸聊天")).optInt("comments", 0);
        int last = Math.max(1, (count + 99) / 100);
        JSONArray out = new JSONArray();
        for (int page = Math.max(1, last - 1); page <= last; page++) {
            JSONArray batch = array(success(request("GET", REPOSITORY_PATH + "/issues/" + issue + "/comments?per_page=100&page=" + page, ""), 200, "無法讀取狐狸聊天"));
            for (int i = 0; i < batch.length(); i++) out.put(batch.get(i));
        }
        return out;
    }

    /** Posts the OWNER's words (or a /fox confirm|reject) to the chat; returns the comment id. */
    long postChat(int issue, String text) throws Exception {
        if (issue <= 0) throw new IllegalArgumentException("Issue 編號無效。 ");
        JSONObject body = new JSONObject().put("body", Gen2FoxChat.message(text));
        return object(success(request("POST", REPOSITORY_PATH + "/issues/" + issue + "/comments", body.toString()), 201, "訊息送出失敗")).optLong("id", 0L);
    }

    private static JSONArray array(GitHubHttpResponse response) {
        try { return new JSONArray(response.body()); }
        catch (Exception error) { throw new IllegalStateException("GitHub 回應格式不正確。 "); }
    }

    /** Starts a run of an approved Workflow (Actions › GEN2 Run Start on main). Needs Actions: write. */
    void startRun(String workflowId, String note) throws Exception {
        if (workflowId == null || !Gen2WorkflowCatalog.WORKFLOW_ID.matcher(workflowId).matches()) {
            throw new IllegalArgumentException("Workflow ID 無效。 ");
        }
        String text = note == null ? "" : note.trim();
        if (text.length() > Gen2ChatBrain.MAX_SCOPE) throw new IllegalArgumentException("範圍說明太長。 ");
        JSONObject body = new JSONObject().put("ref", "main")
                .put("inputs", new JSONObject().put("workflow_id", workflowId).put("note", text));
        GitHubHttpResponse response = request("POST", REPOSITORY_PATH + "/actions/workflows/gen2-run-start.yml/dispatches", body.toString());
        if (response.statusCode() == 403 || response.statusCode() == 404) {
            throw new SecurityException("GitHub App 沒有啟動流程的權限：請在 GitHub App 設定加上 Actions 的 Read and write，並在安裝頁接受新權限。 ");
        }
        success(response, 204, "無法啟動流程");
    }

    /** Workflows on main that the fox can start (files with a gen2-run block). Needs Contents: read. */
    List<Gen2WorkflowCatalog.Workflow> workflows() throws Exception {
        GitHubHttpResponse tree = request("GET", REPOSITORY_PATH + "/git/trees/main?recursive=1", "");
        if (tree.statusCode() == 403 || tree.statusCode() == 404) {
            throw new SecurityException("GitHub App 沒有讀取 gen2-knowledge 內容的權限（Contents：Read）。 ");
        }
        java.util.ArrayList<Gen2WorkflowCatalog.Workflow> out = new java.util.ArrayList<>();
        for (String path : Gen2WorkflowCatalog.workflowPaths(object(success(tree, 200, "無法讀取知識庫清單")))) {
            GitHubHttpResponse file = request("GET", REPOSITORY_PATH + "/contents/" + encodePath(path) + "?ref=main", "");
            if (file.statusCode() != 200) continue;
            String content = object(file).optString("content", ""); // base64 with line breaks; the MIME decoder accepts them
            try {
                String markdown = new String(java.util.Base64.getMimeDecoder().decode(content), java.nio.charset.StandardCharsets.UTF_8);
                Gen2WorkflowCatalog.Workflow workflow = Gen2WorkflowCatalog.parse(markdown);
                if (workflow != null) out.add(workflow);
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    static String encodePath(String path) throws Exception {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals("..") || part.equals(".")) throw new IllegalArgumentException("路徑無效。 ");
            if (out.length() > 0) out.append('/');
            out.append(java.net.URLEncoder.encode(part, "UTF-8").replace("+", "%20"));
        }
        return out.toString();
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
