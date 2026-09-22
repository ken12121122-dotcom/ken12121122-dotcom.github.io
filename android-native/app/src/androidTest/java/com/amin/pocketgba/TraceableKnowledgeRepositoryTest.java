package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public final class TraceableKnowledgeRepositoryTest {
    private static final String DB = "traceable-knowledge-instrumentation.db";
    private static final String NAMESPACE = "traceable-knowledge-instrumentation";
    private Context context;
    private TraceableKnowledgeRepository repository;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB);
        deleteRecursively(new File(context.getFilesDir(), NAMESPACE));
        repository = new TraceableKnowledgeRepository(context, DB, NAMESPACE);
    }

    @After public void tearDown() {
        if (repository != null) repository.close();
        context.deleteDatabase(DB);
        deleteRecursively(new File(context.getFilesDir(), NAMESPACE));
    }

    @Test public void importsFiveDocumentsPersistsAndSearchesWithTraceability() throws Exception {
        long now = System.currentTimeMillis();
        importFixture("osh-act", "職業安全衛生法", "雇主應採取必要的安全衛生設備及措施。", now);
        importFixture("osh-facility", "職業安全衛生設施規則", "工作場所通道應保持暢通。", now + 1);
        importFixture("labor-standards", "勞動基準法", "工作時間及延長工時應依規定辦理。", now + 2);
        importFixture("fire-act", "消防法", "場所應依規定設置消防安全設備。", now + 3);
        TraceableKnowledgeRepository.ImportResult internal = importFixture(
                "internal-safety", "公司安全管理辦法", "發現現場缺失應記錄來源並追蹤改善。", now + 4);

        assertEquals(5, repository.documentCount());
        assertEquals(5, repository.versionCount());

        TraceableKnowledgeRepository.ImportResult duplicate = importFixture(
                "internal-safety", "公司安全管理辦法", "發現現場缺失應記錄來源並追蹤改善。", now + 5);
        assertFalse(duplicate.created);
        assertEquals(internal.rawHash, duplicate.rawHash);
        assertEquals(internal.rawPath, duplicate.rawPath);
        assertEquals(5, repository.versionCount());

        JSONObject result = repository.search("消防安全設備", 3);
        assertEquals(TraceableKnowledgeContract.RESULT_FORMAT, result.getString("format"));
        assertTrue(result.getBoolean("offline"));
        JSONArray matches = result.getJSONArray("matched_chunks");
        assertTrue(matches.length() >= 1);
        assertEquals("消防法", matches.getJSONObject(0).getString("title"));
        assertTrue(matches.getJSONObject(0).has("version_id"));
        assertTrue(matches.getJSONObject(0).has("source_id"));
        assertTrue(result.getJSONArray("source_records").length() >= 1);

        repository.close();
        repository = new TraceableKnowledgeRepository(context, DB, NAMESPACE);
        assertEquals(5, repository.documentCount());
        assertEquals(5, repository.versionCount());
    }

    private TraceableKnowledgeRepository.ImportResult importFixture(
            String id, String title, String content, long now) throws Exception {
        JSONObject source = new JSONObject()
                .put("source_id", "fixture:" + id)
                .put("source_type", "test_fixture")
                .put("authority", "instrumentation_test")
                .put("source_url", "https://example.test/" + id);
        JSONObject document = new JSONObject()
                .put("document_id", "document:" + id)
                .put("source_id", "fixture:" + id)
                .put("title", title)
                .put("review_status", "generated");
        return repository.importText(source, document, "fixture-v1", "", "# " + title
                + "\n\n第一條 " + content, now);
    }

    private static void deleteRecursively(File value) {
        if (value == null || !value.exists()) return;
        File[] children = value.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        value.delete();
    }
}
