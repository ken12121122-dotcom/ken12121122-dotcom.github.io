package com.amin.pocketgba;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PromptStore extends SQLiteOpenHelper {
    static final String DEFAULT_CATEGORY_ID = "inbox";
    static final String STATUS_ACTIVE = "active";
    static final String STATUS_ARCHIVED = "archived";
    static final String STATUS_DELETED = "deleted";
    private static final String DATABASE_NAME = "amin_prompts.db";
    private static final int DATABASE_VERSION = 2;

    PromptStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE prompt_categories (id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,sort_order INTEGER NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE prompts (id INTEGER PRIMARY KEY AUTOINCREMENT,category_id TEXT NOT NULL,title TEXT NOT NULL DEFAULT '',content TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'active',is_favorite INTEGER NOT NULL DEFAULT 0,is_pinned INTEGER NOT NULL DEFAULT 0,usage_count INTEGER NOT NULL DEFAULT 0,last_used_at INTEGER,deleted_at INTEGER,tags TEXT NOT NULL DEFAULT '',version INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,FOREIGN KEY(category_id) REFERENCES prompt_categories(id))");
        db.execSQL("CREATE INDEX prompts_state_idx ON prompts(status,is_pinned DESC,is_favorite DESC,updated_at DESC)");
        db.execSQL("CREATE INDEX prompts_category_created_idx ON prompts(category_id,created_at DESC)");
        createRelations(db);
        seedCategory(db, DEFAULT_CATEGORY_ID, "收件匣", 0);
        seedCategory(db, "work", "工作", 10);
        seedCategory(db, "creation", "創作", 20);
        seedCategory(db, "research", "研究", 30);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.beginTransaction();
            try {
                db.execSQL("ALTER TABLE prompts ADD COLUMN title TEXT NOT NULL DEFAULT ''");
                db.execSQL("ALTER TABLE prompts ADD COLUMN status TEXT NOT NULL DEFAULT 'active'");
                db.execSQL("ALTER TABLE prompts ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE prompts ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE prompts ADD COLUMN usage_count INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE prompts ADD COLUMN last_used_at INTEGER");
                db.execSQL("ALTER TABLE prompts ADD COLUMN deleted_at INTEGER");
                db.execSQL("ALTER TABLE prompts ADD COLUMN tags TEXT NOT NULL DEFAULT ''");
                db.execSQL("ALTER TABLE prompts ADD COLUMN version INTEGER NOT NULL DEFAULT 1");
                db.execSQL("UPDATE prompts SET title=substr(replace(content, char(10), ' '),1,40) WHERE title='' OR title IS NULL");
                db.execSQL("CREATE INDEX IF NOT EXISTS prompts_state_idx ON prompts(status,is_pinned DESC,is_favorite DESC,updated_at DESC)");
                createRelations(db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    private void createRelations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS graph_relations (id INTEGER PRIMARY KEY AUTOINCREMENT,source_type TEXT NOT NULL,source_id TEXT NOT NULL,target_type TEXT NOT NULL,target_id TEXT NOT NULL,relation_type TEXT NOT NULL,created_at INTEGER NOT NULL,UNIQUE(source_type,source_id,target_type,target_id,relation_type))");
        db.execSQL("CREATE INDEX IF NOT EXISTS graph_relations_source_idx ON graph_relations(source_type,source_id)");
    }

    synchronized long savePrompt(String categoryId, CharSequence selectedText) {
        String content = PromptText.requireContent(selectedText);
        return createPrompt(categoryId, PromptText.preview(content.replace('\n', ' '), 40), content, "");
    }

    synchronized long createPrompt(String categoryId, String title, String content, String tags) {
        content = PromptText.requireContent(content);
        String resolvedCategory = categoryExists(categoryId) ? categoryId : DEFAULT_CATEGORY_ID;
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("category_id", resolvedCategory);
        v.put("title", normalizeTitle(title, content));
        v.put("content", content);
        v.put("tags", tags == null ? "" : tags.trim());
        v.put("status", STATUS_ACTIVE);
        v.put("created_at", now);
        v.put("updated_at", now);
        return getWritableDatabase().insertOrThrow("prompts", null, v);
    }

    synchronized boolean updatePrompt(long id, String title, String content, String categoryId, String tags) {
        content = PromptText.requireContent(content);
        ContentValues v = new ContentValues();
        v.put("title", normalizeTitle(title, content));
        v.put("content", content);
        v.put("category_id", categoryExists(categoryId) ? categoryId : DEFAULT_CATEGORY_ID);
        v.put("tags", tags == null ? "" : tags.trim());
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("prompts", v, "id=?", new String[]{String.valueOf(id)}) == 1;
    }

    synchronized boolean setFavorite(long id, boolean value) { return setFlag(id, "is_favorite", value); }
    synchronized boolean setPinned(long id, boolean value) { return setFlag(id, "is_pinned", value); }

    private boolean setFlag(long id, String column, boolean value) {
        ContentValues v = new ContentValues();
        v.put(column, value ? 1 : 0);
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("prompts", v, "id=?", new String[]{String.valueOf(id)}) == 1;
    }

    synchronized boolean archive(long id) { return setStatus(id, STATUS_ARCHIVED, false); }
    synchronized boolean unarchive(long id) { return setStatus(id, STATUS_ACTIVE, false); }
    synchronized boolean softDelete(long id) { return setStatus(id, STATUS_DELETED, true); }
    synchronized boolean restore(long id) { return setStatus(id, STATUS_ACTIVE, false); }

    private boolean setStatus(long id, String status, boolean deleted) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put("updated_at", System.currentTimeMillis());
        if (deleted) v.put("deleted_at", System.currentTimeMillis()); else v.putNull("deleted_at");
        return getWritableDatabase().update("prompts", v, "id=?", new String[]{String.valueOf(id)}) == 1;
    }

    synchronized boolean hardDelete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String sid = String.valueOf(id);
            db.delete("graph_relations", "(source_type='prompt' AND source_id=?) OR (target_type='prompt' AND target_id=?)", new String[]{sid, sid});
            boolean ok = db.delete("prompts", "id=? AND status=?", new String[]{sid, STATUS_DELETED}) == 1;
            db.setTransactionSuccessful();
            return ok;
        } finally { db.endTransaction(); }
    }

    synchronized void recordUsage(long id) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.execSQL("UPDATE prompts SET usage_count=usage_count+1,last_used_at=?,updated_at=? WHERE id=? AND status='active'", new Object[]{now, now, id});
    }

    synchronized Prompt getPrompt(long id) {
        List<Prompt> rows = queryPrompts("id=?", new String[]{String.valueOf(id)}, "id DESC", null);
        return rows.isEmpty() ? null : rows.get(0);
    }

    synchronized List<Prompt> listPrompts(String categoryId) {
        return queryPrompts("status=? AND category_id=?", new String[]{STATUS_ACTIVE, categoryId}, "is_pinned DESC,updated_at DESC,id DESC", null);
    }

    synchronized List<Prompt> listActive() { return queryPrompts("status=?", new String[]{STATUS_ACTIVE}, "is_pinned DESC,updated_at DESC,id DESC", null); }
    synchronized List<Prompt> listArchived() { return queryPrompts("status=?", new String[]{STATUS_ARCHIVED}, "updated_at DESC,id DESC", null); }
    synchronized List<Prompt> listDeleted() { return queryPrompts("status=?", new String[]{STATUS_DELETED}, "deleted_at DESC,id DESC", null); }
    synchronized List<Prompt> listFavorites() { return queryPrompts("status=? AND is_favorite=1", new String[]{STATUS_ACTIVE}, "is_pinned DESC,updated_at DESC,id DESC", null); }
    synchronized List<Prompt> listPinned() { return queryPrompts("status=? AND is_pinned=1", new String[]{STATUS_ACTIVE}, "updated_at DESC,id DESC", null); }
    synchronized List<Prompt> listRecent() { return queryPrompts("status=? AND last_used_at IS NOT NULL", new String[]{STATUS_ACTIVE}, "last_used_at DESC", "50"); }

    synchronized List<Prompt> searchActive(String query) {
        String q = "%" + (query == null ? "" : query.trim()) + "%";
        return queryPrompts("status=? AND (title LIKE ? OR content LIKE ? OR tags LIKE ? OR category_id LIKE ?)", new String[]{STATUS_ACTIVE, q, q, q, q}, "is_pinned DESC,updated_at DESC", "100");
    }

    private List<Prompt> queryPrompts(String where, String[] args, String order, String limit) {
        List<Prompt> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("prompts", new String[]{"id","category_id","title","content","tags","status","is_favorite","is_pinned","usage_count","last_used_at","deleted_at","version","created_at","updated_at"}, where, args, null, null, order, limit)) {
            while (c.moveToNext()) out.add(new Prompt(c));
        }
        return Collections.unmodifiableList(out);
    }

    synchronized List<Category> listCategories() {
        List<Category> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("prompt_categories", new String[]{"id","name"}, null,null,null,null,"sort_order ASC,name ASC")) {
            while (c.moveToNext()) out.add(new Category(c.getString(0), c.getString(1)));
        }
        return Collections.unmodifiableList(out);
    }

    synchronized int countPrompts() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM prompts WHERE status='active'", null)) { return c.moveToFirst() ? c.getInt(0) : 0; }
    }

    synchronized boolean addRelation(String sourceType, String sourceId, String targetType, String targetId, String relationType) {
        if (!validNodeType(sourceType) || !validNodeType(targetType) || empty(sourceId) || empty(targetId) || empty(relationType)) return false;
        ContentValues v = new ContentValues();
        v.put("source_type", sourceType); v.put("source_id", sourceId.trim()); v.put("target_type", targetType); v.put("target_id", targetId.trim()); v.put("relation_type", relationType.trim()); v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict("graph_relations", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    synchronized boolean deleteRelation(long relationId) { return getWritableDatabase().delete("graph_relations", "id=?", new String[]{String.valueOf(relationId)}) == 1; }

    synchronized List<Relation> listRelationsForPrompt(long promptId) {
        List<Relation> out = new ArrayList<>(); String id = String.valueOf(promptId);
        try (Cursor c = getReadableDatabase().query("graph_relations", new String[]{"id","source_type","source_id","target_type","target_id","relation_type"}, "(source_type='prompt' AND source_id=?) OR (target_type='prompt' AND target_id=?)", new String[]{id,id}, null,null,"id DESC")) {
            while (c.moveToNext()) out.add(new Relation(c));
        }
        return Collections.unmodifiableList(out);
    }

    synchronized String graphJson() {
        JSONObject root = new JSONObject(); JSONArray nodes = new JSONArray(); JSONArray links = new JSONArray();
        try {
            for (Prompt p : listActive()) {
                JSONObject n = new JSONObject(); n.put("id", "prompt:" + p.id); n.put("rawId", String.valueOf(p.id)); n.put("type", "prompt"); n.put("name", p.title); n.put("text", p.content); n.put("favorite", p.favorite); n.put("pinned", p.pinned); nodes.put(n);
            }
            try (Cursor c = getReadableDatabase().query("graph_relations", new String[]{"source_type","source_id","target_type","target_id","relation_type"}, null,null,null,null,"id ASC")) {
                while (c.moveToNext()) { JSONObject l = new JSONObject(); l.put("a", c.getString(0)+":"+c.getString(1)); l.put("b", c.getString(2)+":"+c.getString(3)); l.put("type", c.getString(4)); links.put(l); }
            }
            root.put("nodes", nodes); root.put("links", links);
        } catch (JSONException ignored) { return "{\"nodes\":[],\"links\":[]}"; }
        return root.toString();
    }

    private boolean categoryExists(String id) {
        if (id == null) return false;
        try (Cursor c = getReadableDatabase().query("prompt_categories", new String[]{"id"}, "id=?", new String[]{id}, null,null,null,"1")) { return c.moveToFirst(); }
    }

    private void seedCategory(SQLiteDatabase db, String id, String name, int order) {
        ContentValues v = new ContentValues(); v.put("id",id); v.put("name",name); v.put("sort_order",order); v.put("created_at",System.currentTimeMillis()); db.insertOrThrow("prompt_categories",null,v);
    }

    private static String normalizeTitle(String title, String content) {
        String value = title == null ? "" : title.trim();
        if (!value.isEmpty()) return value;
        return PromptText.preview(content.replace('\n',' '), 40);
    }
    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private static boolean validNodeType(String s) { return "prompt".equals(s) || "knowledge".equals(s); }

    static final class Category { final String id,name; Category(String id,String name){this.id=id;this.name=name;} }

    static final class Prompt {
        final long id, usageCount, createdAt, updatedAt; final String categoryId,title,content,tags,status; final boolean favorite,pinned; final Long lastUsedAt,deletedAt; final int version;
        Prompt(Cursor c){ id=c.getLong(0); categoryId=c.getString(1); title=c.getString(2); content=c.getString(3); tags=c.getString(4); status=c.getString(5); favorite=c.getInt(6)!=0; pinned=c.getInt(7)!=0; usageCount=c.getLong(8); lastUsedAt=c.isNull(9)?null:c.getLong(9); deletedAt=c.isNull(10)?null:c.getLong(10); version=c.getInt(11); createdAt=c.getLong(12); updatedAt=c.getLong(13); }
    }

    static final class Relation {
        final long id; final String sourceType,sourceId,targetType,targetId,relationType;
        Relation(Cursor c){id=c.getLong(0);sourceType=c.getString(1);sourceId=c.getString(2);targetType=c.getString(3);targetId=c.getString(4);relationType=c.getString(5);}
    }
}
