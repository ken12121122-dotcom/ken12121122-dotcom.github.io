package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class NodeMdContextBuilderTest {
    @Test public void systemContextNamesFoxAndKeepsReadOnlyBoundary() throws Exception {
        JSONObject context = new JSONObject()
                .put("content", "# 狐狸\n簡單描述")
                .put("source_records", new JSONArray().put(new JSONObject()
                        .put("source_id", "asset:node-context/fox-chat.md")))
                .put("unresolved_gaps", new JSONArray())
                .put("read_only", true);

        String prompt = NodeMdContextBuilder.systemContext(context);
        assertTrue(prompt.contains("你是狐狸"));
        assertTrue(prompt.contains("# 狐狸"));
        assertTrue(prompt.contains("不得聲稱已執行工具"));
    }

    @Test public void emptyContextDoesNotInventInstructions() throws Exception {
        assertEquals("", NodeMdContextBuilder.systemContext(new JSONObject().put("content", "")));
    }
}
