package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class CapabilityGraphContractTest {
    @Test public void acceptsAllStep11TypesWithoutEnablingExecution() throws Exception {
        String[] types = {CapabilityGraphContract.CAPABILITY, CapabilityGraphContract.SKILL,
                CapabilityGraphContract.WORKFLOW, CapabilityGraphContract.TOOL,
                CapabilityGraphContract.CONNECTOR, CapabilityGraphContract.COMMAND,
                CapabilityGraphContract.CERTIFICATION};
        for (String type : types) {
            JSONObject record = CapabilityGraphContract.entity("cap:" + type.toLowerCase(), type,
                    attributes(type));
            JSONObject payload = record.getJSONObject("payload");
            assertEquals(type, payload.getString("entity_type"));
            assertEquals("none", payload.getString("autonomy_level"));
            assertFalse(payload.getBoolean("execution_enabled"));
        }
    }

    @Test public void stableIdentityDoesNotDependOnMutableTitle() throws Exception {
        JSONObject before = CapabilityGraphContract.entity("app:graph", CapabilityGraphContract.CAPABILITY,
                attributes("Old title").put("title", "Old title"));
        JSONObject after = CapabilityGraphContract.entity("app:graph", CapabilityGraphContract.CAPABILITY,
                attributes("New title").put("title", "New title"));
        assertEquals(before.getString("stable_id"), after.getString("stable_id"));
        assertNotEquals(before.getString("content_fingerprint"), after.getString("content_fingerprint"));
    }

    @Test public void certificationRequiresEvidenceAndHumanApproval() throws Exception {
        try {
            CapabilityGraphContract.entity("skill:unsafe", CapabilityGraphContract.SKILL,
                    attributes("skill").put("lifecycle_status", "certified")
                            .put("certification_status", "certified"));
            fail("Expected certification gate");
        } catch (IllegalArgumentException expected) {
            assertEquals("CERTIFICATION_REQUIRES_APPROVAL_AND_EVIDENCE", expected.getMessage());
        }

        JSONObject certified = CapabilityGraphContract.entity("skill:safe", CapabilityGraphContract.SKILL,
                attributes("skill").put("lifecycle_status", "certified")
                        .put("certification_status", "certified")
                        .put("certification_evidence", evidence("test:42"))
                        .put("certification_scope", certificationScope())
                        .put("human_approval", new JSONObject().put("review_status", "approved")));
        assertEquals("certified", certified.getJSONObject("payload").getString("certification_status"));
        assertEquals("time_bounded", certified.getJSONObject("payload").getString("trust_status"));
        assertEquals(2000L, certified.getJSONObject("payload").getLong("trust_expires_at"));
    }

    @Test public void certificationRequiresVersionedTimeBoundedScope() throws Exception {
        try {
            CapabilityGraphContract.entity("skill:no-scope", CapabilityGraphContract.SKILL,
                    attributes("skill").put("lifecycle_status", "certified")
                            .put("certification_status", "certified")
                            .put("certification_evidence", evidence("test:42"))
                            .put("human_approval", new JSONObject().put("review_status", "approved")));
            fail("Expected certification scope gate");
        } catch (IllegalArgumentException expected) {
            assertEquals("CERTIFICATION_SCOPE_REQUIRED", expected.getMessage());
        }
    }

    @Test public void certificationRelationsAreReadOnlyAndExplicitlyRegistered() throws Exception {
        JSONObject relation = CapabilityGraphContract.relation("rel:cert", "skill:safe",
                CapabilityGraphContract.CAPABILITY, "certified_by", "cert:42",
                CapabilityGraphContract.CERTIFICATION, new JSONObject());
        assertFalse(relation.getJSONObject("payload").getBoolean("execution_enabled"));
    }

    @Test public void rejectsUninspectableSourceAndCertificationEvidence() throws Exception {
        try {
            CapabilityGraphContract.entity("tool:no-source", CapabilityGraphContract.TOOL,
                    attributes("tool").put("source_records", new JSONArray().put("unstructured")));
            fail("Expected source evidence gate");
        } catch (IllegalArgumentException expected) {
            assertEquals("CAPABILITY_SOURCE_REQUIRED", expected.getMessage());
        }

        try {
            CapabilityGraphContract.entity("skill:no-evidence-source", CapabilityGraphContract.SKILL,
                    attributes("skill").put("lifecycle_status", "certified")
                            .put("certification_status", "certified")
                            .put("certification_evidence", new JSONArray().put("unstructured"))
                            .put("certification_scope", certificationScope())
                            .put("human_approval", new JSONObject().put("review_status", "approved")));
            fail("Expected structured certification evidence gate");
        } catch (IllegalArgumentException expected) {
            assertEquals("CERTIFICATION_REQUIRES_APPROVAL_AND_EVIDENCE", expected.getMessage());
        }
    }

    @Test public void unregisteredRelationIsRejected() throws Exception {
        try {
            CapabilityGraphContract.relation("rel:1", "cap:a", CapabilityGraphContract.CAPABILITY,
                    "silently_executes", "cap:b", CapabilityGraphContract.CAPABILITY, new JSONObject());
            fail("Expected relation rejection");
        } catch (IllegalArgumentException expected) {
            assertEquals("UNREGISTERED_CAPABILITY_RELATION", expected.getMessage());
        }
    }

    @Test public void batchUsesExistingWorkScopeAndInspectableOwnership() throws Exception {
        JSONArray entities = new JSONArray().put(CapabilityGraphContract.entity(
                "app:graph", CapabilityGraphContract.CAPABILITY, attributes("Graph")));
        JSONObject batch = CapabilityGraphContract.batch("node_registry", "capabilities", "r1",
                entities, new JSONArray(), 1000L);
        assertEquals("work", batch.getString("graph_scope"));
        assertEquals("amin_registry", batch.getJSONObject("sync_owner").getString("provider"));
        assertEquals("capabilities", batch.getString("sync_partition"));
        assertTrue(batch.getJSONObject("provenance").getBoolean("read_only"));
    }

    @Test public void batchValidationCannotBypassExecutionBoundary() throws Exception {
        JSONObject unsafe = CapabilityGraphContract.entity("tool:one", CapabilityGraphContract.TOOL,
                attributes("tool"));
        unsafe.getJSONObject("payload").put("execution_enabled", true);
        try {
            CapabilityGraphContract.batch("test", "tools", "r1",
                    new JSONArray().put(unsafe), new JSONArray(), 1000L);
            fail("Expected execution boundary");
        } catch (IllegalArgumentException expected) {
            assertEquals("CAPABILITY_EXECUTION_NOT_ALLOWED", expected.getMessage());
        }
    }

    private static JSONObject attributes(String source) throws Exception {
        return new JSONObject()
                .put("title", source)
                .put("lifecycle_status", "approved")
                .put("review_status", "approved")
                .put("certification_status", "not_certified")
                .put("source_records", new JSONArray().put(new JSONObject()
                        .put("source_id", source).put("authority", "test")));
    }

    private static JSONObject certificationScope() throws Exception {
        return new JSONObject().put("scope_id", "scope:safe").put("version", "1")
                .put("issued_at", 1000L).put("expires_at", 2000L);
    }

    private static JSONArray evidence(String sourceId) throws Exception {
        return new JSONArray().put(new JSONObject()
                .put("source_id", sourceId).put("authority", "test"));
    }
}
