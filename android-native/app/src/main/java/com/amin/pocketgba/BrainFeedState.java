package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BrainFeedState {
    private static final Set<String> NOTIFIABLE = new LinkedHashSet<>(
            Arrays.asList("waiting_owner", "completed", "failed"));
    private static final int MAX_SEEN = 256;

    private BrainFeedState() { }

    static Delta diff(JSONObject feed, Set<String> previouslySeen) {
        validate(feed);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (previouslySeen != null) seen.addAll(previouslySeen);
        List<Notification> fresh = new ArrayList<>();
        JSONArray notifications = feed.optJSONArray("notifications");
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
        return new Delta(feed.optString("revision", ""), fresh, seen);
    }

    static String summary(JSONObject feed) {
        validate(feed);
        JSONArray tasks = feed.optJSONArray("tasks");
        JSONArray runs = feed.optJSONArray("runs");
        StringBuilder output = new StringBuilder();
        if (tasks.length() == 0) output.append("尚無手機任務。 ");
        for (int index = tasks.length() - 1; index >= 0 && index >= tasks.length() - 20; index--) {
            JSONObject task = tasks.optJSONObject(index);
            if (task == null) continue;
            if (output.length() > 0) output.append('\n');
            output.append("• ").append(task.optString("title", task.optString("stable_id", "TASK")))
                    .append(" · ").append(task.optString("lifecycle_status", "unknown"));
        }
        JSONObject capabilityRuntime = feed.optJSONObject("capability_runtime");
        if (capabilityRuntime != null) {
            JSONArray capabilities = capabilityRuntime.optJSONArray("capabilities");
            JSONArray certifications = capabilityRuntime.optJSONArray("certifications");
            int active = 0;
            if (certifications != null) {
                for (int index = 0; index < certifications.length(); index++) {
                    JSONObject certification = certifications.optJSONObject(index);
                    if (certification != null && "active".equals(certification.optString("status", ""))) active++;
                }
            }
            output.append("\n\n能力：").append(capabilities == null ? 0 : capabilities.length())
                    .append(" · 有效認證：").append(active)
                    .append(" · 訓練通過：").append(capabilityRuntime.optInt("passed_training_records", 0));
        }
        String revision = feed.optString("revision", "");
        output.append("\n\nRUN：").append(runs.length()).append(" · Feed：")
                .append(revision, 0, Math.min(10, revision.length()));
        return output.toString();
    }

    private static void validate(JSONObject feed) {
        if (feed == null || !"amin-brain-mobile-feed".equals(feed.optString("format", ""))
                || feed.optInt("version", -1) != 1 || feed.optString("revision", "").trim().isEmpty()
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
