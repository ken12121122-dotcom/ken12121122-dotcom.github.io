package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PromptStoreInstrumentedTest {
    private Context context;
    private PromptStore store;

    @Before public void setUp(){context=ApplicationProvider.getApplicationContext();context.deleteDatabase("amin_prompts.db");store=new PromptStore(context);}
    @After public void tearDown(){if(store!=null)store.close();context.deleteDatabase("amin_prompts.db");}

    @Test public void categoriesAreSeededAndSelectedTextIsPreserved(){assertEquals(4,store.listCategories().size());long id=store.savePrompt("work","  第一行\n第二行  ");assertTrue(id>0);assertEquals(1,store.countPrompts());PromptStore.Prompt p=store.listPrompts("work").get(0);assertEquals("  第一行\n第二行  ",p.content);assertEquals(PromptStore.STATUS_ACTIVE,p.status);assertFalse(p.favorite);}

    @Test public void lifecycleAndUsageAreReversibleUntilHardDelete(){long id=store.createPrompt("research","研究提示詞","內容","ai,research");store.setFavorite(id,true);store.setPinned(id,true);store.recordUsage(id);PromptStore.Prompt p=store.getPrompt(id);assertTrue(p.favorite);assertTrue(p.pinned);assertEquals(1,p.usageCount);assertNotNull(p.lastUsedAt);store.archive(id);assertEquals(0,store.countPrompts());store.unarchive(id);assertEquals(1,store.countPrompts());store.softDelete(id);assertEquals(0,store.countPrompts());assertEquals(1,store.listDeleted().size());store.restore(id);assertEquals(1,store.countPrompts());store.softDelete(id);assertTrue(store.hardDelete(id));assertEquals(null,store.getPrompt(id));}

    @Test public void graphRelationsUseStableTypedIds(){long a=store.createPrompt("work","A","A content","");long b=store.createPrompt("work","B","B content","");assertTrue(store.addRelation("prompt",String.valueOf(a),"prompt",String.valueOf(b),"depends_on"));assertTrue(store.addRelation("prompt",String.valueOf(a),"knowledge","release-process","uses_knowledge"));String json=store.graphJson();assertTrue(json.contains("prompt:"+a));assertTrue(json.contains("prompt:"+b));assertTrue(json.contains("knowledge:release-process"));assertEquals(2,store.listRelationsForPrompt(a).size());}

    @Test public void versionOneDatabaseMigratesWithoutLosingPrompt(){store.close();context.deleteDatabase("amin_prompts.db");SQLiteDatabase db=context.openOrCreateDatabase("amin_prompts.db",0,null);db.execSQL("CREATE TABLE prompt_categories (id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,sort_order INTEGER NOT NULL,created_at INTEGER NOT NULL)");db.execSQL("CREATE TABLE prompts (id INTEGER PRIMARY KEY AUTOINCREMENT,category_id TEXT NOT NULL,content TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");ContentValues c=new ContentValues();c.put("id","work");c.put("name","工作");c.put("sort_order",10);c.put("created_at",1);db.insert("prompt_categories",null,c);ContentValues p=new ContentValues();p.put("category_id","work");p.put("content","舊提示詞");p.put("created_at",1);p.put("updated_at",1);db.insert("prompts",null,p);db.setVersion(1);db.close();store=new PromptStore(context);PromptStore.Prompt migrated=store.listPrompts("work").get(0);assertEquals("舊提示詞",migrated.content);assertEquals(PromptStore.STATUS_ACTIVE,migrated.status);assertTrue(migrated.title.length()>0);}

    @Test public void unknownCategoryFallsBackToInbox(){store.savePrompt("missing","候選提示詞");assertEquals(1,store.listPrompts(PromptStore.DEFAULT_CATEGORY_ID).size());}
}
