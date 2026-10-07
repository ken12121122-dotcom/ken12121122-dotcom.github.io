package com.amin.pocketgba;

import org.json.JSONArray;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class Gen2FoxChatTest {
    private static final String OWNER = GitHubBrainApi.OWNER;
    private static final String PLAN = "{\"v\":1,\"say\":\"要這樣做嗎？\",\"plan\":{\"kind\":\"workflow\"},\"planId\":\"pa1b2c\",\"readBack\":\"啟動流程「WF-DEMO-001」\"}";

    @Test
    public void readsOwnerWordsAndFoxRepliesOnly() throws Exception {
        JSONArray comments = new JSONArray()
                .put(Gen2FoxChatTestData.comment(1, OWNER, " 嗨 "))
                .put(Gen2FoxChatTestData.comment(2, Gen2FoxChat.BOT, Gen2FoxChatTestData.fox(PLAN)))
                .put(Gen2FoxChatTestData.comment(3, "someone", "好"))
                .put(Gen2FoxChatTestData.comment(4, Gen2FoxChat.BOT, "沒有標記"))
                .put(Gen2FoxChatTestData.comment(5, OWNER, "改過").put("updated_at", "later"))
                .put(Gen2FoxChatTestData.comment(6, Gen2FoxChat.BOT, Gen2FoxChatTestData.fox("{\"v\":2,\"say\":\"x\"}")));
        List<Gen2FoxChat.Turn> turns = Gen2FoxChat.turns(comments, OWNER);
        assertEquals(2, turns.size());
        assertFalse(turns.get(0).fox);
        assertEquals("嗨", turns.get(0).text);
        assertTrue(turns.get(1).fox);
        assertEquals("要這樣做嗎？", turns.get(1).text);
        assertEquals("pa1b2c", Gen2FoxChat.pending(turns).planId);
        assertEquals("啟動流程「WF-DEMO-001」", Gen2FoxChat.pending(turns).readBack);
        assertEquals(2, Gen2FoxChat.replyAfter(turns, 1).id);
        assertNull(Gen2FoxChat.replyAfter(turns, 2));
    }

    @Test
    public void aResolvedPlanIsNoLongerPending() throws Exception {
        JSONArray comments = new JSONArray()
                .put(Gen2FoxChatTestData.comment(2, Gen2FoxChat.BOT, Gen2FoxChatTestData.fox(PLAN)))
                .put(Gen2FoxChatTestData.comment(3, Gen2FoxChat.BOT, Gen2FoxChatTestData.fox("{\"v\":1,\"say\":\"好\",\"resolved\":{\"planId\":\"pa1b2c\",\"outcome\":\"started\",\"issue\":60},\"note\":\"已開始 #60\"}")));
        List<Gen2FoxChat.Turn> turns = Gen2FoxChat.turns(comments, OWNER);
        assertNull(Gen2FoxChat.pending(turns));
        assertEquals("已開始 #60", turns.get(1).note);
    }

    @Test
    public void onlyPlainWordsAndPlanAnswersArePosted() {
        assertEquals("/fox confirm pa1b2c", Gen2FoxChat.confirm("pa1b2c"));
        assertEquals("/fox reject pa1b2c", Gen2FoxChat.reject("pa1b2c"));
        assertThrows(IllegalArgumentException.class, () -> Gen2FoxChat.confirm("p1 /gen2"));
        assertThrows(IllegalArgumentException.class, () -> Gen2FoxChat.message("/gen2 decide G1 approve"));
        assertThrows(IllegalArgumentException.class, () -> Gen2FoxChat.message("  "));
        assertEquals("你好", Gen2FoxChat.message(" 你好 "));
    }
}
