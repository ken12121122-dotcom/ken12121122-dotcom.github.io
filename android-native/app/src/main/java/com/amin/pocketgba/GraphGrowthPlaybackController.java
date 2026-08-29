package com.amin.pocketgba;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Replays only changed synchronized evidence into the existing single Graph renderer.
 * Scanner speed and visual reveal speed are intentionally decoupled.
 */
final class GraphGrowthPlaybackController {
    private static final long EVENT_DELAY_MS = 180L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int generation = 0;

    private GraphGrowthPlaybackController() { }

    static void play(Context context, JSONObject previous, JSONObject next) {
        if (context == null || next == null) return;
        final Context app = context.getApplicationContext();
        final int token = ++generation;
        final JSONObject base = unchangedBaseline(previous, next);
        final JSONArray nextEntities = next.optJSONArray("entities");
        final JSONArray nextRelations = next.optJSONArray("relations");
        final Set<String> changedEntities = changedIds(previous, next, "entities", "entityId");
        final Set<String> changedRelations = changedIds(previous, next, "relations", "relationId");

        if (changedEntities.isEmpty() && changedRelations.isEmpty()) {
            GraphGrowthPlaybackStore.clear();
            UnifiedGraphProvider.notifyChanged(app);
            return;
        }

        GraphGrowthPlaybackStore.set(base);
        UnifiedGraphProvider.notifyChanged(app);

        long delay = 0L;
        Set<String> visible = ids(base.optJSONArray("entities"), "entityId");
        if (nextEntities != null) {
            for (int i = 0; i < nextEntities.length(); i++) {
                JSONObject entity = nextEntities.optJSONObject(i);
                if (entity == null) continue;
                String id = idOf(entity, "entityId");
                if (!changedEntities.contains(id)) continue;
                delay += EVENT_DELAY_MS;
                final JSONObject eventEntity = copy(entity);
                MAIN.postDelayed(() -> {
                    if (token != generation) return;
                    JSONObject current = GraphGrowthPlaybackStore.currentOr(base);
                    upsert(current.optJSONArray("entities"), eventEntity, "entityId");
                    GraphGrowthPlaybackStore.set(current);
                    UnifiedGraphProvider.notifyChanged(app);
                    playTone(eventEntity);
                }, delay);
                visible.add(id);

                if (nextRelations != null) {
                    for (int r = 0; r < nextRelations.length(); r++) {
                        JSONObject relation = nextRelations.optJSONObject(r);
                        if (relation == null) continue;
                        String relationId = idOf(relation, "relationId");
                        if (!changedRelations.contains(relationId)) continue;
                        String from = clean(relation.optString("from", ""));
                        String to = clean(relation.optString("to", ""));
                        if (!visible.contains(from) || !visible.contains(to)) continue;
                        changedRelations.remove(relationId);
                        delay += EVENT_DELAY_MS;
                        final JSONObject eventRelation = copy(relation);
                        MAIN.postDelayed(() -> {
                            if (token != generation) return;
                            JSONObject current = GraphGrowthPlaybackStore.currentOr(base);
                            upsert(current.optJSONArray("relations"), eventRelation, "relationId");
                            GraphGrowthPlaybackStore.set(current);
                            UnifiedGraphProvider.notifyChanged(app);
                            playRelationTone(eventRelation);
                        }, delay);
                    }
                }
            }
        }

        if (nextRelations != null) {
            for (int i = 0; i < nextRelations.length(); i++) {
                JSONObject relation = nextRelations.optJSONObject(i);
                if (relation == null) continue;
                String relationId = idOf(relation, "relationId");
                if (!changedRelations.contains(relationId)) continue;
                delay += EVENT_DELAY_MS;
                final JSONObject eventRelation = copy(relation);
                MAIN.postDelayed(() -> {
                    if (token != generation) return;
                    JSONObject current = GraphGrowthPlaybackStore.currentOr(base);
                    upsert(current.optJSONArray("relations"), eventRelation, "relationId");
                    GraphGrowthPlaybackStore.set(current);
                    UnifiedGraphProvider.notifyChanged(app);
                    playRelationTone(eventRelation);
                }, delay);
            }
        }

        MAIN.postDelayed(() -> {
            if (token != generation) return;
            GraphGrowthPlaybackStore.clear();
            UnifiedGraphProvider.notifyChanged(app);
        }, delay + EVENT_DELAY_MS);
    }

