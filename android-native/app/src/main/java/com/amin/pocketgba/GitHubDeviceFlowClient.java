package com.amin.pocketgba;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class GitHubDeviceFlowClient {
    private static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_.-]{8,128}");
    private final GitHubHttpTransport transport;
    private final String clientId;

    GitHubDeviceFlowClient(GitHubHttpTransport transport, String clientId) {
        this.transport = transport;
        this.clientId = clientId == null ? "" : clientId.trim();
        if (transport == null || !CLIENT_ID.matcher(this.clientId).matches()) {
            throw new IllegalArgumentException("GitHub App client ID 尚未設定。 ");
        }
    }

    GitHubDeviceFlowProtocol.DeviceCode requestCode() throws Exception {
        GitHubHttpResponse response = transport.execute(request(DEVICE_CODE_URL,
                "client_id=" + form(clientId)));
        if (response.statusCode() != 200) throw status("無法取得 GitHub 裝置驗證碼", response);
        return GitHubDeviceFlowProtocol.parseDeviceCode(new JSONObject(response.body()),
                System.currentTimeMillis());
    }

    GitHubDeviceFlowProtocol.Poll poll(GitHubDeviceFlowProtocol.DeviceCode code,
                                       int currentIntervalSeconds) throws Exception {
        if (code == null || System.currentTimeMillis() >= code.expiresAtMillis()) {
            return new GitHubDeviceFlowProtocol.Poll(GitHubDeviceFlowProtocol.PollStatus.EXPIRED,
                    currentIntervalSeconds, null, "expired_token");
        }
        String body = "client_id=" + form(clientId)
                + "&device_code=" + form(code.deviceCode())
                + "&grant_type=" + form("urn:ietf:params:oauth:grant-type:device_code");
        GitHubHttpResponse response = transport.execute(request(TOKEN_URL, body));
        if (response.statusCode() != 200) throw status("GitHub 裝置授權查詢失敗", response);
        return GitHubDeviceFlowProtocol.parsePoll(new JSONObject(response.body()), currentIntervalSeconds);
    }

    GitHubDeviceFlowProtocol.Token refresh(String refreshToken) throws Exception {
        if (refreshToken == null || refreshToken.isBlank()) throw new IllegalArgumentException("Refresh token 不可為空。 ");
        String body = "client_id=" + form(clientId)
                + "&grant_type=" + form("refresh_token")
                + "&refresh_token=" + form(refreshToken);
        GitHubHttpResponse response = transport.execute(request(TOKEN_URL, body));
        if (response.statusCode() != 200) throw status("GitHub 登入續期失敗", response);
        GitHubDeviceFlowProtocol.Poll parsed = GitHubDeviceFlowProtocol.parsePoll(
                new JSONObject(response.body()), 5);
        if (parsed.status() != GitHubDeviceFlowProtocol.PollStatus.SUCCESS) {
            throw new IllegalStateException("GitHub 登入續期被拒絕。 ");
        }
        return parsed.token();
    }

    private static GitHubHttpRequest request(String url, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("User-Agent", "Amin-Pocket-Brain/1");
        return new GitHubHttpRequest("POST", url, headers, body, 256 * 1024);
    }

    private static String form(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static IllegalStateException status(String message, GitHubHttpResponse response) {
        return new IllegalStateException(message + "（HTTP " + response.statusCode() + "）");
    }
}
