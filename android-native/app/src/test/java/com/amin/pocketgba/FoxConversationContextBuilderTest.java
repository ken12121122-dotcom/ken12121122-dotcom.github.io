package com.amin.pocketgba;

import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class FoxConversationContextBuilderTest {
    @Test public void systemContextRequiresSourceHonestyAndNoSideEffects() throws Exception {
        String prompt = FoxConversationContextBuilder.systemContext(new JSONObject()
                .put("content", "## Selected Node: 財務 [app:finance]\n# 財務"));
        assertTrue(prompt.contains("你是狐狸"));
        assertTrue(prompt.contains("無法回報實際數值或狀態"));
        assertTrue(prompt.contains("不得聲稱已執行工具"));
        assertTrue(prompt.contains("Selected Node: 財務"));
    }
}
