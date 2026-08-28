package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Deterministic Capability <-> Source mapping and architecture audit. Never mutates Registry/Connect state. */
final class CapabilitySourceMap {
    static final String FORMAT = "amin-capability-source-map";
    static final int VERSION = 1;

    private CapabilitySourceMap() { }

    static JSONObject evaluate(JSONObject input, JSONArray capabilityNodes, JSONObject sourceGraph, JSONArray registeredRelations) {
        try {
            JSONObject source = input == null ? new JSONObject() : new JSONObject(input.toString());
            JSONArray mappings = source.optJSONArray("mappings");
            if (mappings == null) mappings = new JSONArray();
            JSONArray errors = new JSONArray();
            JSONArray findings = new JSONArray();

            Set<String> capabilityIds = new HashSet<>();
            if (capabilityNodes != null) {
                for (int i = 0; i < capabilityNodes.length(); i++) {
                    JSONObject node = capabilityNodes.optJSONObject(i);
                    if (node == null) continue;
                    String id = clean(node.optString("id", node.optString("registryId", "")));
                    if (!id.isEmpty()) capabilityIds.add(id);
                }
            }

            Set<String> sourceIds = new HashSet<>();
            JSONArray sourceEntities = sourceGraph == null ? null : sourceGraph.optJSONArray("entities");
            if (sourceEntities != null) {
                for (int i = 0; i < sourceEntities.length(); i++) {
                    JSONObject entity = sourceEntities.optJSONObject(i);
                    if (entity == null) continue;
                    String id = clean(entity.optString("id", ""));
                    if (!id.isEmpty()) sourceIds.add(id);
                }
            }

            Map<String, Set<String>> sourceToCapabilities = new HashMap<>();
            Set<String> mappingIds = new HashSet<>();
            for (int i = 0; i < mappings.length(); i++) {
                JSONObject mapping = mappings.optJSONObject(i);
                if (mapping == null) {
                    errors.put("mapping[" + i + "] invalid");
                    continue;
                }
                String mappingId = clean(mapping.optString("id", ""));
                String capabilityId = clean(mapping.optString("capabilityId", ""));
                String sourceId = clean(mapping.optString("sourceId", ""));
                if (mappingId.isEmpty()) errors.put("mapping[" + i + "] id required");
                else if (!mappingIds.add(mappingId)) errors.put("duplicate mapping id:" + mappingId);
                if (!capabilityIds.contains(capabilityId)) errors.put("mapping capability missing:" + capabilityId);
                if (!sourceIds.contains(sourceId)) errors.put("mapping source missing:" + sourceId);
                if (capabilityIds.contains(capabilityId) && sourceIds.contains(sourceId)) {
                    Set<String> owners = sourceToCapabilities.get(sourceId);
                    if (owners == null) {
                        owners = new HashSet<>();
                        sourceToCapabilities.put(sourceId, owners);
                    }
                    owners.add(capabilityId);
                }
                if (!mapping.has("provenance")) mapping.put("provenance", "repository-baseline");
            }

            Set<String> registeredPairs = new HashSet<>();
            if (registeredRelations != null) {
                for (int i = 0; i < registeredRelations.length(); i++) {
                    JSONObject relation = registeredRelations.optJSONObject(i);
                    if (relation == null) continue;
                    String from = clean(relation.optString("from", ""));
                    String to = clean(relation.optString("to", ""));
                    if (!from.isEmpty() && !to.isEmpty()) registeredPairs.add(from + "\n" + to);
                }
            }

            JSONArray sourceRelations = sourceGraph == null ? null : sourceGraph.optJSONArray("relations");
            Set<String> findingKeys = new HashSet<>();
            if (sourceRelations != null) {
                for (int i = 0; i < sourceRelations.length(); i++) {
                    JSONObject relation = sourceRelations.optJSONObject(i);
                    if (relation == null) continue;
                    String type = clean(relation.optString("type", ""));
                    if (!("calls".equals(type) || "imports".equals(type))) continue;
                    String fromSource = clean(relation.optString("from", ""));
                    String toSource = clean(relation.optString("to", ""));
                    Set<String> fromCapabilities = sourceToCapabilities.get(fromSource);
                    Set<String> toCapabilities = sourceToCapabilities.get(toSource);
                    if (fromCapabilities == null || toCapabilities == null) continue;
                    for (String fromCapability : fromCapabilities) {
                        for (String toCapability : toCapabilities) {
                            if (fromCapability.equals(toCapability)) continue;
                            String pair = fromCapability + "\n" + toCapability;
                            if (registeredPairs.contains(pair)) continue;
                            String key = pair + "\n" + type;
                            if (!findingKeys.add(key)) continue;
                            findings.put(new JSONObject()
                                    .put("id", "finding:" + findings.length())
                                    .put("type", "UNREGISTERED_LINK")
                                    .put("severity", "review")
                                    .put("fromCapability", fromCapability)
                                    .put("toCapability", toCapability)
                                    .put("fromSource", fromSource)
                                    .put("toSource", toSource)
                                    .put("sourceRelation", type)
                                    .put("message", "Source dependency exists without a registered capability Connect")
                                    .put("autoRegister", false));
                        }
                    }
                }
            }

            source.put("format", FORMAT);
            source.put("version", VERSION);
            source.put("mappings", mappings);
            source.put("valid", errors.length() == 0);
            source.put("errors", errors);
            source.put("findings", findings);
            source.put("auditPolicy", new JSONObject()
                    .put("autoRegisterConnect", false)
                    .put("findingType", "UNREGISTERED_LINK"));
            return source;
        } catch (Exception error) {
            try {
                return new JSONObject()
                        .put("format", FORMAT)
                        .put("version", VERSION)
                        .put("mappings", new JSONArray())
                        .put("valid", false)
                        .put("errors", new JSONArray().put("EVALUATE_ERROR:" + error.getClass().getSimpleName()))
                        .put("findings", new JSONArray());
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
