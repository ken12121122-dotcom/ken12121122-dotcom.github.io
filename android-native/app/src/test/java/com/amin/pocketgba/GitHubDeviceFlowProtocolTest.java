package com.amin.pocketgba;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitHubDeviceFlowProtocolTest {
    @Test
    public void parsesDeviceCodeAndNeverRequiresClientSecret() {
        GitHubDeviceFlowProtocol.DeviceCode code = GitHubDeviceFlowProtocol.parseDeviceCode(
                new JSONObject().put("device_code", "device-token").put("user_code", "ABCD-EFGH")
                        .put("verification_uri", "https://github.com/login/device")
                        .put("expires_in", 900).put("interval", 5), 1000L);
        assertEquals("ABCD-EFGH", code.userCode());
        assertEquals("https://github.com/login/device", code.verificationUri());
        assertEquals(5, code.intervalSeconds());
        assertFalse(code.json().has("client_secret"));
    }

    @Test
    public void handlesPendingSlowDownDeniedExpiredAndSuccess() {
        assertEquals(GitHubDeviceFlowProtocol.PollStatus.PENDING,
                GitHubDeviceFlowProtocol.parsePoll(new JSONObject().put("error", "authorization_pending"), 5)
                        .status());
        GitHubDeviceFlowProtocol.Poll slow = GitHubDeviceFlowProtocol.parsePoll(
                new JSONObject().put("error", "slow_down"), 5);
        assertEquals(GitHubDeviceFlowProtocol.PollStatus.PENDING, slow.status());
        assertEquals(10, slow.nextIntervalSeconds());
        assertEquals(GitHubDeviceFlowProtocol.PollStatus.DENIED,
                GitHubDeviceFlowProtocol.parsePoll(new JSONObject().put("error", "access_denied"), 5)
                        .status());
        assertEquals(GitHubDeviceFlowProtocol.PollStatus.EXPIRED,
                GitHubDeviceFlowProtocol.parsePoll(new JSONObject().put("error", "expired_token"), 5)
                        .status());
        GitHubDeviceFlowProtocol.Poll success = GitHubDeviceFlowProtocol.parsePoll(new JSONObject()
                .put("access_token", "token").put("token_type", "bearer").put("expires_in", 28800)
                .put("refresh_token", "refresh").put("refresh_token_expires_in", 15811200), 5);
        assertEquals(GitHubDeviceFlowProtocol.PollStatus.SUCCESS, success.status());
        assertTrue(success.token().expiresAtMillis() > System.currentTimeMillis());
    }
}
