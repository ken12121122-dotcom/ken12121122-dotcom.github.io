package com.amin.pocketgba;

import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BrainTaskEnvelopeTest {
    @Test
    public void producesExactPrivateIssueEnvelopeWithClientStableIdentity() {
        BrainTaskEnvelope envelope = BrainTaskEnvelope.create(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "新增任務中心", "顯示任務與執行狀態。", "auto",
                List.of("android:ui", "github:issues"), 2,
                Instant.parse("2026-08-29T12:00:00Z"));

        String body = envelope.issueBody();
        assertTrue(body.startsWith("<!-- amin-brain-task:v1 -->\n```json\n"));
        assertTrue(body.endsWith("\n```"));
        JSONObject json = envelope.json();
        assertEquals("amin-brain-task-request", json.getString("format"));
        assertEquals("ken12121122-dotcom/ken12121122-dotcom.github.io",
                json.getJSONObject("target").getString("repository"));
        assertEquals("release/android", json.getJSONObject("target").getString("base_branch"));
        assertEquals("android-native/", json.getJSONObject("target")
                .getJSONArray("allowed_path_prefixes").getString(0));
        assertFalse(json.has("issue_number"));
    }

    @Test
    public void rejectsSecretsUnsupportedAgentAndMalformedCapability() {
        assertThrows(IllegalArgumentException.class, () -> create("OPENAI_API_KEY=sk-secret", "auto",
                List.of("android:ui"), 1));
        assertThrows(IllegalArgumentException.class, () -> create("正常需求", "other",
                List.of("android:ui"), 1));
        assertThrows(IllegalArgumentException.class, () -> create("正常需求", "auto",
                List.of("bad capability"), 1));
        assertThrows(IllegalArgumentException.class, () -> create("正常需求", "auto",
                List.of("android:ui"), 3));
    }

    private static BrainTaskEnvelope create(String specification, String agent,
                                             List<String> capabilities, int attempts) {
        return BrainTaskEnvelope.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "任務", specification, agent, capabilities, attempts, Instant.now());
    }
}
