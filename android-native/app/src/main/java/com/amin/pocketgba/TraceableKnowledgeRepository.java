package com.amin.pocketgba;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** M1/M2 local ingestion and retrieval facade. Network and Fox are intentionally absent. */
final class TraceableKnowledgeRepository implements AutoCloseable {
    static final String DEFAULT_DATABASE = "traceable-knowledge.db";
    static final String DEFAULT_NAMESPACE = "traceable-knowledge";

    static final class ImportResult {
        final String documentId;
        final String versionId;
        final String rawHash;
        final String rawPath;
        final int chunkCount;
        final boolean created;

        ImportResult(String documentId, String versionId, String rawHash, String rawPath,
                     int chunkCount, boolean created) {
            this.documentId = documentId;
            this.versionId = versionId;
            this.rawHash = rawHash;
            this.rawPath = rawPath;
            this.chunkCount = chunkCount;
            this.created = created;
        }
    }

    private final TraceableKnowledgeDatabase helper;
    private final ImmutableKnowledgeSourceStore rawStore;

    TraceableKnowledgeRepository(Context context) {
        this(context, DEFAULT_DATABASE, DEFAULT_NAMESPACE);
    }

    TraceableKnowledgeRepository(Context context, String databaseName, String namespace) {
        if (context == null) throw new IllegalArgumentException("context required");
        helper = new TraceableKnowledgeDatabase(context.getApplicationContext(), databaseName);
        rawStore = new ImmutableKnowledgeSourceStore(context.getApplicationContext(), namespace);
    }

