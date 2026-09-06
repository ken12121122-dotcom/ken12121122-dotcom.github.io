package com.amin.pocketgba;

import static org.junit.Assert.assertFalse;
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

    @Test public void isNodeContextSelectionTrueWhenIntentMatchesAndNodesSelected() {
        SemanticRouteContract semantic = SemanticRouteContract.parse("{\"intent\":\"node_context\","
                + "\"selected_nodes\":[\"app:finance\"],\"confidence\":0.9,\"requires_execution\":false}");
        assertTrue(FoxConversationContextBuilder.isNodeContextSelection(semantic));
    }

    @Test public void isNodeContextSelectionFalseWhenNoNodesSelected() {
        SemanticRouteContract semantic = SemanticRouteContract.parse(
                "{\"intent\":\"node_context\",\"confidence\":0.9,\"requires_execution\":false}");
        assertFalse(FoxConversationContextBuilder.isNodeContextSelection(semantic));
    }

    @Test public void isNodeContextSelectionFalseWhenIntentIsSomethingElse() {
        SemanticRouteContract semantic = SemanticRouteContract.parse("{\"intent\":\"capability_query\","
                + "\"selected_nodes\":[\"app:finance\"],\"confidence\":0.9,\"requires_execution\":false}");
        assertFalse(FoxConversationContextBuilder.isNodeContextSelection(semantic));
    }

    @Test public void isNodeContextSelectionFalseWhenSemanticIsNull() {
        assertFalse(FoxConversationContextBuilder.isNodeContextSelection(null));
    }
}
