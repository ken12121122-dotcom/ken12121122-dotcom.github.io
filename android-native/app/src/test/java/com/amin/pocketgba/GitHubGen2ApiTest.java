package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GitHubGen2ApiTest {
    @Test
    public void verifiesOwnerAndPrivateGen2Repository() throws Exception {
        FakeTransport transport = new FakeTransport()
                .reply(200, "{\"login\":\"ken12121122-dotcom\"}")
                .reply(200, "{\"full_name\":\"ken12121122-dotcom/gen2-knowledge\",\"private\":true}");
        new GitHubGen2Api(transport, "owner-token").verifySession();
        assertEquals("https://api.github.com/user", transport.requests.get(0).url());
        assertTrue(transport.requests.get(1).url().endsWith("/repos/ken12121122-dotcom/gen2-knowledge"));
        assertEquals("Bearer owner-token", transport.requests.get(1).headers().get("Authorization"));
    }

    @Test
    public void explainsWhenTheGitHubAppCannotSeeGen2Knowledge() {
        FakeTransport transport = new FakeTransport()
                .reply(200, "{\"login\":\"ken12121122-dotcom\"}")
                .reply(404, "{}");
        SecurityException error = assertThrows(SecurityException.class,
                () -> new GitHubGen2Api(transport, "owner-token").verifySession());
        assertTrue(error.getMessage().contains("gen2-knowledge"));
        FakeTransport other = new FakeTransport().reply(200, "{\"login\":\"someone\"}");
        assertThrows(SecurityException.class, () -> new GitHubGen2Api(other, "token").verifySession());
    }

    @Test
    public void listsOpenRunsAndPostsOneCommentPerCommand() throws Exception {
        JSONArray issues = new JSONArray()
                .put(Gen2RunViewTest.issue(7, Gen2RunView.ENGINE_LOGIN, Gen2RunViewTest.gateView()));
        FakeTransport transport = new FakeTransport().reply(200, issues.toString()).reply(201, "{}");
        GitHubGen2Api api = new GitHubGen2Api(transport, "owner-token");
        List<Gen2RunView> runs = api.openRuns();
        assertEquals(1, runs.size());
        assertTrue(transport.requests.get(0).url().contains("/repos/ken12121122-dotcom/gen2-knowledge/issues?labels=gen2-run&state=open"));
        api.postCommand(7, Gen2Command.decide(runs.get(0), "approve", ""));
        GitHubHttpRequest post = transport.requests.get(1);
        assertEquals("POST", post.method());
        assertTrue(post.url().endsWith("/repos/ken12121122-dotcom/gen2-knowledge/issues/7/comments"));
        assertEquals("/gen2 decide G1 approve", new JSONObject(post.body()).getString("body"));
        assertThrows(IllegalArgumentException.class, () -> api.postCommand(7, "hello"));
    }

    @Test
    public void notifiesOncePerWaitingStepAndClearsFinishedOnes() throws Exception {
        Gen2RunView waiting = Gen2RunView.fromIssue(Gen2RunViewTest.issue(7, Gen2RunView.ENGINE_LOGIN, Gen2RunViewTest.gateView()));
        Gen2RunRepository.Delta first = Gen2RunRepository.diff(Collections.singletonList(waiting), Collections.emptySet());
        assertEquals(1, first.fresh.size());
        Gen2RunRepository.Delta again = Gen2RunRepository.diff(Collections.singletonList(waiting), first.waiting);
        assertEquals(0, again.fresh.size());
        JSONObject retried = Gen2RunViewTest.gateView();
        retried.getJSONObject("pending").put("attempt", 2);
        Gen2RunView second = Gen2RunView.fromIssue(Gen2RunViewTest.issue(7, Gen2RunView.ENGINE_LOGIN, retried));
        assertEquals(1, Gen2RunRepository.diff(Collections.singletonList(second), first.waiting).fresh.size());
        Gen2RunRepository.Delta gone = Gen2RunRepository.diff(Collections.emptyList(), first.waiting);
        assertEquals(new LinkedHashSet<>(Arrays.asList(7)), gone.cleared);
        assertTrue(gone.waiting.isEmpty());
    }

    @Test
    public void gen2ApiCannotReachOtherRepositoriesOrMutateCode() throws Exception {
        String api = read("src/main/java/com/amin/pocketgba/GitHubGen2Api.java");
        String activity = read("src/main/java/com/amin/pocketgba/Gen2RunsActivity.java");
        assertTrue(api.contains("ken12121122-dotcom/gen2-knowledge"));
        assertFalse(api.contains("/pulls"));
        assertFalse(api.contains("/actions"));
        assertFalse(api.contains("/contents"));
        assertFalse(api.contains("/merge"));
        assertFalse(api.contains("\"PATCH\""));
        assertFalse(api.contains("\"DELETE\""));
        assertFalse(activity.contains("AminControlApi"));
        assertTrue(read("src/main/AndroidManifest.xml").contains(".Gen2RunsActivity\" android:exported=\"false\""));
    }

    private static String read(String relative) throws Exception {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path directApp = current.resolve("app");
            Path repositoryApp = current.resolve("android-native/app");
            if (Files.isRegularFile(directApp.resolve("build.gradle"))) {
                return new String(Files.readAllBytes(directApp.resolve(relative)), StandardCharsets.UTF_8);
            }
            if (Files.isRegularFile(repositoryApp.resolve("build.gradle"))) {
                return new String(Files.readAllBytes(repositoryApp.resolve(relative)), StandardCharsets.UTF_8);
            }
            if (Files.isRegularFile(current.resolve("build.gradle")) && Files.isDirectory(current.resolve("src/main"))) {
                return new String(Files.readAllBytes(current.resolve(relative)), StandardCharsets.UTF_8);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot find app module");
    }

    private static final class FakeTransport implements GitHubHttpTransport {
        final ArrayDeque<GitHubHttpResponse> responses = new ArrayDeque<>();
        final List<GitHubHttpRequest> requests = new ArrayList<>();
        FakeTransport reply(int status, String body) {
            responses.add(new GitHubHttpResponse(status, body, Collections.emptyMap()));
            return this;
        }
        @Override public GitHubHttpResponse execute(GitHubHttpRequest request) {
            requests.add(request);
            return responses.removeFirst();
        }
    }
}
