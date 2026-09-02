package com.amin.pocketgba;

import android.content.Context;

final class BrainAuthSession {
    private final BrainTokenVault vault;
    private final String clientId;

    BrainAuthSession(Context context) {
        vault = new BrainTokenVault(context);
        clientId = BuildConfig.AMIN_GITHUB_APP_CLIENT_ID == null
                ? "" : BuildConfig.AMIN_GITHUB_APP_CLIENT_ID.trim();
    }

    boolean isConfigured() { return !clientId.isEmpty(); }

    boolean hasSession() {
        GitHubDeviceFlowProtocol.Token token = vault.load();
        long now = System.currentTimeMillis();
        return token != null && (token.accessUsable(now) || token.refreshUsable(now));
    }

    GitHubBrainApi requireVerifiedApi() throws Exception {
        GitHubDeviceFlowProtocol.Token token = requireToken();
        GitHubBrainApi api = new GitHubBrainApi(new GitHubUrlConnectionTransport(), token.accessToken());
        api.verifySession();
        return api;
    }

    void verifyAndSave(GitHubDeviceFlowProtocol.Token token) throws Exception {
        if (token == null) throw new IllegalArgumentException("GitHub 登入資料不可為空。 ");
        GitHubBrainApi api = new GitHubBrainApi(new GitHubUrlConnectionTransport(), token.accessToken());
        api.verifySession();
        vault.save(token);
    }

    GitHubDeviceFlowClient deviceFlowClient() {
        return new GitHubDeviceFlowClient(new GitHubUrlConnectionTransport(), clientId);
    }

    void logout() { vault.clear(); }

    private GitHubDeviceFlowProtocol.Token requireToken() throws Exception {
        GitHubDeviceFlowProtocol.Token token = vault.load();
        long now = System.currentTimeMillis();
        if (token == null) throw new SecurityException("尚未連結 GitHub。 ");
        if (token.accessUsable(now)) return token;
        if (!token.refreshUsable(now) || !isConfigured()) {
            vault.clear();
            throw new SecurityException("GitHub 登入已過期，請重新連結。 ");
        }
        try {
            GitHubDeviceFlowProtocol.Token refreshed = deviceFlowClient().refresh(token.refreshToken());
            verifyAndSave(refreshed);
            return refreshed;
        } catch (Exception error) {
            vault.clear();
            throw error;
        }
    }
}