    ImportResult importText(JSONObject source, JSONObject document, String sourceVersion,
                            String effectiveDate, String rawText, long retrievedAt) throws Exception {
        TraceableKnowledgeContract.requireSource(source);
        TraceableKnowledgeContract.requireDocument(document);
        String versionLabel = clean(sourceVersion);
        if (versionLabel.isEmpty()) throw new IllegalArgumentException("sourceVersion required");
        if (retrievedAt <= 0L) throw new IllegalArgumentException("retrievedAt required");
        if (clean(rawText).isEmpty()) throw new IllegalArgumentException("rawText required");
        byte[] bytes = rawText.getBytes(StandardCharsets.UTF_8);
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.split(rawText);
        if (chunks.isEmpty()) throw new IllegalArgumentException("content produced no chunks");

        ImmutableKnowledgeSourceStore.Snapshot snapshot = rawStore.put(bytes);
        String documentId = document.getString("document_id").trim();
        String versionId = "version:" + safeId(documentId) + ":" + snapshot.sha256;
        JSONObject version = new JSONObject()
                .put("version_id", versionId)
                .put("document_id", documentId)
                .put("source_version", versionLabel)
                .put("raw_hash", snapshot.sha256)
                .put("raw_path", snapshot.relativePath)
                .put("retrieved_at", retrievedAt)
                .put("effective_date", clean(effectiveDate))
                .put("status", "candidate")
                .put("content_hash", snapshot.sha256);
        TraceableKnowledgeContract.requireVersion(version);

        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            assertSourceCompatible(db, source);
            insertSource(db, source, retrievedAt);
            assertDocumentCompatible(db, document);
            insertOrUpdateDocument(db, document, retrievedAt);
            if (exists(db, "document_versions", "version_id", versionId)) {
                int existingChunks = count(db, "chunks", "version_id", versionId);
                db.setTransactionSuccessful();
                return new ImportResult(documentId, versionId, snapshot.sha256,
                        snapshot.relativePath, existingChunks, false);
            }
            insertVersion(db, version);
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunker.Chunk item = chunks.get(i);
                String chunkId = versionId + ":chunk:" + i;
                JSONObject chunk = new JSONObject()
                        .put("chunk_id", chunkId).put("version_id", versionId)
                        .put("ordinal", i).put("section", item.section)
                        .put("content", item.content)
                        .put("source_reference", item.section);
                TraceableKnowledgeContract.requireChunk(chunk);
                insertChunk(db, chunk);
            }
            ContentValues pointer = new ContentValues();
            pointer.put("current_version_id", versionId);
            db.update("documents", pointer, "document_id=?", new String[]{documentId});
            db.setTransactionSuccessful();
            return new ImportResult(documentId, versionId, snapshot.sha256,
                    snapshot.relativePath, chunks.size(), true);
        } finally {
            db.endTransaction();
        }
    }

    JSONObject search(String rawQuery, int requestedLimit) {
        String query = clean(rawQuery);
        int limit = Math.max(1, Math.min(requestedLimit, 20));
        String queryId;
        try {
            queryId = "query:" + ImmutableKnowledgeSourceStore.sha256(
                    (query + ":" + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            queryId = "query:" + System.currentTimeMillis();
        }
        JSONArray matches = new JSONArray();
        JSONArray sources = new JSONArray();
        JSONArray gaps = new JSONArray();
        if (query.isEmpty()) {
            gaps.put("query_required");
            return TraceableKnowledgeContract.result(queryId, query, matches, sources, gaps);
        }
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = searchFts(db, query, limit);
            if (!cursor.moveToFirst()) {
                cursor.close();
                cursor = searchLike(db, query, limit);
            }
            Set<String> seenSources = new HashSet<>();
            if (cursor.moveToFirst()) do {
                String content = cursor.getString(cursor.getColumnIndexOrThrow("content"));
                String sourceId = cursor.getString(cursor.getColumnIndexOrThrow("source_id"));
                double score = content.contains(query) ? 1.0d : 0.6d;
                matches.put(new JSONObject()
                        .put("chunk_id", cursor.getString(cursor.getColumnIndexOrThrow("chunk_id")))
                        .put("document_id", cursor.getString(cursor.getColumnIndexOrThrow("document_id")))
                        .put("title", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                        .put("version_id", cursor.getString(cursor.getColumnIndexOrThrow("version_id")))
                        .put("source_version", cursor.getString(cursor.getColumnIndexOrThrow("source_version")))
                        .put("section", cursor.getString(cursor.getColumnIndexOrThrow("section")))
                        .put("content", content).put("source_id", sourceId).put("score", score));
                if (seenSources.add(sourceId)) {
                    sources.put(new JSONObject().put("source_id", sourceId)
                            .put("source_type", cursor.getString(cursor.getColumnIndexOrThrow("source_type")))
                            .put("authority", cursor.getString(cursor.getColumnIndexOrThrow("authority")))
                            .put("source_url", cursor.getString(cursor.getColumnIndexOrThrow("source_url")))
                            .put("retrieved_at", cursor.getLong(cursor.getColumnIndexOrThrow("retrieved_at")))
                            .put("content_hash", cursor.getString(cursor.getColumnIndexOrThrow("content_hash"))));
                }
            } while (cursor.moveToNext());
            if (matches.length() == 0) gaps.put("no_matching_chunk");
        } catch (Exception error) {
            gaps.put("offline_search_failed:" + error.getClass().getSimpleName());
        } finally {
            if (cursor != null) cursor.close();
            logQuery(db, queryId, query, matches.length());
        }
        return TraceableKnowledgeContract.result(queryId, query, matches, sources, gaps);
    }

    int documentCount() { return count(helper.getReadableDatabase(), "documents", null, null); }
    int versionCount() { return count(helper.getReadableDatabase(), "document_versions", null, null); }

    @Override public void close() { helper.close(); }

    private static Cursor searchFts(SQLiteDatabase db, String query, int limit) {
        return db.rawQuery(selectSql("chunks_fts MATCH ?"),
                new String[]{quoteFts(query), String.valueOf(limit)});
    }

    private static Cursor searchLike(SQLiteDatabase db, String query, int limit) {
        return db.rawQuery(selectSql("(c.content LIKE ? OR c.section LIKE ? OR d.title LIKE ?)"),
                new String[]{"%" + query + "%", "%" + query + "%", "%" + query + "%",
                        String.valueOf(limit)});
    }

    private static String selectSql(String where) {
        return "SELECT c.chunk_id,c.version_id,c.section,c.content,d.document_id,d.title,"
                + "v.source_version,v.retrieved_at,v.content_hash,s.source_id,s.source_type,"
                + "s.authority,s.source_url FROM chunks c "
                + "JOIN chunks_fts ON chunks_fts.chunk_id=c.chunk_id "
                + "JOIN document_versions v ON v.version_id=c.version_id "
                + "JOIN documents d ON d.document_id=v.document_id "
                + "JOIN sources s ON s.source_id=d.source_id WHERE " + where
                + " ORDER BY v.retrieved_at DESC,c.ordinal ASC LIMIT ?";
    }

    private static String quoteFts(String query) {
        return "\"" + query.replace("\"", "\"\"") + "\"";
    }

    private static void insertSource(SQLiteDatabase db, JSONObject source, long now) {
        ContentValues values = new ContentValues();
        values.put("source_id", source.optString("source_id").trim());
        values.put("source_type", source.optString("source_type").trim());
        values.put("authority", source.optString("authority").trim());
        values.put("source_url", source.optString("source_url", "").trim());
        values.put("created_at", now);
        db.insertWithOnConflict("sources", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void insertOrUpdateDocument(SQLiteDatabase db, JSONObject document, long now) {
        ContentValues values = new ContentValues();
        values.put("document_id", document.optString("document_id").trim());
        values.put("source_id", document.optString("source_id").trim());
        values.put("title", document.optString("title").trim());
        values.put("review_status", document.optString("review_status", "generated"));
        values.put("created_at", now);
        db.insertWithOnConflict("documents", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        ContentValues mutable = new ContentValues();
        mutable.put("title", document.optString("title").trim());
        db.update("documents", mutable, "document_id=?",
                new String[]{document.optString("document_id").trim()});
    }

    private static void insertVersion(SQLiteDatabase db, JSONObject version) {
        ContentValues values = new ContentValues();
        String[] keys = {"version_id", "document_id", "source_version", "raw_hash", "raw_path",
                "effective_date", "status", "content_hash"};
        for (String key : keys) values.put(key, version.optString(key, ""));
        values.put("retrieved_at", version.optLong("retrieved_at"));
        if (db.insertOrThrow("document_versions", null, values) < 0) {
            throw new IllegalStateException("version insert failed");
        }
    }

    private static void insertChunk(SQLiteDatabase db, JSONObject chunk) {
        ContentValues values = new ContentValues();
        values.put("chunk_id", chunk.optString("chunk_id"));
        values.put("version_id", chunk.optString("version_id"));
        values.put("ordinal", chunk.optInt("ordinal"));
        values.put("section", chunk.optString("section"));
        values.put("content", chunk.optString("content"));
        values.put("source_reference", chunk.optString("source_reference"));
        db.insertOrThrow("chunks", null, values);
        ContentValues fts = new ContentValues();
        fts.put("chunk_id", chunk.optString("chunk_id"));
        fts.put("section", chunk.optString("section"));
        fts.put("content", chunk.optString("content"));
        db.insertOrThrow("chunks_fts", null, fts);
    }

    private static void assertSourceCompatible(SQLiteDatabase db, JSONObject source) {
        try (Cursor cursor = db.query("sources", new String[]{"source_type", "authority", "source_url"},
                "source_id=?", new String[]{source.optString("source_id").trim()}, null, null, null)) {
            if (!cursor.moveToFirst()) return;
            if (!cursor.getString(0).equals(source.optString("source_type").trim())
                    || !cursor.getString(1).equals(source.optString("authority").trim())
                    || !cursor.getString(2).equals(source.optString("source_url", "").trim())) {
                throw new IllegalStateException("SOURCE_ID_CONFLICT");
            }
        }
    }

    private static void assertDocumentCompatible(SQLiteDatabase db, JSONObject document) {
        try (Cursor cursor = db.query("documents", new String[]{"source_id"}, "document_id=?",
                new String[]{document.optString("document_id").trim()}, null, null, null)) {
            if (cursor.moveToFirst() && !cursor.getString(0).equals(document.optString("source_id").trim())) {
                throw new IllegalStateException("DOCUMENT_SOURCE_CONFLICT");
            }
        }
    }

    private static boolean exists(SQLiteDatabase db, String table, String column, String value) {
        try (Cursor cursor = db.query(table, new String[]{column}, column + "=?",
                new String[]{value}, null, null, null, "1")) { return cursor.moveToFirst(); }
    }

    private static int count(SQLiteDatabase db, String table, String whereColumn, String whereValue) {
        String selection = whereColumn == null ? null : whereColumn + "=?";
        String[] args = whereColumn == null ? null : new String[]{whereValue};
        try (Cursor cursor = db.query(table, new String[]{"COUNT(*)"}, selection, args,
                null, null, null)) { return cursor.moveToFirst() ? cursor.getInt(0) : 0; }
    }

    private static void logQuery(SQLiteDatabase db, String id, String query, int count) {
        try {
            ContentValues values = new ContentValues();
            values.put("query_id", id); values.put("query_text", query);
            values.put("created_at", System.currentTimeMillis()); values.put("result_count", count);
            db.insert("retrieval_logs", null, values);
        } catch (Exception ignored) { }
    }

    private static String safeId(String value) { return clean(value).replaceAll("[^A-Za-z0-9._:-]", "_"); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
