package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class CapabilityCandidateValidatorTest {
    private final CapabilityCandidateValidator validator = new CapabilityCandidateValidator();

    @Test public void acceptsStructurallyValidNewActionAndWarnsForSemanticReview() throws Exception {
        JSONObject candidate = CapabilityCandidateProtocol.createCandidate(new JSONObject()
                .put("entity_type", "action")
                .put("owner_node_id", "app:test")
                .put("action", "EXPORT_MARKDOWN"), "test");
        CapabilityCandidateValidator.Result result = validator.validate(null, null, candidate);
        assertTrue(result.valid);
        assertTrue(result.warnings.toString().contains("NEW_ACTION_REQUIRES_SEMANTIC_REVIEW"));
    }

    @Test public void rejectsActionWithoutOwner() throws Exception {
        JSONObject candidate = CapabilityCandidateProtocol.createCandidate(new JSONObject()
                .put("entity_type", "action")
                .put("action", "refresh"), "test");
        CapabilityCandidateValidator.Result result = validator.validate(null, null, candidate);
        assertFalse(result.valid);
        assertEquals("OWNER_NODE_REQUIRED", result.code);
    }

    @Test public void rejectsUnsupportedConnectRelationship() throws Exception {
        JSONObject candidate = CapabilityCandidateProtocol.createCandidate(new JSONObject()
                .put("entity_type", "connect")
                .put("source_node_id", "app:a")
                .put("target_node_id", "app:b")
                .put("relationship_type", "mystery_link"), "test");
        CapabilityCandidateValidator.Result result = validator.validate(null, null, candidate);
        assertFalse(result.valid);
        assertEquals("RELATIONSHIP_INVALID", result.code);
    }

    @Test public void acceptsKnownRelationshipConnect() throws Exception {
        JSONObject candidate = CapabilityCandidateProtocol.createCandidate(new JSONObject()
                .put("entity_type", "connect")
                .put("source_node_id", "app:a")
                .put("target_node_id", "app:b")
                .put("relationship_type", "uses"), "test");
        CapabilityCandidateValidator.Result result = validator.validate(null, null, candidate);
        assertTrue(result.valid);
    }
}
