package com.amin.pocketgba;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
}
