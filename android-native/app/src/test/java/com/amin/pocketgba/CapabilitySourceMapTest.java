package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class CapabilitySourceMapTest {
    private static JSONArray capabilities() throws Exception {
        return new JSONArray()
                .put(new JSONObject().put("id", "app:control-center"))
                .put(new JSONObject().put("id", "app:voice"))
                .put(new JSONObject().put("id", "app:update"));
    }

    private static JSONObject sourceGraph() throws Exception {
        return new JSONObject()
                .put("entities", new JSONArray()
                        .put(new JSONObject().put("id", "class:ControlCenterActivity").put("entityType", "class"))
                        .put(new JSONObject().put("id", "class:VoiceCommandActivity").put("entityType", "class"))
                        .put(new JSONObject().put("id", "class:UpdateHubActivity").put("entityType", "class")))
                .put("relations", new JSONArray()
                        .put(new JSONObject().put("id", "call:voice").put("from", "class:ControlCenterActivity").put("to", "class:VoiceCommandActivity").put("type", "calls"))
                        .put(new JSONObject().put("id", "call:update").put("from", "class:ControlCenterActivity").put("to", "class:UpdateHubActivity").put("type", "calls")));
    }

    private static JSONObject map() throws Exception {
        return new JSONObject().put("mappings", new JSONArray()
                .put(new JSONObject().put("id", "m1").put("capabilityId", "app:control-center").put("sourceId", "class:ControlCenterActivity"))
                .put(new JSONObject().put("id", "m2").put("capabilityId", "app:voice").put("sourceId", "class:VoiceCommandActivity"))
                .put(new JSONObject().put("id", "m3").put("capabilityId", "app:update").put("sourceId", "class:UpdateHubActivity")));
    }

    @Test public void sourceDependencyWithoutConnectBecomesReviewFinding() throws Exception {
        JSONArray registered = new JSONArray()
                .put(new JSONObject().put("from", "app:control-center").put("to", "app:update").put("type", "contains"));
        JSONObject result = CapabilitySourceMap.evaluate(map(), capabilities(), sourceGraph(), registered);
        assertTrue(result.getBoolean("valid"));
        JSONArray findings = result.getJSONArray("findings");
        assertEquals(1, findings.length());
        JSONObject finding = findings.getJSONObject(0);
        assertEquals("UNREGISTERED_LINK", finding.getString("type"));
        assertEquals("app:control-center", finding.getString("fromCapability"));
        assertEquals("app:voice", finding.getString("toCapability"));
        assertFalse(finding.getBoolean("autoRegister"));
        assertFalse(result.getJSONObject("auditPolicy").getBoolean("autoRegisterConnect"));
    }

    @Test public void registeredConnectSuppressesFinding() throws Exception {
        JSONArray registered = new JSONArray()
                .put(new JSONObject().put("from", "app:control-center").put("to", "app:voice"))
                .put(new JSONObject().put("from", "app:control-center").put("to", "app:update"));
        JSONObject result = CapabilitySourceMap.evaluate(map(), capabilities(), sourceGraph(), registered);
        assertTrue(result.getBoolean("valid"));
        assertEquals(0, result.getJSONArray("findings").length());
    }

    @Test public void missingSourceEndpointFailsHardValidation() throws Exception {
        JSONObject broken = new JSONObject().put("mappings", new JSONArray()
                .put(new JSONObject().put("id", "broken").put("capabilityId", "app:voice").put("sourceId", "class:Missing")));
        JSONObject result = CapabilitySourceMap.evaluate(broken, capabilities(), sourceGraph(), new JSONArray());
        assertFalse(result.getBoolean("valid"));
        assertTrue(result.getJSONArray("errors").getString(0).contains("mapping source missing"));
    }
}
