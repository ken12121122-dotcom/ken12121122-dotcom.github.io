package com.amin.pocketgba;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** Synthetic fox chat comments for tests (no real chat content). */
final class Gen2FoxChatTestData {
    private Gen2FoxChatTestData() { }

    static JSONObject comment(long id, String login, String body) throws Exception {
        return new JSONObject().put("id", id).put("body", body)
                .put("user", new JSONObject().put("login", login))
                .put("created_at", "2026-10-07T03:0" + id + ":00Z").put("updated_at", "2026-10-07T03:0" + id + ":00Z");
    }

    static String fox(String json) {
        return "…\n\n<!-- gen2-fox:" + java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)) + " -->";
    }
}
