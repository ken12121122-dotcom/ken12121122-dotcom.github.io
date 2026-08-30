package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Persists only non-secret read-only Capability Graph state produced by the shared merge kernel. */
final class CapabilityInventoryStore {
    private static final String PREFS = "amin_capability_inventory";
    private static final String KEY_STATE = "shared_state";
    private final Context context;
    private final SharedPreferences prefs;

    CapabilityInventoryStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    JSONObject refresh(NodeMetadataStore nodeStore) {
        JSONObject previous = current();
        long observedAt = System.currentTimeMillis();
        try {
            JSONObject registry = new JSONObject(NodeRegistry.registryJson(context, nodeStore));
            JSONObject edges = new JSONObject(NodeRegistry.typedEdgesJson(context, nodeStore));
            JSONObject registryResult = SharedGraphSyncKernel.apply(previous,
                    CapabilityInventoryAdapter.registryBatch(registry, edges, observedAt), observedAt);
            if (!registryResult.optBoolean("success", false)) return previous;
            JSONObject registryState = registryResult.getJSONObject("state");

            JSONArray commands = commandInventory();
            JSONObject commandResult = SharedGraphSyncKernel.apply(
                    registryState,
                    CapabilityInventoryAdapter.commandBatch(commands, observedAt), observedAt);
            if (!commandResult.optBoolean("success", false)) {
                prefs.edit().putString(KEY_STATE, registryState.toString()).apply();
                return new JSONObject(registryState.toString());
            }

            JSONObject state = commandResult.getJSONObject("state");
            prefs.edit().putString(KEY_STATE, state.toString()).apply();
            return new JSONObject(state.toString());
        } catch (Exception ignored) {
            return previous;
        }
    }

    JSONObject current() {
        try {
            String saved = prefs.getString(KEY_STATE, "");
            if (!saved.isEmpty()) {
                JSONObject state = new JSONObject(saved);
                if (SharedGraphSyncKernel.STATE_FORMAT.equals(state.optString("format", ""))) return state;
            }
        } catch (Exception ignored) { }
        return SharedGraphSyncKernel.emptyState();
    }

    private JSONArray commandInventory() throws Exception {
        JSONArray out = new JSONArray();
        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            out.put(new JSONObject()
                    .put("id", command.getId())
                    .put("title", command.getTitle())
                    .put("description", command.getDescription())
                    .put("action", command.getAction())
                    .put("phrases", new JSONArray(command.getPhrases()))
                    .put("requires_accessibility", command.requiresAccessibility())
                    .put("status", "active")
                    .put("authority", "voice_command_catalog"));
        }
        JSONArray dynamic = new DynamicCommandStore(context).registered();
        for (int i = 0; i < dynamic.length(); i++) {
            JSONObject command = dynamic.optJSONObject(i);
            if (command == null) continue;
            JSONObject copy = new JSONObject(command.toString());
            copy.put("authority", "dynamic_command_registry");
            out.put(copy);
        }
        return out;
    }
}
