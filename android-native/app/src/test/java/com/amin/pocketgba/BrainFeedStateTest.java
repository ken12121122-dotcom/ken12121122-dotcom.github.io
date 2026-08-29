package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BrainFeedStateTest {
    @Test
    public void changedTerminalLifecycleNotifiesAtMostOncePerRevision() {
        JSONObject feed = feed("rev-1", new JSONObject()
                .put("notification_id", "notice-1").put("kind", "task")
                .put("stable_id", "amin:task:1").put("lifecycle_status", "waiting_owner")
                .put("title", "任務一"));
        BrainFeedState.Delta first = BrainFeedState.diff(feed, Set.of());
        assertEquals(1, first.newNotifications().size());
        BrainFeedState.Delta second = BrainFeedState.diff(feed, first.seenNotificationIds());
        assertEquals(0, second.newNotifications().size());
        assertEquals("rev-1", second.revision());
    }

    @Test
    public void ignoresNonTerminalAndMalformedNotifications() {
        JSONObject feed = feed("rev-2",
                new JSONObject().put("notification_id", "running")
                        .put("stable_id", "amin:run:1").put("lifecycle_status", "running"),
                new JSONObject().put("stable_id", "amin:run:2").put("lifecycle_status", "failed"));
        BrainFeedState.Delta delta = BrainFeedState.diff(feed, Set.of());
        assertTrue(delta.newNotifications().isEmpty());
    }

    private static JSONObject feed(String revision, JSONObject... notifications) {
        JSONArray values = new JSONArray();
        for (JSONObject notification : notifications) values.put(notification);
        return new JSONObject().put("format", "amin-brain-mobile-feed").put("version", 1)
                .put("revision", revision).put("goals", new JSONArray()).put("tasks", new JSONArray())
                .put("runs", new JSONArray()).put("notifications", values);
    }
}
