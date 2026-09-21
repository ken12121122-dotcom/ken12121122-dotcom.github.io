package com.amin.pocketgba;

import android.content.Context;

import com.fox.app.data.profile.KnowledgeProfile;
import com.fox.app.data.profile.KnowledgeProfileStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Persistent selector for the graph "save slot" shown by AMIN WIKI.
 *
 * AMIN_SYSTEM is the existing GitHub/system graph. Every FOX KnowledgeProfile
 * is exposed as another selectable graph profile without duplicating its source
 * path or sync configuration.
 */
final class GraphProfileStore {
    static final String SYSTEM_PROFILE_ID = "AMIN_SYSTEM";

    private static final String PREFS = "amin_graph_profiles";
    private static final String KEY_ACTIVE = "active_graph_profile";

    private final Context appContext;
    private final KnowledgeProfileStore knowledgeProfiles;

    GraphProfileStore(Context context) {
        appContext = context.getApplicationContext();
        knowledgeProfiles = new KnowledgeProfileStore(appContext);
    }

    String activeProfileId() {
        String requested = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE, SYSTEM_PROFILE_ID);
        return isValid(requested) ? requested : SYSTEM_PROFILE_ID;
    }

    boolean setActiveProfileId(String profileId) {
        String clean = profileId == null ? "" : profileId.trim();
        if (!isValid(clean)) return false;
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE, clean)
                .apply();
        return true;
    }

    boolean isSystem(String profileId) {
        return SYSTEM_PROFILE_ID.equals(profileId);
    }

    boolean isKnowledge(String profileId) {
        if (profileId == null || profileId.isEmpty()) return false;
        return knowledgeProfiles.profile(profileId) != null;
    }

    JSONObject profilesJson() {
        JSONArray profiles = new JSONArray();
        try {
            profiles.put(new JSONObject()
                    .put("id", SYSTEM_PROFILE_ID)
                    .put("name", "AMIN_SYSTEM")
                    .put("type", "github")
                    .put("sourceLabel", "GitHub / Runtime / Registry")
                    .put("configured", true));

            List<KnowledgeProfile> knowledge = knowledgeProfiles.listProfiles();
            for (KnowledgeProfile profile : knowledge) {
                profiles.put(new JSONObject()
                        .put("id", profile.getId())
                        .put("name", profile.getName())
                        .put("type", "fox")
                        .put("sourceLabel", profile.getSourceLabel() == null ? "" : profile.getSourceLabel())
                        .put("configured", profile.getTreeUri() != null && !profile.getTreeUri().trim().isEmpty()));
            }

            return new JSONObject()
                    .put("format", "amin-graph-profiles")
                    .put("version", 1)
                    .put("activeProfileId", activeProfileId())
                    .put("profiles", profiles);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private boolean isValid(String profileId) {
        return SYSTEM_PROFILE_ID.equals(profileId) || knowledgeProfiles.profile(profileId) != null;
    }
}
