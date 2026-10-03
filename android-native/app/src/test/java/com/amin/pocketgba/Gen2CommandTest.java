package com.amin.pocketgba;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class Gen2CommandTest {
    private static Gen2RunView gate() throws Exception {
        return Gen2RunView.fromIssue(Gen2RunViewTest.issue(7, Gen2RunView.ENGINE_LOGIN, Gen2RunViewTest.gateView()));
    }

    private static Gen2RunView input() throws Exception {
        JSONObject view = Gen2RunViewTest.gateView().put("status", "waiting_input").put("current", "S1");
        view.put("pending", new JSONObject().put("step", "S1").put("type", "input").put("title", "來源")
                .put("prompt", "貼上工作紀錄"));
        return Gen2RunView.fromIssue(Gen2RunViewTest.issue(8, Gen2RunView.ENGINE_LOGIN, view));
    }

    @Test
    public void buildsGateDecisionsAndEnforcesRequiredComments() throws Exception {
        Gen2RunView run = gate();
        assertEquals("/gen2 decide G1 approve", Gen2Command.decide(run, "approve", "  "));
        assertEquals("/gen2 decide G1 approve\n看過了", Gen2Command.decide(run, "approve", "看過了"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> Gen2Command.decide(run, "revise", ""));
        assertTrue(missing.getMessage().contains("必須寫說明"));
        assertEquals("/gen2 decide G1 revise\n10:00 應為 10:30", Gen2Command.decide(run, "revise", "10:00 應為 10:30\r\n"));
        assertThrows(IllegalArgumentException.class, () -> Gen2Command.decide(run, "maybe", ""));
        assertThrows(IllegalStateException.class, () -> Gen2Command.input(run, "x"));
    }

    @Test
    public void buildsInputAndCancel() throws Exception {
        Gen2RunView run = input();
        assertEquals("/gen2 input S1\n09:00 巡檢\n10:00 開會", Gen2Command.input(run, "09:00 巡檢\n10:00 開會"));
        assertThrows(IllegalArgumentException.class, () -> Gen2Command.input(run, "   "));
        assertThrows(IllegalStateException.class, () -> Gen2Command.decide(run, "approve", ""));
        assertEquals("/gen2 cancel", Gen2Command.cancel(run, ""));
        assertEquals("/gen2 cancel\n不需要了", Gen2Command.cancel(run, "不需要了"));
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i <= Gen2Command.MAX_TEXT; i++) huge.append('x');
        assertThrows(IllegalArgumentException.class, () -> Gen2Command.input(run, huge.toString()));
    }
}
