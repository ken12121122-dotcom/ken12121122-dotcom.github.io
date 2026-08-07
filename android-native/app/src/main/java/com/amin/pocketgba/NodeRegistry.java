package com.amin.pocketgba;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Central runtime registry for graph capabilities. Manifest metadata remains the authority. */
final class NodeRegistry {
    static final String META_VISIBLE = "amin.graph.visible";
    static final String META_ID = "amin.graph.id";
    static final String META_CAPABILITY = "amin.graph.capability_id";
    static final String META_TITLE = "amin.graph.title";
    static final String META_DESCRIPTION = "amin.graph.description";
    static final String META_PARENT = "amin.graph.parent";
    static final String META_ROUTE = "amin.graph.route";
    static final String META_TYPE = "amin.graph.node_type";
    static final String META_ACTIONS = "amin.graph.actions";
    static final String META_INPUT = "amin.graph.input_contract";
    static final String META_OUTPUT = "amin.graph.output_contract";
    static final String META_VOICE_ENABLED = "amin.graph.voice_enabled";
    static final String META_VOICE_ALIASES = "amin.graph.voice_aliases";
    static final String META_STATUS = "amin.graph.status";
    static final String META_VERSION = "amin.graph.version";
    static final String META_STORAGE_ADAPTER = "amin.graph.storage_adapter";
    static final String META_STORAGE_SOURCE = "amin.graph.storage_source_id";
    static final String META_STORAGE_TABLE = "amin.graph.storage_table";

    static final class Match {
        final JSONObject node;
        final String alias;
        Match(JSONObject node, String alias) { this.node = node; this.alias = alias; }
    }

    private NodeRegistry() {}

    static String navigationJson(Context context) {
        JSONArray pages = new JSONArray();
        try {
            PackageManager pm = context.getPackageManager();
            ActivityInfo[] infos = pm.getPackageInfo(
                    context.getPackageName(), PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA).activities;
            if (infos != null) {
                for (ActivityInfo info : infos) {
                    Bundle m = info.metaData;
                    if (m == null || !m.getBoolean(META_VISIBLE, false)) continue;
                    JSONObject p = pageFromMetadata(info, m);
                    pages.put(p);
                }
            }
        } catch (Exception ignored) {}
        try {
            JSONObject root = new JSONObject();
            root.put("format", "amin-app-navigation");
            root.put("version", 3);
            root.put("rootId", "app-core");
            root.put("rootCapabilityId", "app:app-core");
            root.put("rootTitle", "Amin Pocket");
            root.put("rootDescription", "Amin Pocket capability root");
            root.put("rootRoute", "amin-home://open");
            root.put("pages", pages);
            return root.toString();
        } catch (Exception e) {
            return "{\"pages\":[]}";
        }
    }

    static String registryJson(Context context, NodeMetadataStore overrides) {
        String base = GraphContract.nodeRegistryJson(navigationJson(context));
        if (overrides == null) return base;
        return overrides.applyToRegistry(base);
    }

    static String typedEdgesJson(Context context, NodeMetadataStore overrides) {
        String base = GraphContract.typedEdgesJson(navigationJson(context));
        if (overrides == null) return base;
        return overrides.mergeEdges(base);
    }

