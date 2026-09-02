package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Test;

public final class GitHubWorkApiTest {
    @Test public void completePublicClientIsGetOnlyAndNeverRequestsRawLogs() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.add("{\"id\":1095700954,\"full_name\":\"" + GitHubWorkApi.REPOSITORY
                + "\",\"pushed_at\":\"r1\"}");
        transport.add("[]");
        transport.add("[]", "<https://api.github.test/page=2>; rel=\"next\"");
        transport.add("[{\"number\":120,\"title\":\"Issue\"},{\"number\":121,"
                + "\"pull_request\":{\"url\":\"x\"}}]");
        transport.add("[{\"number\":121,\"title\":\"PR\"}]");
        transport.add("[{\"id\":44,\"state\":\"APPROVED\"}]");
        transport.add("[{\"filename\":\"A.java\",\"additions\":3,\"deletions\":1}]");
        transport.add("{\"workflow_runs\":[{\"id\":9,\"head_sha\":\"abc\"}]}");
        transport.add("{\"jobs\":[{\"id\":7,\"steps\":[{\"number\":2,"
                + "\"name\":\"compile\",\"conclusion\":\"failure\"}]}]}");
        transport.add("[{\"id\":5,\"tag_name\":\"v1\"}]");
        transport.add("{\"artifacts\":[{\"id\":6,\"name\":\"apk\","
                + "\"workflow_run\":{\"id\":9}}]}");

        JSONObject observation = new GitHubWorkApi(transport).fetchSnapshot(1000L);

        assertEquals(11, transport.requests.size());
        for (GitHubHttpRequest request : transport.requests) {
            assertEquals("GET", request.method());
            assertTrue(request.url().startsWith("https://api.github.com/repos/" + GitHubWorkApi.REPOSITORY));
            assertFalse(request.headers().containsKey("Authorization"));
            assertFalse(request.url().contains("/logs"));
            assertTrue(request.body().isEmpty());
        }
        assertEquals(1, observation.getJSONArray("issues").length());
        assertEquals(1, observation.getJSONArray("pull_requests").getJSONObject(0)
                .getJSONArray("_reviews").length());
        assertEquals("failure", observation.getJSONArray("jobs").getJSONObject(0)
                .getJSONArray("steps").getJSONObject(0).getString("conclusion"));
        assertEquals(1, observation.getJSONArray("releases").length());
        assertEquals(1, observation.getJSONArray("artifacts").length());
        assertFalse(observation.getJSONObject("completeness").getBoolean("commits"));
        assertTrue(observation.getJSONObject("completeness").getBoolean("reviews"));
        assertEquals(11, observation.getJSONObject("sync_metadata").getInt("request_count"));
        assertEquals(49, observation.getJSONObject("sync_metadata").getInt("rate_remaining"));
        assertFalse(observation.getJSONObject("sync_metadata").getBoolean("raw_logs_enabled"));
    }

    private static final class FakeTransport implements GitHubHttpTransport {
        private final List<Response> responses = new ArrayList<>();
        private final List<GitHubHttpRequest> requests = new ArrayList<>();
        private int index;

        void add(String body) { add(body, ""); }
        void add(String body, String link) { responses.add(new Response(body, link)); }

        @Override public GitHubHttpResponse execute(GitHubHttpRequest request) {
            requests.add(request);
            Response response = responses.get(index++);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-RateLimit-Limit", "60");
            headers.put("X-RateLimit-Remaining", String.valueOf(60 - index));
            headers.put("X-RateLimit-Reset", "2000");
            headers.put("X-RateLimit-Resource", "core");
            if (!response.link.isEmpty()) headers.put("Link", response.link);
            return new GitHubHttpResponse(200, response.body, headers);
        }
    }

    private static final class Response {
        final String body;
        final String link;
        Response(String body, String link) { this.body = body; this.link = link; }
    }
}
