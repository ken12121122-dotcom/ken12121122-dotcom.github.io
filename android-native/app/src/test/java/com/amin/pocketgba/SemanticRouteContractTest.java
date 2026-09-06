package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SemanticRouteContractTest {
    @Test public void usableWhenConfidentAndNoExecutionRequested() {
        SemanticRouteContract result = SemanticRouteContract.parse("{"
                + "\"intent\":\"capability_query\",\"selected_nodes\":[\"app:fox-chat\"],"
                + "\"selected_capabilities\":[\"capability:repo-search\"],\"required_context\":[\"governance\"],"
                + "\"confidence\":0.91,\"unresolved_gaps\":[],\"requires_execution\":false}");
        assertTrue(result.isUsable());
        assertTrue(result.isCapabilityQuery());
        assertEquals(1, result.selectedNodes.size());
        assertEquals("app:fox-chat", result.selectedNodes.get(0));
    }

    @Test public void notUsableWhenConfidenceBelowThreshold() {
        SemanticRouteContract result = SemanticRouteContract.parse(
                "{\"intent\":\"capability_query\",\"confidence\":0.4,\"requires_execution\":false}");
        assertFalse(result.isUsable());
    }

    @Test public void neverUsableWhenModelRequestsExecutionEvenAtHighConfidence() {
        SemanticRouteContract result = SemanticRouteContract.parse(
                "{\"intent\":\"capability_query\",\"confidence\":0.99,\"requires_execution\":true}");
        assertFalse(result.isUsable());
    }

    @Test public void missingRequiresExecutionFieldDefaultsToUnusable() {
        SemanticRouteContract result = SemanticRouteContract.parse(
                "{\"intent\":\"capability_query\",\"confidence\":0.95}");
        assertFalse(result.isUsable());
    }

    @Test public void parsesJsonWrappedInProseOrCodeFence() {
        SemanticRouteContract result = SemanticRouteContract.parse("這是結果：\n```json\n"
                + "{\"intent\":\"other\",\"confidence\":0.8,\"requires_execution\":false}\n```");
        assertTrue(result.isUsable());
        assertFalse(result.isCapabilityQuery());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsResponseWithoutAnyJsonObject() {
        SemanticRouteContract.parse("抱歉，我不知道怎麼回答。");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullResponse() {
        SemanticRouteContract.parse(null);
    }

    @Test public void systemPromptForbidsExecutionAndRequestsJsonOnly() {
        String prompt = SemanticRouteContract.systemPrompt();
        assertTrue(prompt.contains("requires_execution"));
        assertTrue(prompt.contains("false"));
        assertTrue(prompt.contains("capability_query"));
    }
}
