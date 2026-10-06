package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class Gen2ChatBrainTest {
    static final String WF_TIME = "---\ndocument_type: workflow\nworkflow_id: WF-DEMO-001\nkb_id: KB-DEMO\n---\n\n"
            + "# WF-DEMO-001｜工作紀錄寫入行事曆\n\n## 目的\n把工作紀錄整理後寫入行事曆。\n\n## 執行步驟\n1. 收資料\n\n"
            + "## 執行定義（gen2-run）\n```gen2-run\n{\"version\":1,\"steps\":[{\"id\":\"S1\",\"type\":\"input\",\"title\":\"收資料\",\"prompt\":\"貼上工作紀錄\"},"
            + "{\"id\":\"S2\",\"type\":\"skill\",\"skill\":\"SK-DEMO-001\",\"title\":\"擷取\"}]}\n```\n";

    static List<Gen2WorkflowCatalog.Workflow> catalog() {
        return Collections.singletonList(Gen2WorkflowCatalog.parse(WF_TIME));
    }

    static Gen2RunView gate(int number) throws Exception {
        return Gen2RunView.fromIssue(Gen2RunViewTest.issue(number, Gen2RunView.ENGINE_LOGIN, Gen2RunViewTest.gateView()));
    }

    static Gen2RunView inputRun(int number, String workflowId) throws Exception {
        JSONObject view = Gen2RunViewTest.gateView().put("status", "waiting_input").put("current", "S1");
        view.getJSONObject("workflow").put("id", workflowId);
        view.put("pending", new JSONObject().put("step", "S1").put("type", "input").put("title", "收資料").put("prompt", "貼上工作紀錄"));
        return Gen2RunView.fromIssue(Gen2RunViewTest.issue(number, Gen2RunView.ENGINE_LOGIN, view));
    }

    @Test
    public void catalogReadsOnlyRunnableWorkflows() {
        Gen2WorkflowCatalog.Workflow wf = Gen2WorkflowCatalog.parse(WF_TIME);
        assertEquals("WF-DEMO-001", wf.id);
        assertEquals("工作紀錄寫入行事曆", wf.title);
        assertEquals("把工作紀錄整理後寫入行事曆。", wf.purpose);
        assertTrue(wf.startsWithInput());
        assertEquals("貼上工作紀錄", wf.firstPrompt);
        assertNull(Gen2WorkflowCatalog.parse(WF_TIME.replace("```gen2-run", "```json")));
        assertNull(Gen2WorkflowCatalog.parse(WF_TIME.replace("document_type: workflow", "document_type: skill")));
        assertNull(Gen2WorkflowCatalog.parse(WF_TIME.replace("workflow_id: WF-DEMO-001", "workflow_id: ../x")));
        assertNull(Gen2WorkflowCatalog.parse(WF_TIME.replace("\"steps\":[", "\"steps\":[] ,\"x\":[")));
        List<Gen2WorkflowCatalog.Workflow> round = Gen2WorkflowCatalog.fromJson(Gen2WorkflowCatalog.toJson(catalog()));
        assertEquals("WF-DEMO-001", round.get(0).id);
        assertTrue(round.get(0).startsWithInput());
    }

    @Test
    public void catalogPathsComeFromTheWorkflowFolders() throws Exception {
        JSONObject tree = new JSONObject().put("tree", new JSONArray()
                .put(new JSONObject().put("type", "blob").put("path", "kb/02_KNOWLEDGE_BASES/KB-TIME_時間管理/04_WORKFLOWS/WF-TIME-001.md"))
                .put(new JSONObject().put("type", "blob").put("path", "kb/02_KNOWLEDGE_BASES/_KB_TEMPLATE/04_WORKFLOWS/WF-X-001.md"))
                .put(new JSONObject().put("type", "blob").put("path", "kb/02_KNOWLEDGE_BASES/KB-TIME_時間管理/05_SKILLS/SK-TIME-001.md"))
                .put(new JSONObject().put("type", "tree").put("path", "kb/02_KNOWLEDGE_BASES/KB-TIME_時間管理/04_WORKFLOWS")));
        assertEquals(Collections.singletonList("kb/02_KNOWLEDGE_BASES/KB-TIME_時間管理/04_WORKFLOWS/WF-TIME-001.md"),
                Gen2WorkflowCatalog.workflowPaths(tree));
    }

    @Test
    public void promptTellsTheModelToJudgeMeaningNotKeywords() throws Exception {
        Gen2RunView run = gate(7);
        Gen2ChatBrain.Proposal waiting = Gen2ChatBrain.propose(new JSONObject().put("type", "decide").put("issue", 7).put("option", "approve"),
                catalog(), Arrays.asList(run));
        String prompt = Gen2ChatBrain.systemPrompt(catalog(), Arrays.asList(run), run, waiting);
        assertTrue(prompt.contains("不要等特定關鍵字"));
        assertTrue(prompt.contains("一次最多問一件事"));
        assertTrue(prompt.contains("絕不要猜同意"));
        assertTrue(prompt.contains("\"id\":\"WF-DEMO-001\""));
        assertTrue(prompt.contains("等待 OWNER 同意的動作"));
        assertTrue(prompt.contains("OWNER 是從 #7 進來的"));
    }

    @Test
    public void parsesAgreementAsJudgedByTheModel() {
        Gen2ChatBrain.Reply yes = Gen2ChatBrain.parseReply("```json\n{\"say\":\"好，我去開始\",\"confirm\":\"yes\",\"action\":null}\n```");
        assertEquals(Boolean.TRUE, yes.confirm);
        assertEquals("好，我去開始", yes.say);
        assertEquals(Boolean.FALSE, Gen2ChatBrain.parseReply("{\"say\":\"那要改哪裡？\",\"confirm\":\"no\"}").confirm);
        Gen2ChatBrain.Reply unsure = Gen2ChatBrain.parseReply("{\"say\":\"你是說要開始嗎？\",\"confirm\":null}");
        assertNull(unsure.confirm);
        Gen2ChatBrain.Reply plain = Gen2ChatBrain.parseReply("嗯嗯");
        assertNull(plain.confirm);
        assertNull(plain.action);
        assertEquals("嗯嗯", plain.say);
    }

    @Test
    public void startProposalsAreValidatedAndReadBackByTheApp() throws Exception {
        JSONObject start = new JSONObject().put("type", "start").put("workflow", "WF-DEMO-001")
                .put("scope", "今天的巡檢和會議寫進行事曆").put("material", "9:00-10:00 巡檢，10:00-11:00 開會");
        Gen2ChatBrain.Proposal p = Gen2ChatBrain.propose(start, catalog(), Collections.emptyList());
        assertTrue(p.isStart());
        assertEquals("WF-DEMO-001", p.workflow.id);
        assertTrue(p.readBack.contains("工作紀錄寫入行事曆") && p.readBack.contains("巡檢"));
        IllegalArgumentException noMaterial = assertThrows(IllegalArgumentException.class,
                () -> Gen2ChatBrain.propose(new JSONObject(start.toString()).put("material", ""), catalog(), Collections.emptyList()));
        assertTrue(noMaterial.getMessage().contains("貼上工作紀錄"));
        assertThrows(IllegalArgumentException.class,
                () -> Gen2ChatBrain.propose(new JSONObject(start.toString()).put("workflow", "WF-NOPE-001"), catalog(), Collections.emptyList()));
        assertThrows(IllegalArgumentException.class,
                () -> Gen2ChatBrain.propose(new JSONObject(start.toString()).put("scope", " "), catalog(), Collections.emptyList()));
        assertNull(Gen2ChatBrain.propose(new JSONObject().put("type", "chat"), catalog(), Collections.emptyList()));
    }

    @Test
    public void runActionsGoThroughTheExistingCommandRules() throws Exception {
        List<Gen2RunView> runs = Arrays.asList(gate(7));
        Gen2ChatBrain.Proposal p = Gen2ChatBrain.propose(new JSONObject().put("type", "decide").put("issue", 7)
                .put("option", "revise").put("comment", "9 點那筆改 9:30"), catalog(), runs);
        assertEquals("/gen2 decide G1 revise\n9 點那筆改 9:30", p.command);
        assertFalse(p.readBack.endsWith("確定嗎？"));
        assertThrows(IllegalArgumentException.class, () -> Gen2ChatBrain.propose(new JSONObject().put("type", "decide").put("issue", 7)
                .put("option", "revise"), catalog(), runs));
        assertThrows(IllegalArgumentException.class, () -> Gen2ChatBrain.propose(new JSONObject().put("type", "decide").put("issue", 99)
                .put("option", "approve"), catalog(), runs));
    }

    @Test
    public void findsTheRunThatAStartCreated() throws Exception {
        List<Gen2RunView> before = Arrays.asList(gate(7), inputRun(9, "WF-DEMO-001"));
        assertEquals(9, Gen2ChatBrain.highest(before));
        List<Gen2RunView> after = Arrays.asList(gate(7), inputRun(9, "WF-DEMO-001"), inputRun(13, "WF-OTHER-001"), inputRun(12, "WF-DEMO-001"));
        assertEquals(12, Gen2ChatBrain.startedRun(after, "WF-DEMO-001", 9).number);
        assertNull(Gen2ChatBrain.startedRun(before, "WF-DEMO-001", 9));
    }

    @Test
    public void greetingReflectsWhatWaits() throws Exception {
        assertTrue(Gen2ChatBrain.greeting(Collections.emptyList(), null).contains("有什麼想交給我"));
        assertTrue(Gen2ChatBrain.greeting(Arrays.asList(gate(7), gate(8)), null).contains("有 2 件事在等你"));
        assertTrue(Gen2ChatBrain.greeting(Arrays.asList(gate(7)), gate(7)).contains("#7"));
    }

    @Test
    public void voiceBrainAlsoReadsTheJudgedAgreement() {
        assertEquals(Boolean.TRUE, Gen2VoiceBrain.parseReply("{\"say\":\"好\",\"confirm\":\"yes\"}").confirm);
        assertEquals(Boolean.FALSE, Gen2VoiceBrain.parseReply("{\"say\":\"不送\",\"confirm\":\"no\",\"action\":null}").confirm);
        assertNull(Gen2VoiceBrain.parseReply("{\"say\":\"嗯\",\"action\":null}").confirm);
        assertTrue(Gen2VoiceBrain.systemPrompt(null, null, "送出 #7 G1 核准").contains("等待 OWNER 同意的動作（App 已念給他）：送出 #7 G1 核准"));
        assertFalse(Gen2VoiceBrain.systemPrompt(null, null).contains("等待 OWNER 同意的動作（App"));
    }
}
