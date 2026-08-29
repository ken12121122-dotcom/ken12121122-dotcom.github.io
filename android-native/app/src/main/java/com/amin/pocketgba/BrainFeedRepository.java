package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

final class BrainFeedRepository {
    private static final String STORE = "amin_brain_feed_state";
    private static final String ETAG = "etag";
    private static final String FEED = "feed_json";
    private static final String SEEN = "seen_notification_ids";
    private final Context context;
    private final SharedPreferences preferences;

    BrainFeedRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    synchronized Refresh refresh(boolean emitNotifications) throws Exception {
        GitHubBrainApi api = new BrainAuthSession(context).requireVerifiedApi();
        GitHubBrainApi.FeedResponse response = api.fetchMobileFeed(preferences.getString(ETAG, ""));
        if (!response.changed()) {
            JSONObject cached = cachedFeed();
            return new Refresh(false, cached, cached == null ? "尚無快取狀態。 " : BrainFeedState.summary(cached));
        }
        JSONObject feed = response.feed();
        BrainFeedState.Delta delta = BrainFeedState.diff(feed, seen());
        JSONArray seenJson = new JSONArray();
        for (String id : delta.seenNotificationIds()) seenJson.put(id);
        boolean saved = preferences.edit()
                .putString(ETAG, response.etag())
                .putString(FEED, feed.toString())
                .putString(SEEN, seenJson.toString())
                .commit();
        if (!saved) throw new IllegalStateException("無法儲存 Brain feed 狀態。 ");
        if (emitNotifications) {
            for (BrainFeedState.Notification notification : delta.newNotifications()) {
                BrainNotificationCenter.notify(context, notification);
            }
        }
        return new Refresh(true, feed, BrainFeedState.summary(feed));
    }

    synchronized JSONObject cachedFeed() {
        String raw = preferences.getString(FEED, "");
        if (raw == null || raw.isBlank()) return null;
        try { return new JSONObject(raw); }
        catch (Exception error) { return null; }
    }

    synchronized void clear() { preferences.edit().clear().commit(); }

    private Set<String> seen() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String raw = preferences.getString(SEEN, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                String value = array.optString(index, "");
                if (!value.isBlank()) values.add(value);
            }
        } catch (Exception ignored) { }
        return values;
    }

    static final class Refresh {
        private final boolean changed;
        private final JSONObject feed;
        private final String summary;
        Refresh(boolean changed, JSONObject feed, String summary) {
            this.changed = changed;
            this.feed = BrainJson.copy(feed);
            this.summary = summary;
        }
        boolean changed() { return changed; }
        JSONObject feed() { return BrainJson.copy(feed); }
        String summary() { return summary; }
    }
}
