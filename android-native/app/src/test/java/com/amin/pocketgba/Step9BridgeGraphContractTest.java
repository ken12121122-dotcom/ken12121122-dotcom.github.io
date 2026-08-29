package com.amin.pocketgba;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class Step9BridgeGraphContractTest {
    @Test public void approvedBridgeDirectionsAreAccepted() {
        assertTrue(Step9BridgeGraphContract.isRegistered("TASK", "uses_knowledge", "KNOWLEDGE"));
        assertTrue(Step9BridgeGraphContract.isRegistered("RUN", "learned_from", "KNOWLEDGE"));
        assertTrue(Step9BridgeGraphContract.isRegistered("RUN", "generated", "MEMORY_CANDIDATE"));
        assertTrue(Step9BridgeGraphContract.isRegistered("MEMORY_CANDIDATE", "promoted_to", "KNOWLEDGE"));
        assertTrue(Step9BridgeGraphContract.isRegistered("PR", "provides_evidence_for", "KNOWLEDGE"));
        assertTrue(Step9BridgeGraphContract.isRegistered("GOAL", "requires_capability", "CAPABILITY"));
    }

    @Test public void reversedOrAdapterInventedSemanticsAreRejected() {
        assertFalse(Step9BridgeGraphContract.isRegistered("KNOWLEDGE", "learned_from", "RUN"));
        assertFalse(Step9BridgeGraphContract.isRegistered("TASK", "related_to", "KNOWLEDGE"));
        assertFalse(Step9BridgeGraphContract.isRegistered("RUN", "uses_knowledge", "KNOWLEDGE"));
    }

    @Test public void relationBatchHasOneCentralValidationPath() throws Exception {
        JSONArray approved = new JSONArray().put(relation("TASK", "uses_knowledge", "KNOWLEDGE"));
        JSONArray reversed = new JSONArray().put(relation("KNOWLEDGE", "learned_from", "RUN"));

        assertEquals("", Step9BridgeGraphContract.validationError(approved));
        assertEquals("UNREGISTERED_BRIDGE_RELATION", Step9BridgeGraphContract.validationError(reversed));
    }

    private static JSONObject relation(String sourceType, String semantic, String targetType) throws Exception {
        return new JSONObject().put("payload", new JSONObject()
                .put("source_type", sourceType)
                .put("relation_semantic", semantic)
                .put("target_type", targetType));
    }
}
