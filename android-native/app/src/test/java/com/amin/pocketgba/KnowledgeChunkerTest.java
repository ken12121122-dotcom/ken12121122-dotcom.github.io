package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class KnowledgeChunkerTest {
    @Test public void keepsLawArticlesTraceableWithoutSummarizing() {
        String input = "# 測試法規\n\n第 一 條 不應匹配空白條號。\n\n第六條 雇主應提供必要安全設備。\n\n第七條 工作場所應保持安全。";
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.split(input);
        assertTrue(chunks.size() >= 3);
        assertEquals("測試法規", chunks.get(0).section);
        assertEquals("第六條", chunks.get(chunks.size() - 2).section);
        assertTrue(chunks.get(chunks.size() - 1).content.contains("工作場所"));
    }

    @Test public void splitsOversizedContentWithoutLosingText() {
        StringBuilder raw = new StringBuilder("## 長內容\n");
        for (int i = 0; i < 500; i++) raw.append("測試資料段落").append(i).append('。');
        List<KnowledgeChunker.Chunk> chunks = KnowledgeChunker.split(raw.toString());
        assertTrue(chunks.size() > 1);
        for (KnowledgeChunker.Chunk chunk : chunks) {
            assertTrue(chunk.content.length() <= KnowledgeChunker.MAX_CHARS);
        }
    }
}