    private static JSONObject unchangedBaseline(JSONObject previous, JSONObject next) {
        JSONObject out = copy(next);
        try {
            JSONArray entities = new JSONArray();
            JSONArray relations = new JSONArray();
            Set<String> changedEntities = changedIds(previous, next, "entities", "entityId");
            Set<String> changedRelations = changedIds(previous, next, "relations", "relationId");
            JSONArray prevEntities = previous == null ? null : previous.optJSONArray("entities");
            JSONArray prevRelations = previous == null ? null : previous.optJSONArray("relations");
            if (prevEntities != null) for (int i = 0; i < prevEntities.length(); i++) {
                JSONObject item = prevEntities.optJSONObject(i);
                if (item != null && !changedEntities.contains(idOf(item, "entityId"))) entities.put(copy(item));
            }
            if (prevRelations != null) for (int i = 0; i < prevRelations.length(); i++) {
                JSONObject item = prevRelations.optJSONObject(i);
                if (item != null && !changedRelations.contains(idOf(item, "relationId"))) relations.put(copy(item));
            }
            out.put("entities", entities);
            out.put("relations", relations);
        } catch (Exception ignored) { }
        return out;
    }

    private static Set<String> changedIds(JSONObject previous, JSONObject next, String arrayKey, String idKey) {
        Map<String, String> before = hashes(previous == null ? null : previous.optJSONArray(arrayKey), idKey);
        Map<String, String> after = hashes(next == null ? null : next.optJSONArray(arrayKey), idKey);
        Set<String> changed = new HashSet<>();
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String old = before.get(entry.getKey());
            if (old == null || !old.equals(entry.getValue())) changed.add(entry.getKey());
        }
        return changed;
    }

    private static Map<String, String> hashes(JSONArray values, String idKey) {
        Map<String, String> out = new HashMap<>();
        if (values == null) return out;
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) continue;
            String id = idOf(item, idKey);
            if (!id.isEmpty()) out.put(id, clean(item.optString("sourceHash", item.toString())));
        }
        return out;
    }

    private static Set<String> ids(JSONArray values, String idKey) {
        Set<String> out = new HashSet<>();
        if (values == null) return out;
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item != null) out.add(idOf(item, idKey));
        }
        return out;
    }

    private static void upsert(JSONArray values, JSONObject item, String idKey) {
        if (values == null || item == null) return;
        String id = idOf(item, idKey);
        for (int i = 0; i < values.length(); i++) {
            JSONObject old = values.optJSONObject(i);
            if (old != null && id.equals(idOf(old, idKey))) {
                try { values.put(i, copy(item)); } catch (Exception ignored) { }
                return;
            }
        }
        values.put(copy(item));
    }

    private static void playTone(JSONObject entity) {
        String verification = clean(entity == null ? "" : entity.optString("verification", ""));
        int tone = "gap".equals(verification) ? ToneGenerator.TONE_PROP_NACK : ToneGenerator.TONE_PROP_ACK;
        tone(tone, "gap".equals(verification) ? 90 : 55);
    }

    private static void playRelationTone(JSONObject relation) {
        String verification = clean(relation == null ? "" : relation.optString("verification", ""));
        tone("gap".equals(verification) ? ToneGenerator.TONE_PROP_NACK : ToneGenerator.TONE_PROP_BEEP2,
                "gap".equals(verification) ? 90 : 45);
    }

    private static void tone(int tone, int durationMs) {
        try {
            ToneGenerator generator = new ToneGenerator(AudioManager.STREAM_SYSTEM, 18);
            generator.startTone(tone, durationMs);
            MAIN.postDelayed(generator::release, durationMs + 30L);
        } catch (RuntimeException ignored) { }
    }

    private static String idOf(JSONObject item, String primary) {
        if (item == null) return "";
        return clean(item.optString(primary, item.optString("id", "")));
    }

    private static JSONObject copy(JSONObject value) {
        if (value == null) return GraphSyncEngine.empty();
        try { return new JSONObject(value.toString()); }
        catch (Exception ignored) { return GraphSyncEngine.empty(); }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