    static JSONObject findNode(Context context, NodeMetadataStore overrides, String nodeOrCapabilityId) {
        String needle = clean(nodeOrCapabilityId);
        if (needle.isEmpty()) return null;
        try {
            JSONArray nodes = new JSONObject(registryJson(context, overrides)).optJSONArray("nodes");
            if (nodes == null) return null;
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject n = nodes.optJSONObject(i);
                if (n == null) continue;
                if (needle.equals(n.optString("node_id")) || needle.equals(n.optString("nodeId"))
                        || needle.equals(n.optString("capability_id")) || needle.equals(n.optString("capabilityId"))) {
                    return n;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    static JSONObject findByRoute(Context context, NodeMetadataStore overrides, String route) {
        String needle = clean(route);
        if (needle.isEmpty()) return null;
        try {
            JSONArray nodes = new JSONObject(registryJson(context, overrides)).optJSONArray("nodes");
            if (nodes == null) return null;
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject n = nodes.optJSONObject(i);
                if (n != null && needle.equals(n.optString("route"))) return n;
            }
        } catch (Exception ignored) {}
        return null;
    }

    static Match matchVoice(Context context, NodeMetadataStore overrides, String transcript) {
        String normalized = VoiceCommandParser.normalize(transcript);
        if (normalized.isEmpty()) return null;
        Match candidate = null;
        int best = -1;
        try {
            JSONArray nodes = new JSONObject(registryJson(context, overrides)).optJSONArray("nodes");
            if (nodes == null) return null;
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject n = nodes.optJSONObject(i);
                if (n == null) continue;
                JSONObject voice = n.optJSONObject("voice");
                if (voice == null || !voice.optBoolean("enabled", false)) continue;
                JSONArray aliases = voice.optJSONArray("aliases");
                if (aliases == null) continue;
                for (int j = 0; j < aliases.length(); j++) {
                    String alias = aliases.optString(j, "");
                    String a = VoiceCommandParser.normalize(alias);
                    if (a.isEmpty()) continue;
                    boolean exact = normalized.equals(a);
                    boolean contains = normalized.contains(a) || a.contains(normalized);
                    if (!exact && !contains) continue;
                    int score = exact ? 10000 + a.length() : a.length();
                    if (score > best) {
                        best = score;
                        candidate = new Match(n, alias);
                    }
                }
            }
        } catch (Exception ignored) {}
        return candidate;
    }

    private static JSONObject pageFromMetadata(ActivityInfo info, Bundle m) throws Exception {
        JSONObject p = new JSONObject();
        String graphId = m.getString(META_ID, info.name);
        String title = m.getString(META_TITLE, info.name.substring(info.name.lastIndexOf('.') + 1));
        p.put("id", graphId);
        p.put("capabilityId", GraphContract.capabilityId(GraphContract.APP_ORIGIN, graphId, m.getString(META_CAPABILITY, "")));
        p.put("title", title);
        p.put("description", m.getString(META_DESCRIPTION, ""));
        p.put("parent", m.getString(META_PARENT, "app-core"));
        p.put("route", m.getString(META_ROUTE, ""));
        p.put("direction", m.getString("amin.graph.direction", "right"));
        p.put("slot", m.getInt("amin.graph.slot", 0));
        p.put("activity", info.name);
        p.put("origin", "app");
        p.put("locked", true);
        p.put("nodeType", m.getString(META_TYPE, "capability"));
        p.put("status", m.getString(META_STATUS, "active"));
        p.put("nodeVersion", m.getString(META_VERSION, "1"));
        p.put("actions", csv(m.getString(META_ACTIONS, "open")));
        p.put("inputContract", m.getString(META_INPUT, ""));
        p.put("outputContract", m.getString(META_OUTPUT, ""));
        JSONObject voice = new JSONObject();
        boolean voiceEnabled = m.containsKey(META_VOICE_ENABLED) ? m.getBoolean(META_VOICE_ENABLED, false) : !m.getString(META_ROUTE, "").isEmpty();
        voice.put("enabled", voiceEnabled);
        JSONArray aliases = csv(m.getString(META_VOICE_ALIASES, ""));
        if (aliases.length() == 0 && voiceEnabled) aliases.put(title);
        voice.put("aliases", aliases);
        p.put("voice", voice);
        JSONObject storage = new JSONObject();
        storage.put("adapter", m.getString(META_STORAGE_ADAPTER, ""));
        storage.put("source_id", m.getString(META_STORAGE_SOURCE, ""));
        storage.put("table", m.getString(META_STORAGE_TABLE, ""));
        p.put("storage", storage);
        return p;
    }

    private static JSONArray csv(String value) {
        JSONArray out = new JSONArray();
        for (String item : split(value)) out.put(item);
        return out;
    }

    private static List<String> split(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) return out;
        for (String item : value.split("[,，|]")) {
            String clean = clean(item);
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
