package com.amin.pocketgba;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PromptStore extends SQLiteOpenHelper {
    static final String DEFAULT_CATEGORY_ID = "inbox";
    private static final String DATABASE_NAME = "amin_prompts.db";
    private static final int DATABASE_VERSION = 1;

    PromptStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE prompt_categories ("
                + "id TEXT PRIMARY KEY NOT NULL,"
                + "name TEXT NOT NULL,"
                + "sort_order INTEGER NOT NULL,"
                + "created_at INTEGER NOT NULL)");
        database.execSQL("CREATE TABLE prompts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "category_id TEXT NOT NULL,"
                + "content TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "FOREIGN KEY(category_id) REFERENCES prompt_categories(id))");
        database.execSQL("CREATE INDEX prompts_category_created_idx "
                + "ON prompts(category_id, created_at DESC)");
        seedCategory(database, DEFAULT_CATEGORY_ID, "收件匣", 0);
        seedCategory(database, "work", "工作", 10);
        seedCategory(database, "creation", "創作", 20);
        seedCategory(database, "research", "研究", 30);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // Version 1 is the initial append-only prompt schema.
    }

    synchronized long savePrompt(String categoryId, CharSequence selectedText) {
        String content = PromptText.requireContent(selectedText);
        String resolvedCategory = categoryExists(categoryId) ? categoryId : DEFAULT_CATEGORY_ID;
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("category_id", resolvedCategory);
        values.put("content", content);
        values.put("created_at", now);
        values.put("updated_at", now);
        long id = getWritableDatabase().insertOrThrow("prompts", null, values);
        return id;
    }

    synchronized List<Category> listCategories() {
        List<Category> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "prompt_categories",
                new String[]{"id", "name"},
                null,
                null,
                null,
                null,
                "sort_order ASC, name ASC"
        )) {
            while (cursor.moveToNext()) {
                result.add(new Category(cursor.getString(0), cursor.getString(1)));
            }
        }
        return Collections.unmodifiableList(result);
    }

    synchronized List<Prompt> listPrompts(String categoryId) {
        List<Prompt> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "prompts",
                new String[]{"id", "category_id", "content", "created_at"},
                "category_id = ?",
                new String[]{categoryId},
                null,
                null,
                "created_at DESC, id DESC"
        )) {
            while (cursor.moveToNext()) {
                result.add(new Prompt(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3)
                ));
            }
        }
        return Collections.unmodifiableList(result);
    }

    synchronized int countPrompts() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM prompts", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private boolean categoryExists(String categoryId) {
        if (categoryId == null) return false;
        try (Cursor cursor = getReadableDatabase().query(
                "prompt_categories",
                new String[]{"id"},
                "id = ?",
                new String[]{categoryId},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    private void seedCategory(SQLiteDatabase database, String id, String name, int sortOrder) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        values.put("sort_order", sortOrder);
        values.put("created_at", System.currentTimeMillis());
        database.insertOrThrow("prompt_categories", null, values);
    }

    static final class Category {
        final String id;
        final String name;

        Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Prompt {
        final long id;
        final String categoryId;
        final String content;
        final long createdAt;

        Prompt(long id, String categoryId, String content, long createdAt) {
            this.id = id;
            this.categoryId = categoryId;
            this.content = content;
            this.createdAt = createdAt;
        }
    }
}
