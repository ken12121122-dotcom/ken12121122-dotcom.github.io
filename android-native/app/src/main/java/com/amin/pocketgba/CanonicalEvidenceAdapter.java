package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Converts scanner/indexer evidence into one canonical graph-neutral payload.
 *
 * Scanner/indexers are not allowed to choose coordinates or render a canvas. They only emit
 * evidence-backed entities and relations. The visual graph consumes this payload through the
 * single dynamic canvas in amin-wiki-graph/index.html.
 */
final class CanonicalEvidenceAdapter {
    static final String FORMAT = "amin-canonical-evidence";
    static final int VERSION = 1;

    private CanonicalEvidenceAdapter() { }

    static JSONObject fromReverseDiscovery(JSONObject reverse) {
        try {
            JSONObject out = empty();
            if (reverse == null || !reverse.optBoolean("anchorVerified", false)) return out;

            JSONArray entities = out.getJSONArray("entities");
            JSONArray relations = out.getJSONArray("relations");
            Set<String> entityIds = new HashSet<>();
            Set<String> relationIds = new HashSet<>();
            String revision = clean(reverse.optString("revision", ""));

            String anchorId = clean(reverse.optString("anchorId", ""));
            if (!anchorId.isEmpty()) {
                addEntity(
                        entities,
                        entityIds,
                        anchorId,
                        reverse.optString("anchorLabel", anchorId),
                        "scanner",
                        "",
                        "static_verified",
                        reverse.optJSONObject("anchorEvidence"),
                        revision
                );
            }

            JSONArray steps = reverse.optJSONArray("steps");
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.optJSONObject(i);
                    if (step == null) continue;
                    String from = clean(step.optString("from", ""));
                    String to = clean(step.optString("to", ""));
                    if (from.isEmpty() || to.isEmpty()) continue;
                    String verification = clean(step.optString("verification", "static_verified"));
                    JSONObject evidence = step.optJSONObject("evidence");
                    addEntity(
                            entities,
                            entityIds,
                            to,
                            step.optString("toLabel", to),
                            "source",
                            from,
                            verification,
                            evidence,
                            revision
                    );
                    addRelation(
                            relations,
                            relationIds,
                            stableRelationId(step.optString("relation", "related_to"), from, to),
                            from,
                            to,
                            step.optString("relation", "related_to"),
                            verification,
                            evidence,
                            revision
                    );
                }
            }

            JSONObject gap = reverse.optJSONObject("gap");
            if (gap != null) {
                String after = clean(gap.optString("after", ""));
                String code = clean(gap.optString("code", "EVIDENCE_GAP"));
                String gapId = "gap:" + (code.isEmpty() ? "evidence" : code);
                addEntity(
                        entities,
                        entityIds,
                        gapId,
                        gap.optString("expectedLayer", "GAP").toUpperCase() + " GAP",
                        "gap",
                        after,
                        "gap",
                        null,
                        revision
                );
                if (!after.isEmpty()) {
                    addRelation(
                            relations,
                            relationIds,
                            stableRelationId("evidence_gap", after, gapId),
                            after,
                            gapId,
                            "evidence_gap",
                            "gap",
                            null,
                            revision
                    );
                }
                out.put("gap", new JSONObject(gap.toString()));
            }

            out.put("revision", revision);
            out.put("status", reverse.optString("status", ""));
            out.put("anchorId", anchorId);
            return out;
        } catch (Exception error) {
            return empty();
        }
    }

    static JSONObject empty() {
        try {
            return new JSONObject()
                    .put("format", FORMAT)
                    .put("version", VERSION)
                    .put("revision", "")
                    .put("anchorId", "")
                    .put("status", "")
                    .put("entities", new JSONArray())
                    .put("relations", new JSONArray());
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static void addEntity(JSONArray out, Set<String> ids, String id, String title,
                                  String kind, String parentId, String verification,
                                  JSONObject evidence, String revision) throws Exception {
        id = clean(id);
        if (id.isEmpty() || !ids.add(id)) return;
        JSONObject entity = new JSONObject()
                .put("entityId", id)
                .put("id", id)
                .put("title", clean(title).isEmpty() ? id : title)
                .put("kind", clean(kind))
                .put("parentId", clean(parentId))
                .put("verification", clean(verification))
                .put("evidenceRevision", clean(revision))
                .put("sourceKey", sourceKey(id, evidence));
        if (evidence != null) entity.put("evidence", new JSONObject(evidence.toString()));
        out.put(entity);
    }

    private static void addRelation(JSONArray out, Set<String> ids, String relationId,
                                    String from, String to, String type, String verification,
                                    JSONObject evidence, String revision) throws Exception {
        relationId = clean(relationId);
        if (relationId.isEmpty() || !ids.add(relationId)) return;
        JSONObject relation = new JSONObject()
                .put("relationId", relationId)
                .put("id", relationId)
                .put("from", clean(from))
                .put("to", clean(to))
                .put("type", clean(type).isEmpty() ? "related_to" : type)
                .put("verification", clean(verification))
                .put("evidenceRevision", clean(revision));
        if (evidence != null) relation.put("evidence", new JSONObject(evidence.toString()));
        out.put(relation);
    }

    private static String stableRelationId(String type, String from, String to) {
        return "relation:" + clean(type) + ":" + clean(from) + ">" + clean(to);
    }

    private static String sourceKey(String id, JSONObject evidence) {
        if (evidence == null) return id;
        String repository = clean(evidence.optString("repository", ""));
        String path = clean(evidence.optString("path", ""));
        String match = clean(evidence.optString("match", ""));
        StringBuilder key = new StringBuilder();
        if (!repository.isEmpty()) key.append(repository);
        if (!path.isEmpty()) {
            if (key.length() > 0) key.append('|');
            key.append(path);
        }
        if (!match.isEmpty()) {
            if (key.length() > 0) key.append('|');
            key.append(match);
        }
        if (key.length() == 0) key.append(id);
        return key.toString();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
