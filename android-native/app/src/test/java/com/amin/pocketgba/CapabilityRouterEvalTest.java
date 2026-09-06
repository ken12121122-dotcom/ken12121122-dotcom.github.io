package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Phase 12 Step 5 — A/B eval harness (capability-question dimension only; see class javadoc below
 * for why the other three metrics from the plan aren't here).
 *
 * <p>35 phrases a real user might plausibly say, hand-labeled for whether a human would call them
 * a "what capabilities do you have" question, run against the actual deterministic
 * {@link CapabilityResolver#isCapabilityQuestion}. This is the *baseline* half of the comparison:
 * "old Router". The "Semantic Router" half needs a real LLM call (a configured API key), which
 * this sandboxed environment does not have — so it isn't fabricated here. Once Step 2's semantic
 * gate is exercised on a real device with a key configured, the same 35 phrases should be run
 * through it and the two accuracy numbers compared; MISS_INDICES below are exactly the cases a
 * semantic router with real language understanding ought to catch that keyword matching cannot.
 *
 * <p>The other three metrics from the original plan (Node-selection accuracy, wrong-context-load
 * rate, average context size) all depend on {@code Context}/{@code NodeMetadataStore}
 * ({@link FoxConversationContextBuilder#build} and friends), which cannot run in a plain JUnit
 * test in this environment (verified: every attempt to compile those classes standalone fails on
 * missing android.* stubs, same constraint noted in every commit this phase). Measuring them needs
 * an instrumentation test (like {@code WikiGraphActivityTest}) exercised through this repo's real
 * emulator-acceptance CI job or a real device — that is a deliberately separate follow-up, not
 * something to fake here.
 */
public final class CapabilityRouterEvalTest {
    /** phrase, expected (human judgment of "is this asking what capabilities exist") */
    private static final Object[][] CASES = {
        {"你目前有哪些能力？", true},
        {"你可以做什麼事情？", true},
        {"有沒有記帳能力", true},
        {"你有什麼功能", true},
        {"能力清單列出來給我看", true},
        {"幫我盤點一下你的能力", true},
        {"你到底會什麼", true},
        {"查詢能力：財務", true},
        {"尋找能力 記帳", true},
        {"capability list please", true},
        {"你有沒有翻譯的能力", true},
        {"有什麼功能", true},
        {"你能做什麼", true},
        {"系統目前有哪些能力可以用", true},
        {"你目前具備什麼能力範圍", true},
        {"跟我說說你現在支援哪些操作", true},
        {"有什麼是你辦得到的", true},
        {"介紹一下你的功能", true},
        {"你有哪些功能可以介紹一下嗎", true},
        {"你的能力範圍是什麼", true},
        {"今天天氣如何", false},
        {"把音量調高一點", false},
        {"現在幾點", false},
        {"你覺得今天心情怎麼樣", false},
        {"幫我打開財務記帳頁面", false},
        {"我想知道專案進度到哪了", false},
        {"狐狸你在嗎", false},
        {"幫我查一下GitHub有沒有新的PR", false},
        {"解釋一下Focus Chain Navigation怎麼用", false},
        {"現在天氣會下雨嗎", false},
        {"幫我建立一個新節點", false},
        {"財務目前的收支狀況", false},
        {"晚餐吃什麼比較好", false},
        {"把游標移到左邊", false},
        {"謝謝你今天的幫忙", false},
    };

    /**
     * Measured against the real {@link CapabilityResolver#isCapabilityQuestion} on 2026-09-06:
     * 27/35 correct, all 8 misses are false negatives on paraphrased capability questions
     * (indices 5, 6, 14, 15, 16, 17, 18, 19) — zero false positives on the 15 non-capability
     * phrases. This is the floor: a future change to the keyword list should only move this
     * number up, never down.
     */
    private static final int BASELINE_CORRECT = 27;

    @Test public void deterministicRouterMeetsMeasuredBaseline() {
        int correct = 0;
        StringBuilder misses = new StringBuilder();
        for (Object[] c : CASES) {
            String phrase = (String) c[0];
            boolean expected = (Boolean) c[1];
            boolean actual = CapabilityResolver.isCapabilityQuestion(phrase);
            if (expected == actual) {
                correct++;
            } else {
                misses.append("\n  expected=").append(expected).append(" actual=").append(actual)
                        .append("  \"").append(phrase).append('"');
            }
        }
        assertTrue("deterministic router regressed below measured baseline " + BASELINE_CORRECT
                + "/" + CASES.length + " (" + correct + " correct):" + misses,
                correct >= BASELINE_CORRECT);
    }

    @Test public void deterministicRouterHasNoFalsePositivesOnNonCapabilityPhrases() {
        for (Object[] c : CASES) {
            boolean expected = (Boolean) c[1];
            if (expected) continue;
            String phrase = (String) c[0];
            assertEquals("false positive on non-capability phrase: \"" + phrase + "\"",
                    false, CapabilityResolver.isCapabilityQuestion(phrase));
        }
    }
}
