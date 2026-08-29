package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BrainFeedState {
    private static final Set<String> NOTIFIABLE = Set.of("waiting_owner", "completed", "failed");
    private static final int MAX_SEEN = 256;

    private BrainFeedState() { }

    static Delta diff(JSONObject feed, Set<String> previouslySeen) {
        validate(feed);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (previouslySeen != null) seen.addAll(previouslySeen);
        List<Notification> fresh = new ArrayList<>();
        JSONArray notifications = feed.getJSONArray("notifications");
        for (int index = 0; index < notifications.length(); index++) {
            JSONObject value = notifications.optJSONObject(index);
            if (value == null) continue;
            String id = value.optString("notification_id", "").trim();
            String stableId = value.optString("stable_id", "").trim();
            String lifecycle = value.optString("lifecycle_status", "").trim();
            if (id.isEmpty() || stableId.isEmpty() || !NOTIFIABLE.contains(lifecycle)) continue;
            if (seen.add(id)) fresh.add(new Notification(id, stableId, lifecycle,
                    value.optString("title", lifecycle)));
        }
        while (seen.size() > MAX_SEEN) {
            String oldest = seen.iterator().next();
            seen.remove(oldest);
        }
        return new Delta(feed.getString("revision"), fresh, seen);
    }

    static String summary(JSONObject feed) {
        validate(feed);
        JSONArray tasks = feed.getJSONArray("tasks");
        JSONArray runs = feed.getJSONArray("runs");
        StringBuilder output = new StringBuilder();
        if (tasks.length() == 0) output.append("尚無手機任務。 ");
        for (int index = tasks.length() - 1; index >= 0 && index >= tasks.length() - 20; index--) {
            JSONObject task = tasks.optJSONObject(index);
            if (task == null) continue;
            if (output.length() > 0) output.append('\n');
            output.append("• ").append(task.optString("title", task.optString("stable_id", "TASK")))
                    .append(" · ").append(task.optString("lifecycle_status", "unknown"));
        }
        output.append("\n\nRUN：").append(runs.length()).append(" · Feed：")
                .append(feed.getString("revision"), 0, Math.min(10, feed.getString("revision").length()));
        return output.toString();
    }

    private static void validate(JSONObject feed) {
        if (feed == null || !"amin-brain-mobile-feed".equals(feed.optString("format", ""))
                || feed.optInt("version", -1) != 1 || feed.optString("revision", "").isBlank()
                || feed.optJSONArray("goals") == null || feed.optJSONArray("tasks") == null
                || feed.optJSONArray("runs") == null || feed.optJSONArray("notifications") == null) {
            throw new IllegalArgumentException("Brain feed 格式不正確。 ");
        }
    }

    static final class Notification {
        private final String id;
        private final String stableId;
        private final String lifecycleStatus;
        private final String title;
        Notification(String id, String stableId, String lifecycleStatus, String title) {
            this.id = id; this.stableId = stableId; this.lifecycleStatus = lifecycleStatus; this.title = title;
        }
        String id() { return id; }
        String stableId() { return stableId; }
        String lifecycleStatus() { return lifecycleStatus; }
        String title() { return title; }
    }

    static final class Delta {
        private final String revision;
        private final List<Notification> newNotifications;
        private final Set<String> seenNotificationIds;
        Delta(String revision, List<Notification> notifications, Set<String> seen) {
            this.revision = revision;
            this.newNotifications = Collections.unmodifiableList(new ArrayList<>(notifications));
            this.seenNotificationIds = Collections.unmodifiableSet(new LinkedHashSet<>(seen));
        }
        String revision() { return revision; }
        List<Notification> newNotifications() { return newNotifications; }
        Set<String> seenNotificationIds() { return seenNotificationIds; }
    }
}
