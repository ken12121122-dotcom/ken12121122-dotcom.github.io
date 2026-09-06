package com.amin.pocketgba;

import org.json.JSONArray;
import org.json.JSONObject;

/** Executable M0 contract for source-backed, versioned local knowledge. */
final class TraceableKnowledgeContract {
    static final int VERSION = 1;
    static final String CAPABILITY_ID = "CAP-TRACEABLE-KNOWLEDGE-001";
    static final String RESULT_FORMAT = "amin-traceable-knowledge-result";
    static final String REVIEW_GENERATED = "generated";

    private TraceableKnowledgeContract() { }

    static void requireSource(JSONObject value) {
        require(value, "source_id");
        require(value, "source_type");
        require(value, "authority");
        String url = value.optString("source_url", "").trim();
        if (!url.isEmpty() && !ExternalResourcePolicy.isAllowedHttps(url)) {
            throw new IllegalArgumentException("source_url must be HTTPS");
        }
    }

    static void requireDocument(JSONObject value) {
        require(value, "document_id");
        require(value, "source_id");
        require(value, "title");
        String status = value.optString("review_status", REVIEW_GENERATED);
        if (!("generated".equals(status) || "reviewed".equals(status)
                || "approved".equals(status) || "rejected".equals(status)
                || "deprecated".equals(status))) {
            throw new IllegalArgumentException("invalid review_status");
        }
    }

    static void requireVersion(JSONObject value) {
        require(value, "version_id");
        require(value, "document_id");
        require(value, "source_version");
        requireHash(value.optString("raw_hash", ""), "raw_hash");
        requireHash(value.optString("content_hash", ""), "content_hash");
        require(value, "raw_path");
        if (value.optLong("retrieved_at", 0L) <= 0L) {
            throw new IllegalArgumentException("retrieved_at required");
        }
    }

    static void requireChunk(JSONObject value) {
        require(value, "chunk_id");
        require(value, "version_id");
        require(value, "section");
        require(value, "content");
        if (value.optInt("ordinal", -1) < 0) throw new IllegalArgumentException("ordinal required");
    }

    static JSONObject result(String queryId, String query, JSONArray matches,
                             JSONArray sources, JSONArray gaps) {
        try {
            return new JSONObject()
                    .put("format", RESULT_FORMAT)
                    .put("version", VERSION)
                    .put("capability_id", CAPABILITY_ID)
                    .put("query_id", clean(queryId))
                    .put("query", clean(query))
                    .put("matched_chunks", matches == null ? new JSONArray() : matches)
                    .put("source_records", sources == null ? new JSONArray() : sources)
                    .put("unresolved_gaps", gaps == null ? new JSONArray() : gaps)
                    .put("offline", true)
                    .put("review_status", REVIEW_GENERATED);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static void require(JSONObject value, String key) {
        if (value == null || clean(value.optString(key, "")).isEmpty()) {
            throw new IllegalArgumentException(key + " required");
        }
    }

    private static void requireHash(String value, String key) {
        if (!clean(value).matches("[0-9a-f]{64}")) throw new IllegalArgumentException(key + " invalid");
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
