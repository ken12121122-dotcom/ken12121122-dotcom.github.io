package com.amin.pocketgba;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Isolated M1 database. It does not replace the existing Graph stores. */
final class TraceableKnowledgeDatabase extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;

    TraceableKnowledgeDatabase(Context context, String name) {
        super(context, name, null, DATABASE_VERSION);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sources ("
                + "source_id TEXT PRIMARY KEY NOT NULL,"
                + "source_type TEXT NOT NULL, authority TEXT NOT NULL,"
                + "source_url TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE documents ("
                + "document_id TEXT PRIMARY KEY NOT NULL, source_id TEXT NOT NULL,"
                + "title TEXT NOT NULL, current_version_id TEXT,"
                + "review_status TEXT NOT NULL DEFAULT 'generated', created_at INTEGER NOT NULL,"
                + "FOREIGN KEY(source_id) REFERENCES sources(source_id))");
        db.execSQL("CREATE TABLE document_versions ("
                + "version_id TEXT PRIMARY KEY NOT NULL, document_id TEXT NOT NULL,"
                + "source_version TEXT NOT NULL, raw_hash TEXT NOT NULL, raw_path TEXT NOT NULL,"
                + "retrieved_at INTEGER NOT NULL, effective_date TEXT NOT NULL DEFAULT '',"
                + "status TEXT NOT NULL DEFAULT 'candidate', content_hash TEXT NOT NULL,"
                + "FOREIGN KEY(document_id) REFERENCES documents(document_id),"
                + "UNIQUE(document_id, content_hash))");
        db.execSQL("CREATE TABLE chunks ("
                + "chunk_id TEXT PRIMARY KEY NOT NULL, version_id TEXT NOT NULL,"
                + "ordinal INTEGER NOT NULL, section TEXT NOT NULL, content TEXT NOT NULL,"
                + "source_reference TEXT NOT NULL,"
                + "FOREIGN KEY(version_id) REFERENCES document_versions(version_id),"
                + "UNIQUE(version_id, ordinal))");
        db.execSQL("CREATE VIRTUAL TABLE chunks_fts USING fts4("
                + "chunk_id, section, content, tokenize=simple)");
        db.execSQL("CREATE TABLE retrieval_logs ("
                + "query_id TEXT PRIMARY KEY NOT NULL, query_text TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL, result_count INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE verification_records ("
                + "verification_id TEXT PRIMARY KEY NOT NULL, version_id TEXT NOT NULL,"
                + "verification_status TEXT NOT NULL, verified_at INTEGER NOT NULL,"
                + "source_reference TEXT NOT NULL DEFAULT '',"
                + "FOREIGN KEY(version_id) REFERENCES document_versions(version_id))");
        db.execSQL("CREATE INDEX idx_documents_source ON documents(source_id)");
        db.execSQL("CREATE INDEX idx_versions_document ON document_versions(document_id, retrieved_at)");
        db.execSQL("CREATE INDEX idx_chunks_version ON chunks(version_id, ordinal)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Knowledge database migration required: "
                + oldVersion + " -> " + newVersion);
    }
}
