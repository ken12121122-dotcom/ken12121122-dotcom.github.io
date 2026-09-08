package com.amin.pocketgba;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LegalCorpusResolverTest {
    @Test public void detectsStartupEquityLawQuestions() {
        assertTrue(LegalCorpusResolver.isLegalQuestion("新創公司股權要怎麼分配？"));
        assertTrue(LegalCorpusResolver.isLegalQuestion("有限公司跟股份有限公司差在哪"));
        assertTrue(LegalCorpusResolver.isLegalQuestion("我想創業，要先做商業登記嗎"));
        assertTrue(LegalCorpusResolver.isLegalQuestion("員工認股權怎麼設計"));
        assertTrue(LegalCorpusResolver.isLegalQuestion("公司法規定董事會怎麼開"));
    }

    @Test public void rejectsUnrelatedQuestions() {
        assertFalse(LegalCorpusResolver.isLegalQuestion("今天天氣如何"));
        assertFalse(LegalCorpusResolver.isLegalQuestion("幫我打開財務記帳頁面"));
        assertFalse(LegalCorpusResolver.isLegalQuestion("我想知道專案進度到哪了"));
        assertFalse(LegalCorpusResolver.isLegalQuestion(""));
        assertFalse(LegalCorpusResolver.isLegalQuestion(null));
    }

    @Test public void systemPromptWithoutEvidenceRefusesInsteadOfGuessing() {
        String prompt = LegalCorpusResolver.systemPrompt(null);
        assertTrue(prompt.contains("查無足夠條文證據"));
        assertTrue(prompt.contains("沒有檢索到相關條文"));
    }
}
