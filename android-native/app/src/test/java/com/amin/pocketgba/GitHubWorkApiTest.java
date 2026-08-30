package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

public final class GitHubWorkApiTest {
    @Test public void publicClientIsGetOnlyAndNeverRequestsRawLogs() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.add("{\"id\":1095700954,\"full_name\":\"" + GitHubWorkApi.REPOSITORY
                + "\",\"pushed_at\":\"r1\"}");
        transport.add("[]");
        transport.add("[]");
        transport.add("[]");
        transport.add("{\"workflow_runs\":[{\"id\":9,\"head_sha\":\"abc\"}]}");
        transport.add("{\"jobs\":[{\"id\":7,\"steps\":[{\"number\":2,"
                + "\"name\":\"compile\",\"conclusion\":\"failure\"}]}]}");

        JSONObject observation = new GitHubWorkApi(transport).fetchSnapshot(1000L);

        assertEquals(6, transport.requests.size());
        for (GitHubHttpRequest request : transport.requests) {
            assertEquals("GET", request.method());
            assertTrue(request.url().startsWith("https://api.github.com/repos/" + GitHubWorkApi.REPOSITORY));
            assertFalse(request.headers().containsKey("Authorization"));
            assertFalse(request.url().contains("/logs"));
            assertTrue(request.body().isEmpty());
        }
        assertEquals("failure", observation.getJSONArray("jobs").getJSONObject(0)
                .getJSONArray("steps").getJSONObject(0).getString("conclusion"));
        assertFalse(observation.getJSONObject("completeness").getBoolean("jobs"));
    }

    private static final class FakeTransport implements GitHubHttpTransport {
        private final List<String> responses = new ArrayList<>();
        private final List<GitHubHttpRequest> requests = new ArrayList<>();
        private int index;

        void add(String response) { responses.add(response); }

        @Override public GitHubHttpResponse execute(GitHubHttpRequest request) {
            requests.add(request);
            return new GitHubHttpResponse(200, responses.get(index++), Collections.emptyMap());
        }
    }
}
