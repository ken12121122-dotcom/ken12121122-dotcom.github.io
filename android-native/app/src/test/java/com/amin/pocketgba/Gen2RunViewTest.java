package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Gen2RunViewTest {
    static JSONObject gateView() throws Exception {
        return new JSONObject()
                .put("format", "gen2-run-view").put("version", 1)
                .put("runId", "WF-TIME-001-20261003120035")
                .put("workflow", new JSONObject().put("id", "WF-TIME-001").put("title", "工作紀錄寫入 Google Calendar"))
                .put("status", "waiting_owner").put("current", "G1").put("seq", 2)
                .put("pending", new JSONObject().put("step", "G1").put("type", "gate")
                        .put("title", "OWNER 審核候選工作紀錄").put("attempt", 1)
                        .put("options", new JSONArray()
                                .put(new JSONObject().put("id", "approve").put("label", "核准").put("comment", JSONObject.NULL))
                                .put(new JSONObject().put("id", "revise").put("label", "退回重新擷取").put("comment", "required"))
                                .put(new JSONObject().put("id", "stop").put("label", "這次不寫入").put("comment", "optional")))
                        .put("review", new JSONObject().put("step", "S2").put("title", "產出候選")
                                .put("output", new JSONObject().put("text", "| 10/3 | 09:00 | 巡檢 |")
                                        .put("url", "https://github.com/ken12121122-dotcom/gen2-knowledge/issues/7#c1"))))
                .put("steps", new JSONArray().put(new JSONObject().put("id", "S1").put("type", "input")
                        .put("title", "來源").put("status", "done").put("result", JSONObject.NULL)));
    }

    static JSONObject issue(int number, String login, JSONObject view) {
        try {
            String body = "## ▶ run\n\n<!-- gen2-run-start:e30= -->\n<!-- gen2-run-view:"
                    + Base64.getEncoder().encodeToString(view.toString().getBytes(StandardCharsets.UTF_8)) + " -->\n";
            return new JSONObject().put("number", number).put("title", "▶ WF-TIME-001｜run")
                    .put("html_url", "https://github.com/ken12121122-dotcom/gen2-knowledge/issues/" + number)
                    .put("user", new JSONObject().put("login", login))
                    .put("labels", new JSONArray().put(new JSONObject().put("name", "gen2-run"))
                            .put(new JSONObject().put("name", "gen2:waiting-owner")))
                    .put("body", body);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void parsesWaitingGateFromEngineIssue() throws Exception {
        Gen2RunView run = Gen2RunView.fromIssue(issue(7, Gen2RunView.ENGINE_LOGIN, gateView()));
        assertNotNull(run);
        assertEquals(7, run.number);
        assertEquals("WF-TIME-001", run.workflowId);
        assertTrue(run.needsOwner());
        assertEquals("等待你核准", run.statusText());
        assertTrue(run.pending.isGate());
        assertEquals(3, run.pending.options.size());
        assertTrue(run.pending.option("revise").commentRequired);
        assertTrue(run.pending.option("stop").commentOptional);
        assertFalse(run.pending.option("approve").commentRequired);
        assertEquals("| 10/3 | 09:00 | 巡檢 |", run.pending.reviewText);
        assertEquals("S2｜產出候選", run.pending.reviewTitle);
        assertEquals("WF-TIME-001-20261003120035|G1|1|waiting_owner", run.waitKey());
    }

    @Test
    public void ignoresIssuesNotOpenedByTheEngineOrWithoutAValidView() throws Exception {
        assertNull(Gen2RunView.fromIssue(issue(8, "someone-else", gateView())));
        JSONObject pr = issue(9, Gen2RunView.ENGINE_LOGIN, gateView()).put("pull_request", new JSONObject());
        assertNull(Gen2RunView.fromIssue(pr));
        JSONObject wrongFormat = gateView().put("format", "other");
        assertNull(Gen2RunView.fromIssue(issue(10, Gen2RunView.ENGINE_LOGIN, wrongFormat)));
        JSONObject noMarker = issue(11, Gen2RunView.ENGINE_LOGIN, gateView()).put("body", "no view");
        assertNull(Gen2RunView.fromIssue(noMarker));
        JSONObject noLabel = issue(12, Gen2RunView.ENGINE_LOGIN, gateView()).put("labels", new JSONArray());
        assertNull(Gen2RunView.fromIssue(noLabel));
    }

    @Test
    public void dropsPendingThatDoesNotMatchTheCurrentStepOrHasTooFewOptions() throws Exception {
        JSONObject stale = gateView().put("current", "S3");
        assertNull(Gen2RunView.fromIssue(issue(13, Gen2RunView.ENGINE_LOGIN, stale)).pending);
        JSONObject oneOption = gateView();
        oneOption.getJSONObject("pending").put("options", new JSONArray()
                .put(new JSONObject().put("id", "approve").put("label", "核准")));
        Gen2RunView run = Gen2RunView.fromIssue(issue(14, Gen2RunView.ENGINE_LOGIN, oneOption));
        assertNull(run.pending);
        assertFalse(run.needsOwner());
    }

    @Test
    public void listsRunsWaitingForTheOwnerFirst() throws Exception {
        JSONObject skill = gateView().put("status", "waiting_skill").put("current", "S2");
        skill.put("pending", new JSONObject().put("step", "S2").put("type", "skill").put("title", "擷取")
                .put("skill", "SK-TIME-001"));
        JSONArray issues = new JSONArray()
                .put(issue(20, Gen2RunView.ENGINE_LOGIN, skill))
                .put(issue(5, Gen2RunView.ENGINE_LOGIN, gateView()))
                .put(issue(21, "intruder", gateView()));
        List<Gen2RunView> runs = Gen2RunView.fromIssues(issues);
        assertEquals(2, runs.size());
        assertEquals(5, runs.get(0).number);
        assertEquals(20, runs.get(1).number);
        assertEquals("等待 Agent 執行", runs.get(1).statusText());
    }
}
