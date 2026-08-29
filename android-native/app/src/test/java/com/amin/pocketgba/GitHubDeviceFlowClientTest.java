package com.amin.pocketgba;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitHubDeviceFlowClientTest {
    @Test
    public void requestsAndPollsWithPublicClientIdButNoSecret() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.responses.add(new GitHubHttpResponse(200, new JSONObject()
                .put("device_code", "device").put("user_code", "ABCD-EFGH")
                .put("verification_uri", "https://github.com/login/device")
                .put("expires_in", 900).put("interval", 5).toString(), Map.of()));
        transport.responses.add(new GitHubHttpResponse(200,
                new JSONObject().put("error", "authorization_pending").toString(), Map.of()));
        GitHubDeviceFlowClient client = new GitHubDeviceFlowClient(transport, "Iv1.public-client-id");

        GitHubDeviceFlowProtocol.DeviceCode code = client.requestCode();
        GitHubDeviceFlowProtocol.Poll poll = client.poll(code, code.intervalSeconds());
        assertEquals(GitHubDeviceFlowProtocol.PollStatus.PENDING, poll.status());
        assertEquals("https://github.com/login/device/code", transport.requests.get(0).url());
        assertEquals("https://github.com/login/oauth/access_token", transport.requests.get(1).url());
        String allBodies = transport.requests.get(0).body() + transport.requests.get(1).body();
        assertTrue(allBodies.contains("client_id=Iv1.public-client-id"));
        assertFalse(allBodies.contains("client_secret"));
    }

    private static final class FakeTransport implements GitHubHttpTransport {
        final List<GitHubHttpRequest> requests = new ArrayList<>();
        final List<GitHubHttpResponse> responses = new ArrayList<>();
        @Override public GitHubHttpResponse execute(GitHubHttpRequest request) {
            requests.add(request);
            return responses.remove(0);
        }
    }
}
