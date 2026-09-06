package com.amin.pocketgba;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FloatingVoiceControllerTest {
    @Test
    public void detectsWakeWordWithoutSpace() {
        assertTrue(FloatingVoiceController.containsWakeWord("狐狸開啟語音"));
    }

    @Test
    public void detectsWakeWordSplitByAsrSpace() {
        assertTrue(FloatingVoiceController.containsWakeWord("狐 狸 開啟語音"));
    }

    @Test
    public void rejectsTextWithoutWakeWord() {
        assertFalse(FloatingVoiceController.containsWakeWord("開啟語音"));
    }

    @Test
    public void stripsWakeWordWithoutSpace() {
        assertEquals("開啟語音", FloatingVoiceController.stripWakeWord("狐狸 開啟語音"));
    }

    @Test
    public void stripsWakeWordSplitByAsrSpaceKeepingRemainderIntact() {
        assertEquals("開啟語音", FloatingVoiceController.stripWakeWord("狐 狸 開啟語音"));
    }

    @Test
    public void stripReturnsTrimmedTextWhenWakeWordMissing() {
        assertEquals("開啟語音", FloatingVoiceController.stripWakeWord(" 開啟語音 "));
    }

    @Test
    public void stripHandlesWakeWordOnlyUtterance() {
        assertEquals("", FloatingVoiceController.stripWakeWord("狐狸"));
    }

    @Test
    public void parseSemanticResultAcceptsUsableRoute() {
        SemanticRouteContract result = FloatingVoiceController.parseSemanticResult(
                "{\"intent\":\"capability_query\",\"confidence\":0.9,\"requires_execution\":false}");
        assertNotNull(result);
        assertTrue(result.isCapabilityQuery());
    }

    @Test
    public void parseSemanticResultFallsBackOnLowConfidence() {
        assertNull(FloatingVoiceController.parseSemanticResult(
                "{\"intent\":\"capability_query\",\"confidence\":0.2,\"requires_execution\":false}"));
    }

    @Test
    public void parseSemanticResultFallsBackOnExecutionRequest() {
        assertNull(FloatingVoiceController.parseSemanticResult(
                "{\"intent\":\"capability_query\",\"confidence\":0.99,\"requires_execution\":true}"));
    }

    @Test
    public void parseSemanticResultFallsBackOnMalformedOutput() {
        assertNull(FloatingVoiceController.parseSemanticResult("我不確定怎麼回答這個問題。"));
    }

    @Test
    public void parseSemanticResultFallsBackOnNullOutput() {
        assertNull(FloatingVoiceController.parseSemanticResult(null));
    }
}
