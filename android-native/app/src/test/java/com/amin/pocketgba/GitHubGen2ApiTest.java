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
    public void startsARunThroughGen2RunStartOnMain() throws Exception {
        FakeTransport transport = new FakeTransport().reply(204, "");
        new GitHubGen2Api(transport, "owner-token").startRun("WF-TIME-001", "今天的巡檢寫進行事曆");
        GitHubHttpRequest post = transport.requests.get(0);
        assertEquals("POST", post.method());
        assertTrue(post.url().endsWith("/repos/ken12121122-dotcom/gen2-knowledge/actions/workflows/gen2-run-start.yml/dispatches"));
        JSONObject body = new JSONObject(post.body());
        assertEquals("main", body.getString("ref"));
        assertEquals("WF-TIME-001", body.getJSONObject("inputs").getString("workflow_id"));
        assertEquals("今天的巡檢寫進行事曆", body.getJSONObject("inputs").getString("note"));
        assertThrows(IllegalArgumentException.class, () -> new GitHubGen2Api(new FakeTransport(), "t").startRun("../x", ""));
        SecurityException denied = assertThrows(SecurityException.class,
                () -> new GitHubGen2Api(new FakeTransport().reply(403, "{}"), "t").startRun("WF-TIME-001", ""));
        assertTrue(denied.getMessage().contains("Actions"));
    }

    @Test
    public void readsRunnableWorkflowsFromMain() throws Exception {
        String path = "kb/02_KNOWLEDGE_BASES/KB-DEMO_示範/04_WORKFLOWS/WF-DEMO-001.md";
        JSONObject tree = new JSONObject().put("tree", new JSONArray()
                .put(new JSONObject().put("type", "blob").put("path", path)));
        String encoded = java.util.Base64.getMimeEncoder().encodeToString(Gen2ChatBrainTest.WF_TIME.getBytes(StandardCharsets.UTF_8));
        FakeTransport transport = new FakeTransport().reply(200, tree.toString())
                .reply(200, new JSONObject().put("content", encoded).toString());
        List<Gen2WorkflowCatalog.Workflow> workflows = new GitHubGen2Api(transport, "t").workflows();
        assertEquals(1, workflows.size());
        assertEquals("WF-DEMO-001", workflows.get(0).id);
        assertTrue(transport.requests.get(0).url().endsWith("/repos/ken12121122-dotcom/gen2-knowledge/git/trees/main?recursive=1"));
        assertTrue(transport.requests.get(1).url().contains("/contents/kb/02_KNOWLEDGE_BASES/KB-DEMO_%E7%A4%BA%E7%AF%84/04_WORKFLOWS/WF-DEMO-001.md?ref=main"));
        assertThrows(IllegalArgumentException.class, () -> GitHubGen2Api.encodePath("kb/../x.md"));
    }

    @Test
    public void foxChatFindsOrOpensTheChatIssueAndPostsWords() throws Exception {
        FakeTransport found = new FakeTransport().reply(200, "[{\"number\":9,\"pull_request\":{}},{\"number\":50}]");
        assertEquals(50, new GitHubGen2Api(found, "t").chatIssue());
        assertTrue(found.requests.get(0).url().endsWith("/repos/ken12121122-dotcom/gen2-knowledge/issues?labels=gen2-chat&state=open&per_page=10&sort=created&direction=asc"));
        FakeTransport open = new FakeTransport().reply(200, "[]").reply(201, "{\"number\":51}");
        assertEquals(51, new GitHubGen2Api(open, "t").chatIssue());
        assertEquals("POST", open.requests.get(1).method());
        assertTrue(new JSONObject(open.requests.get(1).body()).getJSONArray("labels").toString().contains("gen2-chat"));

        FakeTransport post = new FakeTransport().reply(201, "{\"id\":777}");
        assertEquals(777L, new GitHubGen2Api(post, "t").postChat(50, "幫我做週報"));
        assertTrue(post.requests.get(0).url().endsWith("/repos/ken12121122-dotcom/gen2-knowledge/issues/50/comments"));
        assertThrows(IllegalArgumentException.class, () -> new GitHubGen2Api(new FakeTransport(), "t").postChat(50, "/gen2 cancel"));

        FakeTransport read = new FakeTransport().reply(200, "{\"comments\":150}").reply(200, "[{\"id\":1}]").reply(200, "[{\"id\":2}]");
        assertEquals(2, new GitHubGen2Api(read, "t").chatComments(50).length());
        assertTrue(read.requests.get(1).url().endsWith("/issues/50/comments?per_page=100&page=1"));
        assertTrue(read.requests.get(2).url().endsWith("/issues/50/comments?per_page=100&page=2"));
    }

    @Test
    public void notebooksAndMemoryAreReadOnlyExceptOpeningANotebook() throws Exception {
        FakeTransport list = new FakeTransport().reply(200, "[{\"number\":40,\"title\":\"🦊 消防演練\"}]");
        assertEquals("消防演練", Gen2FoxChat.notebooks(new GitHubGen2Api(list, "t").notebooks()).get(0).title);
        assertTrue(list.requests.get(0).url().contains("/issues?labels=gen2-chat&state=open"));
        FakeTransport open = new FakeTransport().reply(201, "{\"number\":41}");
        assertEquals(41, new GitHubGen2Api(open, "t").newNotebook(" 巡檢 週報 "));
        assertEquals("🦊 巡檢 週報", new JSONObject(open.requests.get(0).body()).getString("title"));
        assertThrows(IllegalArgumentException.class, () -> new GitHubGen2Api(new FakeTransport(), "t").newNotebook(" "));
        String memory = java.util.Base64.getMimeEncoder().encodeToString("{\"version\":1,\"items\":[{\"id\":\"m1\",\"kind\":\"profile\",\"text\":\"職安主管\"}]}".getBytes(StandardCharsets.UTF_8));
        FakeTransport read = new FakeTransport().reply(200, new JSONObject().put("content", memory).toString());
        assertEquals("m1", new GitHubGen2Api(read, "t").foxMemory().getJSONArray("items").getJSONObject(0).getString("id"));
        assertTrue(read.requests.get(0).url().endsWith("/contents/memory.json?ref=fox-memory"));
        assertEquals(0, new GitHubGen2Api(new FakeTransport().reply(404, "{}"), "t").foxMemory().length());
    }

    @Test
    public void gen2ApiCannotReachOtherRepositoriesOrMutateCode() throws Exception {
        String api = read("src/main/java/com/amin/pocketgba/GitHubGen2Api.java");
        String activity = read("src/main/java/com/amin/pocketgba/Gen2RunsActivity.java");
        assertTrue(api.contains("ken12121122-dotcom/gen2-knowledge"));
        assertFalse(api.contains("/pulls"));
        assertFalse(api.contains("/merge"));
        // Bridge 104: the only Actions call is starting an approved run on main,
        // and file access is read-only on main.
        java.util.regex.Matcher actions = java.util.regex.Pattern.compile("/actions[^\"]*").matcher(api);
        while (actions.find()) assertEquals("/actions/workflows/gen2-run-start.yml/dispatches", actions.group());
        java.util.regex.Matcher contents = java.util.regex.Pattern.compile("request\\(\"([A-Z]+)\", REPOSITORY_PATH \\+ \"/(contents|git)/").matcher(api);
        int reads = 0;
        while (contents.find()) { assertEquals("GET", contents.group(1)); reads++; }
        assertEquals(3, reads); // workflow tree, workflow file, fox memory (Bridge 106)
        assertTrue(api.contains("\"ref\", \"main\""));
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
