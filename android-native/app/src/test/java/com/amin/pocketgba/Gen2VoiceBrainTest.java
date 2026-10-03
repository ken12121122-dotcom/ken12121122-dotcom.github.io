package com.amin.pocketgba;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class Gen2VoiceBrainTest {
    private static Gen2RunView gate(int number) {
        return Gen2RunView.fromIssue(Gen2RunViewTest.issue(number, Gen2RunView.ENGINE_LOGIN, gateViewUnchecked()));
    }

    private static JSONObject gateViewUnchecked() {
        try { return Gen2RunViewTest.gateView(); } catch (Exception error) { throw new AssertionError(error); }
    }

    private static Gen2RunView input(int number) throws Exception {
        JSONObject view = Gen2RunViewTest.gateView().put("status", "waiting_input").put("current", "S1");
        view.put("pending", new JSONObject().put("step", "S1").put("type", "input").put("title", "來源")
                .put("prompt", "貼上工作紀錄"));
        return Gen2RunView.fromIssue(Gen2RunViewTest.issue(number, Gen2RunView.ENGINE_LOGIN, view));
    }

    private static Gen2RunView agent(int number) throws Exception {
        JSONObject view = Gen2RunViewTest.gateView().put("status", "waiting_skill").put("current", "S2");
        view.put("pending", new JSONObject().put("step", "S2").put("type", "skill").put("title", "擷取").put("skill", "SK-1"));
        return Gen2RunView.fromIssue(Gen2RunViewTest.issue(number, Gen2RunView.ENGINE_LOGIN, view));
    }

    @Test
    public void confirmationOnlyCountsWhileAProposalWaits() {
        assertEquals(Gen2VoiceBrain.Local.CONFIRM, Gen2VoiceBrain.local("確定", true));
        assertEquals(Gen2VoiceBrain.Local.CONFIRM, Gen2VoiceBrain.local("好，送出", true));
        assertEquals(Gen2VoiceBrain.Local.CONFIRM, Gen2VoiceBrain.local("狐狸 確認", true));
        assertEquals(Gen2VoiceBrain.Local.NONE, Gen2VoiceBrain.local("確定", false));
        assertEquals(Gen2VoiceBrain.Local.CANCEL, Gen2VoiceBrain.local("不確定", true));
        assertEquals(Gen2VoiceBrain.Local.CANCEL, Gen2VoiceBrain.local("不要送", true));
        assertEquals(Gen2VoiceBrain.Local.CANCEL, Gen2VoiceBrain.local("等一下", true));
        assertEquals(Gen2VoiceBrain.Local.NONE, Gen2VoiceBrain.local("好像有問題，9 點那筆是什麼會議", true));
        assertEquals(Gen2VoiceBrain.Local.NEXT, Gen2VoiceBrain.local("下一筆", false));
        assertEquals(Gen2VoiceBrain.Local.END, Gen2VoiceBrain.local("先這樣", false));
        assertEquals(Gen2VoiceBrain.Local.OPEN_PAGE, Gen2VoiceBrain.local("我用按的", false));
        assertEquals(Gen2VoiceBrain.Local.LIST, Gen2VoiceBrain.local("還有哪些", false));
    }

    @Test
    public void queuePutsTheTargetFirstAndSkipsAgentSteps() throws Exception {
        List<Gen2RunView> runs = Arrays.asList(gate(7), agent(8), input(9));
        List<Gen2RunView> queue = Gen2VoiceBrain.queue(runs, 9);
        assertEquals(2, queue.size());
        assertEquals(9, queue.get(0).number);
        assertEquals(7, queue.get(1).number);
        assertTrue(Gen2VoiceBrain.briefing(queue).startsWith("有 2 筆等你處理"));
        assertTrue(Gen2VoiceBrain.describe(gate(7)).contains("選項有：核准、退回重新擷取、這次不寫入"));
        assertEquals("目前沒有等你處理的 GEN2 流程。", Gen2VoiceBrain.briefing(new ArrayList<>()));
    }

    @Test
    public void systemPromptCarriesTheRunAndTheRules() {
        Gen2RunView run = gate(7);
        String prompt = Gen2VoiceBrain.systemPrompt(run, Arrays.asList(run, gate(11)));
        assertTrue(prompt.contains("你不能自己做決定"));
        assertTrue(prompt.contains("\"id\":\"revise\""));
        assertTrue(prompt.contains("| 10/3 | 09:00 | 巡檢 |"));
        assertTrue(prompt.contains("#11"));
    }

    @Test
    public void parsesModelRepliesWithOrWithoutJson() {
        Gen2VoiceBrain.Reply reply = Gen2VoiceBrain.parseReply("```json\n{\"say\":\"建議退回\",\"action\":{\"type\":\"decide\",\"option\":\"revise\",\"comment\":\"改 09:30\"}}\n```");
        assertEquals("建議退回", reply.say);
        assertEquals("revise", reply.action.optString("option"));
        Gen2VoiceBrain.Reply plain = Gen2VoiceBrain.parseReply("這筆看起來沒問題。");
        assertEquals("這筆看起來沒問題。", plain.say);
        assertNull(plain.action);
    }

    @Test
    public void proposalsGoThroughGen2CommandAndReadBack() throws Exception {
        Gen2RunView run = gate(7);
        Gen2VoiceBrain.Proposal p = Gen2VoiceBrain.propose(run,
                new JSONObject().put("type", "decide").put("option", "revise").put("comment", "改到 09:30"));
        assertEquals("/gen2 decide G1 revise\n改到 09:30", p.command);
        assertTrue(p.readBack.contains("退回重新擷取") && p.readBack.contains("改到 09:30") && p.readBack.endsWith("確定嗎？"));
        IllegalArgumentException noReason = assertThrows(IllegalArgumentException.class,
                () -> Gen2VoiceBrain.propose(run, new JSONObject().put("type", "decide").put("option", "revise")));
        assertTrue(noReason.getMessage().contains("必須寫說明"));
        assertThrows(IllegalArgumentException.class,
                () -> Gen2VoiceBrain.propose(run, new JSONObject().put("type", "decide").put("option", "merge")));
        assertNull(Gen2VoiceBrain.propose(run, new JSONObject().put("type", "next")));
        assertEquals("/gen2 cancel\n不需要", Gen2VoiceBrain.propose(run, new JSONObject().put("type", "cancel").put("reason", "不需要")).command);
        Gen2RunView in = input(9);
        assertEquals("/gen2 input S1\n09:00 巡檢", Gen2VoiceBrain.propose(in, new JSONObject().put("type", "input").put("text", "09:00 巡檢")).command);
    }

    @Test
    public void fallbackMatchesOptionsWithoutAModel() throws Exception {
        Gen2RunView run = gate(7);
        assertEquals("approve", Gen2VoiceBrain.fallbackAction(run, "核准").optString("option"));
        JSONObject revise = Gen2VoiceBrain.fallbackAction(run, "退回，9 點那筆改成 9 點半");
        assertEquals("revise", revise.optString("option"));
        assertEquals("退回，9 點那筆改成 9 點半", revise.optString("comment"));
        assertEquals("stop", Gen2VoiceBrain.fallbackAction(run, "這次不寫入").optString("option"));
        assertNull(Gen2VoiceBrain.fallbackAction(run, "這是什麼"));
        assertEquals("input", Gen2VoiceBrain.fallbackAction(input(9), "九點巡檢").optString("type"));
    }

    @Test
    public void recognisesAskingTheFoxForPendingWork() {
        assertTrue(Gen2VoiceBrain.asksForPendingWork("狐狸，有什麼要我處理的？"));
        assertTrue(Gen2VoiceBrain.asksForPendingWork("GEN2 待核准"));
        assertTrue(Gen2VoiceBrain.asksForPendingWork("我的待辦"));
        assertFalse(Gen2VoiceBrain.asksForPendingWork("打開 GBA"));
        assertFalse(Gen2VoiceBrain.asksForPendingWork("今天天氣如何"));
    }

    @Test
    public void deepLinkAcceptsOnlyRunNumbers() {
        assertEquals(7, Gen2VoiceBrain.issueFromUri("amin-gen2", "run", "/7"));
        assertEquals(12, Gen2VoiceBrain.issueFromUri("amin-gen2", "run", "/12/"));
        assertEquals(0, Gen2VoiceBrain.issueFromUri("amin-gen2", "run", ""));
        assertEquals(0, Gen2VoiceBrain.issueFromUri("amin-gen2", "run", "/0"));
        assertEquals(0, Gen2VoiceBrain.issueFromUri("amin-gen2", "run", "/7/../1"));
        assertEquals(0, Gen2VoiceBrain.issueFromUri("https", "run", "/7"));
        assertFalse(Gen2VoiceBrain.issueFromUri("amin-gen2", "evil", "/7") > 0);
    }
}
