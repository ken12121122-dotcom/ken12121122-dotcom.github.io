package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds only the Capability context needed by the first P0 conversation slice. */
final class ReadOnlyCapabilityContextBuilder {
    private ReadOnlyCapabilityContextBuilder() { }

    static JSONObject build(Context context, NodeMetadataStore nodeStore) {
        try {
            JSONObject state = new CapabilityInventoryStore(context).refresh(nodeStore);
            JSONArray records = activeEntities(state.optJSONArray("entities"));
            int direct = 0;
            int bridge = 0;
            JSONObject typeCounts = new JSONObject();
            for (int i = 0; i < records.length(); i++) {
                JSONObject payload = records.getJSONObject(i).getJSONObject("payload");
                String type = payload.optString("entity_type", CapabilityGraphContract.CAPABILITY);
                typeCounts.put(type, typeCounts.optInt(type, 0) + 1);
                if (isChatAddressable(payload)) direct += 1;
                else bridge += 1;
            }
            return new JSONObject()
                    .put("format", ConversationCapabilityContract.CONTEXT_FORMAT)
                    .put("version", ConversationCapabilityContract.VERSION)
                    .put("generated_at", System.currentTimeMillis())
                    .put("boundary", ConversationCapabilityContract.readOnlyBoundary())
                    .put("resolution_order", ConversationCapabilityContract.resolutionOrder())
                    .put("summary", new JSONObject()
                            .put("total", records.length())
                            .put("chat_addressable", direct)
                            .put("bridge_required", bridge)
                            .put("type_counts", typeCounts))
                    .put("capabilities", records)
                    .put("source_records", new JSONArray()
                            .put(new JSONObject().put("source_id", "node_registry")
                                    .put("authority", "android_manifest"))
                            .put(new JSONObject().put("source_id", "command_catalog")
                                    .put("authority", "voice_command_catalog")))
                    .put("unresolved_gaps", new JSONArray()
                            .put("github_work_context_deferred")
                            .put("agent_status_provider_unavailable"));
        } catch (Exception error) {
            try {
                return new JSONObject()
                        .put("format", ConversationCapabilityContract.CONTEXT_FORMAT)
                        .put("version", ConversationCapabilityContract.VERSION)
                        .put("boundary", ConversationCapabilityContract.readOnlyBoundary())
                        .put("resolution_order", ConversationCapabilityContract.resolutionOrder())
                        .put("summary", new JSONObject().put("total", 0)
                                .put("chat_addressable", 0).put("bridge_required", 0)
                                .put("type_counts", new JSONObject()))
                        .put("capabilities", new JSONArray())
                        .put("source_records", new JSONArray())
                        .put("unresolved_gaps", new JSONArray()
                                .put("capability_context_build_failed:" + error.getClass().getSimpleName()));
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }

    private static JSONArray activeEntities(JSONArray source) throws Exception {
        JSONArray out = new JSONArray();
        if (source == null) return out;
        for (int i = 0; i < source.length(); i++) {
            JSONObject record = source.optJSONObject(i);
            if (record == null || record.optJSONObject("payload") == null
                    || "stale".equals(record.optString("sync_status", "active"))) continue;
            out.put(new JSONObject(record.toString()));
        }
        return out;
    }

    static boolean isChatAddressable(JSONObject payload) {
        if (payload == null) return false;
        if (CapabilityGraphContract.COMMAND.equals(payload.optString("entity_type", ""))) return true;
        JSONObject input = payload.optJSONObject("input_context");
        if (input != null && "managed_md".equals(input.optString("mode", ""))
                && !input.optString("context_node_id", "").trim().isEmpty()) return true;
        JSONObject voice = payload.optJSONObject("voice");
        return voice != null && voice.optBoolean("enabled", false);
    }
}
