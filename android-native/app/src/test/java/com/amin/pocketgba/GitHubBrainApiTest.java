package com.amin.pocketgba;

import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitHubBrainApiTest {
    @Test
    public void verifiesExactOwnerAndPrivateRepositoryBeforeWrites() throws Exception {
        FakeTransport transport = new FakeTransport()
                .reply(200, "{\"login\":\"ken12121122-dotcom\"}")
                .reply(200, "{\"full_name\":\"ken12121122-dotcom/amin-agent-control\",\"private\":true}");
        GitHubBrainApi api = new GitHubBrainApi(transport, "owner-token");
        api.verifySession();
        assertEquals("https://api.github.com/user", transport.requests.get(0).url());
        assertEquals("Bearer owner-token", transport.requests.get(0).headers().get("Authorization"));
        assertTrue(transport.requests.get(1).url().endsWith("/repos/ken12121122-dotcom/amin-agent-control"));
    }

    @Test
    public void publishesIssueThenApprovesWithSeparateLabelRequest() throws Exception {
        FakeTransport transport = new FakeTransport()
                .reply(201, "{\"number\":17,\"html_url\":\"https://github.com/private/issues/17\"}")
                .reply(404, "{}")
                .reply(200, "[{\"name\":\"agent-approved\"}]");
        GitHubBrainApi api = new GitHubBrainApi(transport, "owner-token");
        BrainTaskEnvelope envelope = BrainTaskEnvelope.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "任務", "建立任務畫面。", "auto", List.of("android:ui"), 1, Instant.now());
        GitHubBrainApi.Issue created = api.createIssue(envelope);
        api.approveIssue(created.number());

        assertEquals("POST", transport.requests.get(0).method());
        assertTrue(transport.requests.get(0).url().endsWith("/issues"));
        assertTrue(transport.requests.get(0).body().contains("amin-brain-task:v1"));
        assertEquals("DELETE", transport.requests.get(1).method());
        assertTrue(transport.requests.get(1).url().endsWith("/issues/17/labels/agent-approved"));
        assertTrue(transport.requests.get(2).url().endsWith("/issues/17/labels"));
        assertTrue(transport.requests.get(2).body().contains("agent-approved"));
    }

    @Test
    public void ownerCanSendSeparateAuditableCancellationLabel() throws Exception {
        FakeTransport transport = new FakeTransport().reply(404, "{}")
                .reply(200, "[{\"name\":\"agent-cancel\"}]");
        new GitHubBrainApi(transport, "owner-token").cancelIssue(17);
        assertEquals("DELETE", transport.requests.get(0).method());
        assertTrue(transport.requests.get(1).body().contains("agent-cancel"));
    }

    @Test
    public void conditionalFeedReadTreats304AsUnchanged() throws Exception {
        FakeTransport transport = new FakeTransport().reply(304, "", Map.of("ETag", "etag-1"));
        GitHubBrainApi.FeedResponse response = new GitHubBrainApi(transport, "owner-token")
                .fetchMobileFeed("etag-1");
        assertFalse(response.changed());
        assertEquals("etag-1", transport.requests.get(0).headers().get("If-None-Match"));
        assertTrue(transport.requests.get(0).url().contains("work-graph/v1/mobile-feed.json?ref=main"));
    }

    private static final class FakeTransport implements GitHubHttpTransport {
        final ArrayDeque<GitHubHttpResponse> responses = new ArrayDeque<>();
        final List<GitHubHttpRequest> requests = new ArrayList<>();
        FakeTransport reply(int status, String body) { return reply(status, body, Map.of()); }
        FakeTransport reply(int status, String body, Map<String, String> headers) {
            responses.add(new GitHubHttpResponse(status, body, headers));
            return this;
        }
        @Override public GitHubHttpResponse execute(GitHubHttpRequest request) {
            requests.add(request);
            return responses.removeFirst();
        }
    }
}
