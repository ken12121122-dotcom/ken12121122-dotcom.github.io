package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Converts mature indexer output (Tree-sitter now; other indexers later) into AMIN canonical evidence. */
final class IndexerEvidenceAdapter {
    static final String FORMAT = "amin-indexer-evidence";
    static final int VERSION = 1;

    private IndexerEvidenceAdapter() { }

    static JSONObject toCanonical(JSONObject input) {
        try {
            if (input == null || !FORMAT.equals(input.optString("format", ""))) {
                return CanonicalEvidenceAdapter.empty();
            }

            JSONObject out = CanonicalEvidenceAdapter.empty();
            JSONArray entities = out.getJSONArray("entities");
            JSONArray relations = out.getJSONArray("relations");
            Set<String> entityIds = new HashSet<>();
            Set<String> relationIds = new HashSet<>();
            String revision = clean(input.optString("revision", ""));
            String provider = clean(input.optString("provider", ""));

            JSONObject anchor = input.optJSONObject("anchor");
            String anchorId = "";
            if (anchor != null) {
                anchorId = clean(anchor.optString("entityId", anchor.optString("id", "")));
                addEntity(
                        entities,
                        entityIds,
                        anchorId,
                        anchor.optString("title", anchorId),
                        anchor.optString("kind", "source"),
                        "",
                        anchor.optString("verification", "indexed"),
                        anchor.optJSONObject("evidence"),
                        revision,
                        provider
                );
            }

            JSONArray indexedRelations = input.optJSONArray("relations");
            if (indexedRelations != null) {
                for (int i = 0; i < indexedRelations.length(); i++) {
                    JSONObject source = indexedRelations.optJSONObject(i);
                    if (source == null) continue;
                    String from = clean(source.optString("from", ""));
                    String to = clean(source.optString("to", ""));
                    if (from.isEmpty() || to.isEmpty()) continue;
                    JSONObject evidence = source.optJSONObject("evidence");
                    String verification = clean(source.optString("verification", "indexed"));
                    String title = to;
                    if (evidence != null) {
                        String callerSymbol = clean(evidence.optString("callerSymbol", ""));
                        if (!callerSymbol.isEmpty()) title = callerSymbol.replace(".", " · ");
                    }
                    addEntity(entities, entityIds, to, title, "source", from, verification, evidence, revision, provider);
                    String relationId = clean(source.optString("relationId", source.optString("id", "")));
                    String type = clean(source.optString("type", "related_to"));
                    if (relationId.isEmpty()) relationId = "relation:" + type + ":" + from + ">" + to;
                    addRelation(relations, relationIds, relationId, from, to, type, verification, evidence, revision, provider);
                }
            }

            JSONArray gaps = input.optJSONArray("gaps");
            if (gaps != null && gaps.length() > 0) {
                JSONObject gap = gaps.optJSONObject(0);
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
                            revision,
                            provider
                    );
                    if (!after.isEmpty()) {
                        addRelation(
                                relations,
                                relationIds,
                                "relation:evidence_gap:" + after + ">" + gapId,
                                after,
                                gapId,
                                "evidence_gap",
                                "gap",
                                null,
                                revision,
                                provider
                        );
                    }
                    out.put("gap", new JSONObject(gap.toString()));
                }
            }

            out.put("revision", revision);
            out.put("anchorId", anchorId);
            out.put("provider", provider);
            out.put("providerVersion", input.optString("providerVersion", ""));
            out.put("parserVersion", input.optString("parserVersion", ""));
            out.put("status", "indexed");
            return out;
        } catch (Exception ignored) {
            return CanonicalEvidenceAdapter.empty();
        }
    }

    private static void addEntity(JSONArray out, Set<String> ids, String id, String title, String kind,
                                  String parentId, String verification, JSONObject evidence,
                                  String revision, String provider) throws Exception {
        id = clean(id);
        if (id.isEmpty() || !ids.add(id)) return;
        JSONObject entity = new JSONObject()
                .put("entityId", id)
                .put("id", id)
                .put("title", clean(title).isEmpty() ? id : title)
                .put("kind", clean(kind))
                .put("parentId", clean(parentId))
                .put("verification", clean(verification))
                .put("evidenceRevision", revision)
                .put("sourceKey", sourceKey(id, evidence, provider));
        if (evidence != null) entity.put("evidence", new JSONObject(evidence.toString()));
        out.put(entity);
    }

    private static void addRelation(JSONArray out, Set<String> ids, String id, String from, String to,
                                    String type, String verification, JSONObject evidence,
                                    String revision, String provider) throws Exception {
        id = clean(id);
        if (id.isEmpty() || !ids.add(id)) return;
        JSONObject relation = new JSONObject()
                .put("relationId", id)
                .put("id", id)
                .put("from", clean(from))
                .put("to", clean(to))
                .put("type", clean(type).isEmpty() ? "related_to" : type)
                .put("verification", clean(verification))
                .put("evidenceRevision", revision)
                .put("provider", provider);
        if (evidence != null) relation.put("evidence", new JSONObject(evidence.toString()));
        out.put(relation);
    }

    private static String sourceKey(String id, JSONObject evidence, String provider) {
        if (evidence == null) return clean(provider) + "|" + id;
        String path = clean(evidence.optString("path", ""));
        String symbol = clean(evidence.optString("symbol", evidence.optString("callerSymbol", evidence.optString("calleeSymbol", ""))));
        StringBuilder out = new StringBuilder(clean(provider));
        if (!path.isEmpty()) out.append('|').append(path);
        if (!symbol.isEmpty()) out.append('|').append(symbol);
        if (out.length() == 0) out.append(id);
        return out.toString();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
