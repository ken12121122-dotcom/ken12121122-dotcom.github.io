package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

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

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("amin_prompts.db");
        store = new PromptStore(context);
    }

    @After
    public void tearDown() {
        store.close();
        context.deleteDatabase("amin_prompts.db");
    }

    @Test
    public void categoriesAreSeededAndSelectedTextIsPreserved() {
        assertEquals(4, store.listCategories().size());
        long id = store.savePrompt("work", "  第一行\n第二行  ");
        assertTrue(id > 0L);
        assertEquals(1, store.countPrompts());
        assertEquals("  第一行\n第二行  ", store.listPrompts("work").get(0).content);
    }

    @Test
    public void unknownCategoryFallsBackToInbox() {
        store.savePrompt("missing", "候選提示詞");
        assertEquals(1, store.listPrompts(PromptStore.DEFAULT_CATEGORY_ID).size());
    }
}
