package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CapabilityCandidateProtocolTest {
    @Test public void classifiesNodeFromCapabilityMetadata() throws Exception {
        JSONObject proposal = new JSONObject()
                .put("capability_id", "app:test")
                .put("route", "amin-test://open");
        assertEquals(CapabilityCandidateProtocol.TYPE_NODE, CapabilityCandidateProtocol.classify(proposal));
    }

    @Test public void classifiesActionOnlyWhenItHasOwner() throws Exception {
        JSONObject proposal = new JSONObject()
                .put("owner_node_id", "app:test")
                .put("action", "refresh");
        assertEquals(CapabilityCandidateProtocol.TYPE_ACTION, CapabilityCandidateProtocol.classify(proposal));
    }

    @Test public void classifiesCommandBeforeGenericAction() throws Exception {
        JSONObject proposal = new JSONObject()
                .put("command_id", "refresh_page")
                .put("action", "REFRESH")
                .put("phrases", new JSONArray().put("重新整理"));
        assertEquals(CapabilityCandidateProtocol.TYPE_COMMAND, CapabilityCandidateProtocol.classify(proposal));
    }

    @Test public void classifiesConnectFromSourceTargetAndRelationship() throws Exception {
        JSONObject proposal = new JSONObject()
                .put("source_node_id", "app:a")
                .put("target_node_id", "app:b")
                .put("relationship_type", "opens");
        assertEquals(CapabilityCandidateProtocol.TYPE_CONNECT, CapabilityCandidateProtocol.classify(proposal));
    }

    @Test public void newActionRequiresSemanticReviewButKnownActionDoesNot() throws Exception {
        JSONObject known = CapabilityCandidateProtocol.createCandidate(new JSONObject()
                .put("entity_type", "action")
                .put("owner_node_id", "app:test")
                .put("action", "SYSTEM_HOME"), "test");
        JSONObject fresh = CapabilityCandidateProtocol.createCandidate(new JSONObject()
                .put("entity_type", "action")
                .put("owner_node_id", "app:test")
                .put("action", "EXPORT_MARKDOWN"), "test");
        assertFalse(known.optBoolean("semantic_review_required", true));
        assertTrue(fresh.optBoolean("semantic_review_required", false));
    }
}
